package com.wms.model.dto.outbound;

import com.wms.model.entity.DocumentItemEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboundItemDto {
    // ED - 17 : 출고 문서 상세 조회
    private Integer documentItemId;
    private String productCode;
    private String productName;
    private Integer expectedQty;

    public static OutboundItemDto from(DocumentItemEntity entity) {
        return OutboundItemDto.builder()
                .documentItemId(entity.getDocumentItemId())
                .productCode(entity.getProductEntity().getProductCode())
                .productName(entity.getProductEntity().getProductName())
                .expectedQty(entity.getExpectedQty())
                .build();
    }

}
