package com.wms.model.dto.outbound;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder 
public class OutboundItemDto {
    private Integer documentItemId;
    private String productCode;
    private String productName;
    private Integer expectedQty;

    public static OutboundItemDto from(DocumentItemEntity documentItemEntity){
        return OutboundItemDto.builder()
            .documentItemId(documentItemEntity.getDocumentItemId())
            .productCode(documentItemEntity.getProductEntity().getProductCode())
            .productName(documentItemEntity.getProductEntity().getProductName())
            .expectedQty(documentItemEntity.getExpectedQty())
            .build();
    }

}
