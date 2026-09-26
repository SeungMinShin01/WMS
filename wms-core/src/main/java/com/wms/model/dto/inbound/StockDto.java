package com.wms.model.dto.inbound;

import java.time.LocalDate;

import com.wms.model.entity.StockEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class StockDto {
    // ED - 21 : 재고 하나 조회
    private Integer stockId;
    private String productCode;
    private String productName;
    private String lotCode;
    private LocalDate expiryDate;
    private String locationCode;
    private Integer qty;
    private Integer allocatedQty;
    private Integer availableQty;

    public static StockDto from(StockEntity entity) {
        // ED - 21 : 전체 재고 조회
        return StockDto.builder()
                .stockId(entity.getStockId())
                .productCode(entity.getLotEntity().getProductEntity().getProductCode())
                .productName(entity.getLotEntity().getProductEntity().getProductName())
                .lotCode(entity.getLotEntity().getLotCode())
                .expiryDate(entity.getLotEntity().getExpiryDate())
                .locationCode(entity.getLocationEntity().getLocationCode())
                .qty(entity.getQty())
                .allocatedQty(entity.getAllocatedQty())
                // 가용재고 = 실제재고 - 선점재고
                .availableQty(entity.getQty() - entity.getAllocatedQty())
                .build();
    }
}
