package com.wms.model.dto.outbound;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.ProductEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class OutboundItemCreateDto {
    // 출고 품목 1줄 — LOT 는 정하지 않음 (할당 때 FEFO 로 결정)
    private Integer productId;
    private Integer expectedQty; // 주문 수량

    public DocumentItemEntity toEntity(DocumentEntity document, ProductEntity product) {
        return DocumentItemEntity.builder()
                .documentEntity(document)
                .productEntity(product)
                .expectedQty(this.expectedQty)
                .build(); // lotEntity = null
    }
}