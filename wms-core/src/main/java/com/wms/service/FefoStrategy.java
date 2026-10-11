package com.wms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.springframework.stereotype.Component;

import com.wms.model.entity.StockEntity;

// [ED-출고 할당 추천(buildPlan) 알고리즘 리팩터링] ③④ 새로 만듦 : FEFO 추천 계산 (우선순위 큐 2단계)
// 전 : buildPlan 안에서 후보 전체를 비교 7단계로 한 번에 sort 한 뒤 앞에서부터 담음
//      → LOT 기준과 칸 기준이 한 비교 함수에 섞여 있고, 몇 개만 쓰는데도 전부 정렬
// 후 : 1단계 LOT 큐에서 1등 LOT 를 꺼내고 → 2단계 그 LOT 의 칸 큐에서 1등 칸부터 꺼내 담음
//      → "LOT 를 고르고, 그 LOT 안에서 칸을 고른다" 는 업무 순서가 코드 모양 그대로 보임
//      → need 가 채워지면 바로 멈춰서 나머지 LOT·칸은 꺼내지 않음
// 결과(어느 재고에서 몇 개, 순서)는 전과 똑같음 : 비교 기준 7개와 순서를 그대로 옮김
// @Component : 스프링이 이 클래스를 객체로 만들어 두고, AllocationStrategy 가 필요한 곳(@Autowired)에 넣어줌
// implements AllocationStrategy : 인터페이스의 plan 메서드를 이 클래스가 실제로 채움
@Component
public class FefoStrategy implements AllocationStrategy {

    // LOT 묶음 1개 (계산용, DB 저장 안 함)
    private static class LotGroup {
        Integer lotId;              // LOT 번호
        LocalDate expiryDate;       // 그 LOT 의 소비기한 (LOT 단위 값이라 묶음 안 재고들은 모두 같음)
        int total;                  // 그 LOT 에 속한 후보 재고들의 가용 합계
        LocalDateTime firstIn;      // 그 LOT 에서 가장 먼저 적재된 시각
        List<StockEntity> stocks;   // 그 LOT 에 속한 후보 재고 행들 (칸이 여러 개일 수 있음)
    }

    @Override
    public List<Pick> plan(int need, List<StockEntity> candidates, Map<Integer, Integer> available) {

        // 1. 후보 재고를 LOT 별로 묶기 : LOT번호 → 그 LOT 의 재고 행들
        Map<Integer, List<StockEntity>> stocksByLot = new HashMap<>();
        for (StockEntity s : candidates) {
            Integer lotId = s.getLotEntity().getLotId();
            if (!stocksByLot.containsKey(lotId)) {
                stocksByLot.put(lotId, new ArrayList<>());
            }
            stocksByLot.get(lotId).add(s);
        }

        // 2. 1단계 LOT 큐 만들기
        // PriorityQueue : 넣어 두면 poll() 할 때마다 "지금 1등"을 하나씩 꺼내 주는 상자
        //                 전부 정렬하지 않고 꺼낼 때마다 1등만 찾아 줌
        // (a, b) -> { ... } : 1등을 정하는 비교 규칙 (sort 와 같은 방식 : 음수면 a 가 앞, 양수면 b 가 앞)
        PriorityQueue<LotGroup> lotQueue = new PriorityQueue<>((a, b) -> {
            // 1 소비기한 빠른 순 (FEFO : 먼저 만료되는 것부터)
            int result = a.expiryDate.compareTo(b.expiryDate);
            if (result != 0) return result;

            // 2-1 LOT 가용 합계 많은 순 (b, a 자리 바꿈) : 한 LOT 를 통째로 먼저 소진 → LOT 섞임 줄임
            result = Integer.compare(b.total, a.total);
            if (result != 0) return result;

            // 2-2 LOT 첫 적재일 빠른 순 : 먼저 들어온 LOT 먼저 (FIFO)
            result = a.firstIn.compareTo(b.firstIn);
            if (result != 0) return result;

            // 2-3 lotId 작은 순 : 위가 다 같을 때 순서를 하나로 고정
            return a.lotId.compareTo(b.lotId);
        });

        // LOT 마다 묶음(LotGroup)을 만들어 LOT 큐에 넣기
        for (Integer lotId : stocksByLot.keySet()) {
            // stocksByLot.keySet() = stocksByLot의 키만 전부 모아서 for문 돌림
            List<StockEntity> stocks = stocksByLot.get(lotId);

            LotGroup group = new LotGroup();
            // group → [ lotId: null | expiryDate: null | total: 0 | firstIn: null | stocks: null ]
            group.lotId = lotId;
            // stocks.get(0)은 Map의 get이 아니라 List의 get이라 괄호안숫자는 몇번째 칸(인덱스)인지 
            group.expiryDate = stocks.get(0).getLotEntity().getExpiryDate();
            group.stocks = stocks;
            group.total = 0;
            group.firstIn = null;
            for (StockEntity s : stocks) {
                group.total += available.get(s.getStockId()); // LOT 가용 합계 누적
                // 처음이거나 지금 적힌 시각보다 더 이르면 바꿈 → 끝나면 가장 이른 적재 시각
                if (group.firstIn == null || s.getCreatedAt().isBefore(group.firstIn)) {
                    group.firstIn = s.getCreatedAt();
                }
            }
            lotQueue.add(group); // add : 큐에 넣기 (넣을 때 비교 규칙대로 자리를 잡음)
        }

        // 3. LOT 1등 → 그 LOT 의 칸 1등부터 need 만큼 꺼내기, LOT 를 다 써도 남으면 다음 LOT
        List<Pick> picks = new ArrayList<>();
        int remain = need; // 아직 채워야 할 수량

        while (remain > 0 && !lotQueue.isEmpty()) {
            // poll 전 : lotQueue = [B, A]
            // LotGroup lot = lotQueue.poll();
            // poll 후 : lotQueue = [A] B가 빠지고 lot에 B가 들어감 그래서 lot.stack 이나 lot.total 같은 값을 꺼내 쓸 수 있음
            LotGroup lot = lotQueue.poll(); // poll() : 1등을 꺼내고 큐에서 뺌

            // 2단계 칸 큐 : 이 LOT 의 칸(재고 행)들만 넣음
            PriorityQueue<StockEntity> stockQueue = new PriorityQueue<>((a, b) -> {
                // 3-1 칸 가용 많은 순 (b, a 자리 바꿈) : 적은 칸 수에서 꺼내 피킹 동선 줄임
                int result = Integer.compare(available.get(b.getStockId()), available.get(a.getStockId()));
                if (result != 0) return result;

                // 3-2 칸 적재일 빠른 순
                result = a.getCreatedAt().compareTo(b.getCreatedAt());
                if (result != 0) return result;

                // 3-3 stockId 작은 순 (마지막 고정 기준)
                return a.getStockId().compareTo(b.getStockId());
            });
            stockQueue.addAll(lot.stocks); // addAll : 목록을 통째로 큐에 넣기

            while (remain > 0 && !stockQueue.isEmpty()) {
                StockEntity s = stockQueue.poll();

                // Math.min : 재고가 넉넉하면 remain 만큼만, 모자라면 이 칸에 있는 만큼만
                int take = Math.min(remain, available.get(s.getStockId()));
                picks.add(new Pick(s, take));
                remain -= take;
            }
        }
        return picks;
    }
}