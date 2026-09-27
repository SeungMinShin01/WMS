package com.wms.model.dto.inbound;

import com.wms.model.entity.DocumentItemDetailEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class InspectionResultDto {
    private Integer detailId;
    private Integer documentItemId;
    private String productName;
    private String lotCode;
    private Integer expectedQty;
    private Integer qty;
    private String locationCode;

    public static InspectionResultDto from(DocumentItemDetailEntity entity) {
        return InspectionResultDto.builder()
                .detailId(entity.getDetailId())
                .documentItemId(entity.getDocumentItemEntity().getDocumentItemId())
                .productName(entity.getDocumentItemEntity().getProductEntity().getProductName())
                .lotCode(entity.getLotEntity().getLotCode())
                .expectedQty(entity.getDocumentItemEntity().getExpectedQty())
                .qty(entity.getQty())
                // 적재전이면 location null
                .locationCode(entity.getLocationEntity() == null ? null : entity.getLocationEntity().getLocationCode())
                .build();
    }
}
