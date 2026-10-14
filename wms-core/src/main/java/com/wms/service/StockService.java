package com.wms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.inbound.LocationRecommendDto;
import com.wms.model.dto.stock.StockDto;
import com.wms.model.dto.stock.StockHistoryDto;
import com.wms.model.entity.DetailStatus;
import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.LocationEntity;
import com.wms.model.entity.LotEntity;
import com.wms.model.entity.StockDisposalEntity;
import com.wms.model.entity.StockEntity;
import com.wms.model.repository.DocumentItemDetailRepository;
import com.wms.model.repository.LocationRepository;
import com.wms.model.repository.LotRepository;
import com.wms.model.repository.StockDisposalRepository;
import com.wms.model.repository.StockRepository;
import com.wms.model.repository.TenantRepository;
import com.wms.model.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional
public class StockService {
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private DocumentItemDetailRepository detailRepository;
    @Autowired 
    private TenantRepository tenantRepository;
    @Autowired 
    private LotRepository lotRepository;
    @Autowired 
    private StockDisposalRepository stockDisposalRepository;
    @Autowired 
    private UserRepository userRepository;

    // ED-21 재고 조회
    public List<StockDto> stockFindAll() {
        List<StockEntity> stockEntities = stockRepository.findAll();
        // 폐기·출고로 실물 0 이 된 줄은 재고현황에서 숨김 (줄 자체는 이력 때문에 남아 있음)
        stockEntities.removeIf(s -> s.getQty() == 0);
        // FEFO정렬: 소비기한 빠른 순 -> 소비기한 없는 LOT는 맨 뒤 -> 같으면 stockId순으로 
        stockEntities.sort((a,b) -> {
            LocalDate expiryA = a.getLotEntity().getExpiryDate();
            LocalDate expiryB = b.getLotEntity().getExpiryDate();

            // 1. 소비기한이 없는 쪽은 맨 뒤
            if(expiryA == null && expiryB != null) return 1;    // a 뒤로
            if(expiryA != null && expiryB == null) return -1;    // a 앞으로

            // 2. 둘 다 있고 날짜가 다르면 빠른 날짜가 앞으로
            if(expiryA != null && expiryB != null && !expiryA.equals(expiryB))
                return expiryA.compareTo(expiryB);
            
            // 3. 소비기한이 같으면 먼저 생긴 재고 (stockId 작은 것)이 앞으로
            return a.getStockId().compareTo(b.getStockId());
        });
        List<StockDto> stockDtos = new ArrayList<>();
        stockEntities.forEach(stockEntity -> stockDtos.add(StockDto.from(stockEntity)));
        return stockDtos;
    }

    // 재고 증가(ED-16 적재) - ED-70읽어서 자바에서 더하지 않고 DB에 qty = qty+수량
    // 같은 LOT + 같은 칸 줄이 있으면 UPDATE, 없으면 새 줄 INSERT
    public StockEntity increase(Integer lotId, Integer locationId, Integer tenantId, int qty){
        int changed = stockRepository.addQty(lotId, locationId, qty);
        if(changed > 0)
            return stockRepository.findByLotEntityLotIdAndLocationEntityLocationId(lotId, locationId)
                    .orElseThrow(()->new IllegalStateException("재고 줄을 찾을 수 없습니다."));

        // 칸에 그 LOT 줄이 없으면 새로 만든다
        // 같은 칸 LOT 줄이 동시에 2개 생기는 경우는 적재 대기열(carryExecutor)이 줄 세워 막음
        StockEntity newStock = StockEntity.builder()
                    .tenantEntity(tenantRepository.getReferenceById(tenantId))
                    .lotEntity(lotRepository.getReferenceById(lotId))
                    .locationEntity(locationRepository.getReferenceById(locationId))
                    .qty(qty)
                    .build();
        return stockRepository.save(newStock);
    } 

    // 칸 하나의 현재 적재 수량 합계 (적재 capacity 검사용, ED-51) - ED-70 DB SUM 한번
    public int locationTotal(Integer locationId){
        return stockRepository.sumQtyByLocationId(locationId).intValue();
    }

    // 칸에 다른 화주 재고가 있는지 (칸 단위 화주 검사) - 넣으려는 LOT의 품목화주 기준 - ED-70 DB SUM 한번
    public boolean hasOtherTenant(Integer locatonId, Integer tenantId){
        return stockRepository.countOtherTenant(locatonId, tenantId) > 0;
    }

    // 칸 하나의 현재 상태 (적재하려는 LOT 기준), 이유: 결과값4개를 한번에 반환해야하는데 JAVA 메소드는 하나의 값만 돌려줘서
    private record LocationCheck(int total, boolean hasSameLot, boolean hasSameProductOtherLot, boolean hasOtherTenant) {
    }

    // 칸 하나 조사
    // stocks = 이 칸의 수량 있는 재고만
    private LocationCheck check(LocationEntity loc, LotEntity lot, List<StockEntity> stocks) {
        int total = 0;  // 칸 합계
        boolean hasSameLot = false; // 같은 LOT 유무
        boolean hasSameProductOtherLot = false; // 같은 품목인데 LOT만 다른것이 있는지
        boolean hasOtherTenant = false; // 다른 화주의 재고 유무
        for (StockEntity s : stocks) {
            total += s.getQty();
            if(!s.getTenantEntity().getTenantId().equals(lot.getProductEntity().getTenantEntity().getTenantId()))
                hasOtherTenant = true;  // 다른 화주 재고가 있음(ED-60)
            Integer productId = s.getLotEntity().getProductEntity().getProductId();
            if (s.getLotEntity().getLotId().equals(lot.getLotId()))
                hasSameLot = true;
            else if (productId.equals(lot.getProductEntity().getProductId()))
                hasSameProductOtherLot = true;
        }
        return new LocationCheck(total, hasSameLot, hasSameProductOtherLot, hasOtherTenant);
    }

    // 여유 수량 - capacity가 null이면 제한 없음
    private int freeQty(LocationEntity loc, int total) {
        return loc.getCapacity() == null ? Integer.MAX_VALUE : loc.getCapacity() - total;
    }

    // 정책 위반 사유 (없으면 null) - 적치 추천에서 제외할 칸들
    private String policyViolation(LocationCheck c, boolean mixLot) {
        if (mixLot)
            return null; // 혼용적재 ON: 같은 품목 다른 LOT도 허용 -> 제외 없음
        // 혼용적재 OFF: 이 칸에 넣으면 LOT가 섞이므로 추천에서 뺌(다른 품목 칸은 유지)
        if (c.hasSameProductOtherLot())
            return "같은 품목의 다른 LOT가 있는 칸";
        return null;
    }

    // 적치 추천 - 제외: 미사용·꽉 찬 칸·(OFF) 같은 품목 다른 LOT 칸 / 순위: 같은 LOT → 같은 품목 다른 LOT → 다른
    // 품목 잔량 → 빈 칸 / 같은 순위: Best Fit
    public List<LocationRecommendDto> recommend(LotEntity lot, int qty, boolean mixLot) {
        // ED-71 사용 중인 칸만 + 수량 있는 재고만 쿼리 1번씩
        // 전에는 칸-재고 findAll + 칸마다 재고 전체를 훑고 재고 줄마다 lot-품목 select(n+1)
        List<LocationEntity> locations = locationRepository.findByIsActiveTrue();

        // 칸Id -> 그 칸의 재고들
        Map<Integer, List<StockEntity>> stocksByLocation = new HashMap<>();
        for(StockEntity s : stockRepository.findAllForRecommend()){
            Integer locationId = s.getLocationEntity().getLocationId();
            if(!stocksByLocation.containsKey(locationId))
                stocksByLocation.put(locationId, new ArrayList<>());
            stocksByLocation.get(locationId).add(s);
        }

        // 추천칸 빈 리스트
        List<LocationRecommendDto> candidates = new ArrayList<>();

        for(LocationEntity loc : locations){
            // 재고가 없는 칸은 빈 리스트 -> 빈 칸으로 판단
            LocationCheck c = check(loc, lot, stocksByLocation.getOrDefault(loc.getLocationId(), new ArrayList<>()));
            int free = freeQty(loc, c.total());
            if (free <= 0)
                continue; // 꽉 찬 칸 -> 탈락
            if(c.hasOtherTenant())
                continue;   // 다른 화주 재고가 있는 칸 --> 탈락 (ED-60)
            if (policyViolation(c, mixLot) != null)
                continue; // 정책 위반 칸 -> 탈락

            int priority;
            String reason;
            if (c.hasSameLot()) {
                priority = 1;
                reason = "같은 LOT 적치 중";
            } else if (c.hasSameProductOtherLot()) {
                priority = 2;
                reason = "같은 품목(다른 LOT) 칸";
            } // ON일 때만 여기까지 옴
            else if (c.total() > 0) {
                priority = 3;
                reason = "다른 품목 잔량 칸";
            } else {
                priority = 4;
                reason = "빈 칸";
            }

            // 추천칸 담기
            candidates.add(LocationRecommendDto.builder()
                    .locationId(loc.getLocationId())
                    .locationCode(loc.getLocationCode())
                    .reason(reason)
                    .currentQty(c.total())
                    .freeQty(free == Integer.MAX_VALUE ? null : free) // null = 제한 없음
                    .fits(free >= qty)
                    .priority(priority)
                    .build());
        }


        // 예) 20박스 적재 / A 여유 22, B 40, C 100 (전부 들어감) / D 15, E 5 (부족)
        // → 1번에서 [A, B, C] / [D, E] 로 나뉘고
        // → 3번에서 들어가는 칸은 여유 작은 순 A(22) → B(40) → C(100), 부족한 칸은 여유 큰 순 D(15) → E(5)
        // → 최종 A → B → C → D → E
                    
        // 추천칸 정렬
        candidates.sort((a,b)->{
            // 1. 전량 들어가는 칸(fits = true)이 앞 — 적재는 나눠 넣을 수 없어서 부족한 칸은 순위가 높아도 뒤로
            if(a.getFits() && !b.getFits()) return -1;
            if(!a.getFits() && b.getFits()) return 1;

            // 2. 순위 숫자가 작은 칸이 앞(1. 같은 LOT -> 2. 같은 품목 다른 LOT -> 3. 다른 품목 잔량 -> 빈칸)
            if(!a.getPriority().equals(b.getPriority()))
                return a.getPriority().compareTo(b.getPriority());

            // 3. 여유 비교 (제한없음 null은 가장 큰 여유칸으로 취급하기 )
            int freeA = a.getFreeQty() == null ? Integer.MAX_VALUE : a.getFreeQty();
            int freeB = b.getFreeQty() == null ? Integer.MAX_VALUE : b.getFreeQty();
            if(freeA != freeB){
                if(a.getFits()) return Integer.compare(freeA, freeB);   // 전부 들어가는 칸끼리: 여유 작은 칸이 앞 (딱 맞는칸, Best Fit)
                else            return Integer.compare(freeB, freeA);   // 부족한 칸끼리: 여유 큰 칸이 앞으로 (많이 들어가는 칸)          
            }
            // 다 같으면 칸 코드 순으로 
            return a.getLocationCode().compareTo(b.getLocationCode());
        });
        return candidates.size() > 5 ? candidates.subList(0, 5) : candidates;   // 후보 5개만 자르기
    }

    // 입출고 이력 - 재고를 바꾼 detail만, 최신순 + 변경 후 수량 계산
    public List<StockHistoryDto> historyFindAll() {
        // 1. 이력 줄 만들기 (검수만된 INSPCETED 줄은 재고 변화가 없으니 제외)
        List<StockHistoryDto> historyDtos = new ArrayList<>();
        for (DocumentItemDetailEntity detail : detailRepository.findAll()) {
            if(detail.getStatus() == DetailStatus.INSPECTED || detail.getStockEntity() == null)
                continue;
            historyDtos.addAll(StockHistoryDto.from(detail));
        }

        // 1-2. 폐기 기록도 이력 줄로 (문서가 없어서 stock_disposal 에서 읽음)
        for (StockDisposalEntity d : stockDisposalRepository.findAllForHistory())
            historyDtos.add(StockHistoryDto.fromDisposal(d));

        historyDtos.sort((a,b)->{
            LocalDateTime timeA = a.getOccurredAt();
            LocalDateTime timeB = b.getOccurredAt();

            // 1. 발생일시가 없는 쪽을 뒤로
            if(timeA == null && timeB != null) return 1;
            if(timeA != null && timeB == null) return -1;

            // 2. 둘 다 있고 다르면 늦은 시각이 앞으로 (b와a순서를 바꿔서 내림차순으로 정리)
            if(timeA != null && timeB != null && !timeA.equals(timeB))
                return timeB.compareTo(timeA);

            // 3. 같으면 detailId가 큰 것 (나중에 생긴 기록)이 앞으로
            // 폐기 줄은 앞으로
            if(a.getDetailId() == null || b.getDetailId() == null){
                if(a.getDetailId() == null && b.getDetailId() == null) return 0;
                return a.getDetailId() == null ? -1 : 1;
            }
            return b.getDetailId().compareTo(a.getDetailId());
        });

        // 3. 변경 후 수량: 재고 줄마다 현재 수량에서 시작해 최신 -> 과거로 증감을 빼며 되돌리기
        Map<Integer, int[]> running = new HashMap<>(); // stockId -> {실물, 선점}
        for (StockEntity s : stockRepository.findAll())
            running.put(s.getStockId(), new int[] { s.getQty(), s.getAllocatedQty() });

        for (StockHistoryDto h : historyDtos) {
            int[] now = running.get(h.getStockId());
            h.setAfterQty(now[0]); // 변동 직후 값
            h.setAfterAllocated(now[1]);
            now[0] -= h.getQtyChange(); // 한 단계 과거로
            now[1] -= h.getAllocatedChange();
        }
        return historyDtos;
    }

    // 재고 폐기 - 관리자 전용
    // 소비기한 당일까지 정상, 다음 날부터 폐기 대상 / 칸 단위 LOT 재고 전량
    // 재고 줄은 지우지 않고 실물 0 + 폐기 기록 1줄 (예전 입고·출고 기록이 이 재고 줄을 가리킴)
    public Integer dispose(Integer stockId, Integer userId){
        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(()-> new EntityNotFoundException("재고가 없습니다."));

        // 1. 소비기한 검사 : 오늘이 소비기한보다 뒤여야 폐기 가능
        LocalDate expiry = stock.getLotEntity().getExpiryDate();
        if(expiry == null || !expiry.isBefore(LocalDate.now()))
            throw new IllegalStateException("소비기한이 지난 재고만 폐기할 수 있습니다.");

        // 2. 출고 할당된 수량이 있으면 불가 (출고예정 물건이 사라지는거 방지)
        if(stock.getAllocatedQty() > 0)
            throw new IllegalStateException("출고 할당된 수량이 있어 폐기할 수 없습니다. 출고 취소 후 폐기해주세요.");

        int qty = stock.getQty();
        if(qty <= 0)
            throw new IllegalStateException("이미 폐기되었거나 수량이 없는 재고입니다.");

        // 3. 조건부 update: 
        int changed = stockRepository.dispose(stockId, qty);
        if(changed == 0)
            throw new IllegalStateException("재고가 그 사이 변경되었습니다. 새로고침 후 다시 시도해주세요.");

        // 4. 폐기기록 
        StockDisposalEntity saved = stockDisposalRepository.save(StockDisposalEntity.builder()
                    .stockEntity(stockRepository.getReferenceById(stockId))
                    .qty(qty)
                    .reason("소비기한 경과")
                    .userEntity(userRepository.getReferenceById(userId))
                    .disposedAt(LocalDateTime.now())
                    .build());
        return saved.getDisposalId();
    }
}
