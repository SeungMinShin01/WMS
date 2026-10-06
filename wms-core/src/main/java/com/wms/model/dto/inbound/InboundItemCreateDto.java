package com.wms.model.dto.inbound;

import java.time.LocalDate;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.LotEntity;
import com.wms.model.entity.ProductEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class InboundItemCreateDto {
    // 입고 문서 품목 1줄 등록 요청 — LOT 번호·소비기한은 공급사가 정해서 온 값
    private Integer productId;
    private String lotCode;
    private LocalDate expiryDate;
    private Integer expectedQty;

    // DTO -> ENTITY : 문서·품목·LOT는 서비스가 찾아오거나 등록한 엔티티
    public DocumentItemEntity toEntity(DocumentEntity document, ProductEntity product, LotEntity lot) {
        return DocumentItemEntity.builder()
                .documentEntity(document)
                .productEntity(product)
                .lotEntity(lot)
                .expectedQty(this.expectedQty)
                .build();
    }
}