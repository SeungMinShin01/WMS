package com.wms.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.inbound.LocationRecommendDto;
import com.wms.model.dto.stock.StockDto;
import com.wms.model.entity.LocationEntity;
import com.wms.model.entity.LotEntity;
import com.wms.model.entity.StockEntity;
import com.wms.model.repository.LocationRepository;
import com.wms.model.repository.StockRepository;

@Service 
@Transactional 
public class StockService {
    @Autowired private StockRepository stockRepository;
    @Autowired private LocationRepository locationRepository;
    // ED-21 재고 조회
     public List<StockDto> stockFindAll() {
        List<StockEntity> stockEntities = stockRepository.findAll();
        // FEFO정렬: 유통기한 오름차순 -> 유통기한없는 lot는 맨 뒤로 -> 같으면 stockId순으로 
        stockEntities.sort(
            Comparator.comparing((StockEntity s)->s.getLotEntity().getExpiryDate(),
            Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StockEntity::getStockId)
        );
        List<StockDto> stockDtos = new ArrayList<>();
        stockEntities.forEach(stockEntity -> stockDtos.add(StockDto.from(stockEntity)));
        return stockDtos;
    }

    // 재고 증가 (ED-16 적재에서 호출) — 같은 LOT + 같은 칸 재고가 있으면 더하고, 없으면 새로 만든다
    public StockEntity increase(LotEntity lotEntity, LocationEntity locationEntity, int qty) {
        List<StockEntity> stockEntities = stockRepository.findAll();
        for (StockEntity s : stockEntities) {
            if (s.getLotEntity().getLotId().equals(lotEntity.getLotId())
                    && s.getLocationEntity().getLocationId().equals(locationEntity.getLocationId())) {
                s.setQty(s.getQty() + qty);   // 있으면 수량 +
                return s;
            }
        }
        // 없으면 새 재고 생성
        StockEntity newStock = StockEntity.builder()
                .lotEntity(lotEntity)
                .locationEntity(locationEntity)
                .qty(qty)
                .build();
        return stockRepository.save(newStock);
    }

    // 칸 하나의 현재 상태 (적재하려는 LOT 기준)
    private record LocationCheck(int total, boolean hasSameLot, boolean hasSameProductOtherLot, int otherProductCount) {}

    // 칸 하나 조사
    private LocationCheck check(LocationEntity loc, LotEntity lot, List<StockEntity> stocks){
        int total = 0;
        boolean hasSameLot = false;
        boolean hasSameProductOtherLot = false;
        Set<Integer> otherProducts = new HashSet<>();
        for(StockEntity s : stocks){
            if(!s.getLocationEntity().getLocationId().equals(loc.getLocationId()) || s.getQty() == 0) continue;
            total += s.getQty();
            Integer productId = s.getLotEntity().getProductEntity().getProductId();
            if(s.getLotEntity().getLotId().equals(lot.getLotId()))  hasSameLot = true;
            else if (productId.equals(lot.getProductEntity().getProductId())) hasSameProductOtherLot = true;
            else                                                              otherProducts.add(productId);
        }
        return new LocationCheck(total, hasSameLot, hasSameProductOtherLot, otherProducts.size());
    }

    // 여유 수량 - capacity가 null이면 제한 없음
    private int freeQty(LocationEntity loc, int total){
        return loc.getCapacity() == null ? Integer.MAX_VALUE : loc.getCapacity() - total;
    }

    // 정책 위반 사유 (없으면 null) - 적치 추천에서 제외할 칸들
    private String policyViolation(LocationCheck c, boolean mixLot){
        if(mixLot) return null;                     // 혼용적재 ON: 같은 품목 다른 LOT도 허용 -> 제외 없음
        // 혼용적재 OFF: 이 칸에 넣으면 LOT가 섞이므로 추천에서 뺌(다른 품목 칸은 유지)
        if(c.hasSameProductOtherLot()) return "같은 품목의 다른 LOT가 있는 칸";
        return null;
    }

        // 적치 추천 - 제외: 미사용·꽉 찬 칸·(OFF) 같은 품목 다른 LOT 칸 / 순위: 같은 LOT → 같은 품목 다른 LOT → 다른 품목 잔량 → 빈 칸 / 같은 순위: Best Fit
    public List<LocationRecommendDto> recommend(LotEntity lot, int qty, boolean mixLot){
        List<StockEntity> stocks = stockRepository.findAll();
        List<LocationEntity> locations = locationRepository.findAll();
        List<LocationRecommendDto> candidates = new ArrayList<>();

        for(LocationEntity loc : locations){
            if(!loc.getIsActive()) continue;    // 미사용 칸
            LocationCheck c = check(loc, lot, stocks);
            int free = freeQty(loc, c.total());
            if(free <= 0) continue;             // 꽉 찬 칸
            if(policyViolation(c, mixLot) != null) continue;    // 정책 위반 칸

            int priority;
            String reason;
            if(c.hasSameLot())                  {priority = 1; reason = "같은 LOT 적치 중";}
            else if(c.hasSameProductOtherLot()) {priority = 2; reason = "같은 품목(다른 LOT) 칸";}   // ON일 때만 여기까지 옴
            else if(c.total() > 0)              {priority = 3; reason = "다른 품목 잔량 칸";}
            else                                {priority = 4; reason = "빈 칸";}

            candidates.add(LocationRecommendDto.builder()
                        .locationId(loc.getLocationId())
                        .locationCode(loc.getLocationCode())
                        .reason(reason)
                        .currentQty(c.total())
                        .freeQty(free == Integer.MAX_VALUE ? null : free)   // null = 제한 없음
                        .fits(free >= qty)
                        .priority(priority)
                        .build());
        }

        candidates.sort(Comparator
            .comparing(LocationRecommendDto::getPriority)                               // 1->2->3
            .thenComparing(LocationRecommendDto::getFits, Comparator.reverseOrder())    // 전량 들어가는 칸 먼저
            .thenComparingInt(d -> {
                int f = d.getFreeQty() == null ? Integer.MAX_VALUE : d.getFreeQty();
                return d.getFits() ? f : -f;    // 전량 칸: 여유 작은 순(Best Fit) / 부족 칸: 여유 큰 순
            })
            .thenComparing(LocationRecommendDto::getLocationCode)   // 같으면 칸 코드 순
        );
        return candidates.size() > 5 ? candidates.subList(0, 5) : candidates; 
    }
}
