package com.wms.model.dto.inbound;

import java.time.LocalDate;

import com.wms.model.entity.DocumentItemEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundItemDto {
    // ED - 13 : 입고 문서 상세 조회
    private Integer documentItemId;
    private String productCode;
    private String productName;
    private String lotCode;
    private LocalDate expiryDate;
    private Integer expectedQty;

    public static InboundItemDto from(DocumentItemEntity entity) {
        return InboundItemDto.builder()
                .documentItemId(entity.getDocumentItemId())
                .productCode(entity.getProductEntity().getProductCode())
                .productName(entity.getProductEntity().getProductName())
                .lotCode(entity.getLotEntity().getLotCode())
                .expiryDate(entity.getLotEntity().getExpiryDate())
                .expectedQty(entity.getExpectedQty())
                .build();
    }
}
