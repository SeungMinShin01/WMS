package com.wms.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.inbound.StockDto;
import com.wms.model.entity.LocationEntity;
import com.wms.model.entity.LotEntity;
import com.wms.model.entity.StockEntity;
import com.wms.model.repository.StockRepository;

@Service 
@Transactional 
public class StockService {
    @Autowired private StockRepository stockRepository;
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
}
