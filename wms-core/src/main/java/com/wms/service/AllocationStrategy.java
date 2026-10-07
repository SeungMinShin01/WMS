package com.wms.service;

import java.util.List;
import java.util.Map;

import com.wms.model.entity.StockEntity;

// [ED-출고 할당 추천(buildPlan) 알고리즘 리팩터링] ④ 새로 만듦 : 추천 계산 방식의 틀(인터페이스)
// "후보 재고 중 어디서 몇 개 꺼낼지" 계산만 하는 역할 (DB 조회·저장·DTO 변환은 안 함)
// AllocationPlanService 는 이 인터페이스만 알고, 실제 계산은 구현체(FefoStrategy) 가 함
//   → 나중에 다른 방식이 필요하면 구현체만 새로 만들면 됨
//   → DB 없이 재고 엔티티 몇 개만 만들어서 계산 결과를 테스트할 수 있음
// interface : 메서드 이름·매개변수·리턴 타입만 정해 두고, 내용은 구현 클래스가 채우는 틀
public interface AllocationStrategy {

    // need       : 이 품목 줄에서 채워야 할 수량
    // candidates : 규칙을 통과하고 가용이 0 보다 큰 후보 재고들 (같은 상품)
    // available  : 재고번호 → 이번 계산 기준 가용수량 (실물 − 선점 − 같은 문서 앞 품목 줄이 이미 쓴 수량)
    // 리턴       : 꺼낼 순서대로 담긴 Pick 목록 (수량 합계 = need)
    List<Pick> plan(int need, List<StockEntity> candidates, Map<Integer, Integer> available);

    // 계산 결과 1줄 = 이 재고(stock)에서 qty 개 꺼낸다
    // 인터페이스 안에 만든 클래스라 밖에서는 AllocationStrategy.Pick 으로 부름
    class Pick {
        private StockEntity stock; // 꺼낼 재고 행
        private int qty;           // 꺼낼 수량

        public Pick(StockEntity stock, int qty) {
            this.stock = stock;
            this.qty = qty;
        }

        public StockEntity getStock() {
            return stock;
        }

        public int getQty() {
            return qty;
        }
    }
}