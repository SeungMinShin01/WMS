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
    private Integer allocatedQty;   // 지금까지 할당된 수량 (서비스에서 채움)
    private Integer availableQty;   // 출고 가능한 재고 수량 (칸·소비기한 조건 통과한 가용 합계, 서비스에서 채움)

    public static OutboundItemDto from(DocumentItemEntity Entity) {
        return OutboundItemDto.builder()
                .documentItemId(Entity.getDocumentItemId())
                .productCode(Entity.getProductEntity().getProductCode())
                .productName(Entity.getProductEntity().getProductName())
                .expectedQty(Entity.getExpectedQty())
                .build();
    }
}