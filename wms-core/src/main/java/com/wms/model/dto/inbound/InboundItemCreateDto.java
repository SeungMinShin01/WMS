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
    // 입고 문서 품목 1줄 등록 요청 — LOT 번호는 서버가 제조일자·공급사코드로 자동 생성
    private Integer productId;
    private LocalDate manufactureDate;   // 제조일자: LOT 번호 만들 때만 사용 (DB에 저장 안 함)
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