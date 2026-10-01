package com.wms.model.dto.outbound;

import java.time.LocalDate;

import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.StockEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AllocationPreviewDto {
    private Integer documentItemId;   // 어느 품목 줄의 추천인지
    private Integer stockId;          // 추천 재고 행
    private String locationCode;
    private String productCode;
    private String productName;
    private String lotCode;
    private LocalDate expiryDate;
    private Integer qty;              // 꺼낼 예정 수량

    public static AllocationPreviewDto from(DocumentItemEntity item, StockEntity stock, int qty) {
        return AllocationPreviewDto.builder()
                .documentItemId(item.getDocumentItemId())
                .stockId(stock.getStockId())
                .locationCode(stock.getLocationEntity().getLocationCode())
                .productCode(item.getProductEntity().getProductCode())
                .productName(item.getProductEntity().getProductName())
                .lotCode(stock.getLotEntity().getLotCode())
                .expiryDate(stock.getLotEntity().getExpiryDate())
                .qty(qty)
                .build();
    }
}