package com.wms.model.dto.outbound;

import com.wms.model.entity.DocumentItemDetailEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PickingDto {
    // ED - 19 : 피킹 리스트 하나 조회
    private Integer detailId;
    private String locationCode;
    private String productCode;
    private String productName;
    private String lotCode;
    private Integer qty;

    public static PickingDto from(DocumentItemDetailEntity entity) {
        return PickingDto.builder()
                .detailId(entity.getDetailId())
                .locationCode(entity.getLocationEntity().getLocationCode())
                .productCode(entity.getDocumentItemEntity().getProductEntity().getProductCode())
                .productName(entity.getDocumentItemEntity().getProductEntity().getProductName())
                .lotCode(entity.getLotEntity().getLotCode())
                .qty(entity.getQty())
                .build();
    }
}
