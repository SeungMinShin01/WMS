package com.wms.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    // 적치 추천 - 같은 LOT 칸 -> 같은 품목 칸 -> 빈 칸 순, 다른 품목이 있는 칸은 제외
    public  List<LocationRecommendDto> recommend(LotEntity lotEntity){
        List<StockEntity> stocks = stockRepository.findAll();
        List<LocationEntity> locations = locationRepository.findAll();
        locations.sort((a,b)-> a.getLocationCode().compareTo(b.getLocationCode()));

        List<LocationRecommendDto> sameLot = new ArrayList<>();
        List<LocationRecommendDto> sameProduct = new ArrayList<>();
        List<LocationRecommendDto> empty = new ArrayList<>();

        for(LocationEntity loc : locations){
            if(!loc.getIsActive()) continue;    // 미사용칸 제외

            int total = 0;
            boolean hasSameLot = false, hasSameProduct = false, hasOther = false;
            for(StockEntity s : stocks){
                if(!s.getLocationEntity().getLocationId().equals(loc.getLocationId()) || s.getQty() == 0) continue;
                total += s.getQty();
                if(s.getLotEntity().getLotId().equals(lotEntity.getLotId())) hasSameLot = true;
                else if(s.getLotEntity().getProductEntity().getProductId().equals(lotEntity.getProductEntity().getProductId())) hasSameProduct = true;
                else hasOther = true;
            }

            if(hasOther) continue;  // 다른 품목이 있는 칸은 추천 안함(혼적방지)
            if(hasSameLot)              sameLot.add(new LocationRecommendDto(loc.getLocationId(), loc.getLocationCode(), "같은 LOT 적치 중", total));
            else if(hasSameProduct)     sameProduct.add(new LocationRecommendDto(loc.getLocationId(),loc.getLocationCode(), "같은 품목 적치 중", total));
            else                        empty.add(new LocationRecommendDto(loc.getLocationId(),loc.getLocationCode(), "빈 칸", 0)); 
        }
        
        List<LocationRecommendDto> result = new ArrayList<>();
        result.addAll(sameLot);
        result.addAll(sameProduct);
        result.addAll(empty);
        return result.size() > 5 ? result.subList(0, 5) : result;   // 상위 5개
    }
}
