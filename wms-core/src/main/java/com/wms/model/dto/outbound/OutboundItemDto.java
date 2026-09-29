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
    private Integer documentItemId;
    private String productCode;
    private String productName;
    private Integer expectedQty;

    public static OutboundItemDto from(DocumentItemEntity Entity) {
        return OutboundItemDto.builder()
                .documentItemId(Entity.getDocumentItemId())
                .productCode(Entity.getProductEntity().getProductCode())
                .productName(Entity.getProductEntity().getProductName())
                .expectedQty(Entity.getExpectedQty())
                .build();
    }
}
