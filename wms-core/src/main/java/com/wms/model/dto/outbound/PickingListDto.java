package com.wms.model.dto.outbound;

import com.wms.model.entity.DocumentItemDetailEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder 
public class PickingListDto {
    private Integer detailId;
    private String locationCode;
    private String productCode;
    private String productName;
    private String lotCode;
    private Integer qty;

    public static PickingListDto from( DocumentItemDetailEntity Entity ){
        return PickingListDto.builder()
            .detailId(Entity.getDetailId())
            .locationCode(Entity.getLocationEntity().getLocationCode())
            .productCode(Entity.getDocumentItemEntity().getProductEntity().getProductCode())
            .productName(Entity.getDocumentItemEntity().getProductEntity().getProductName())
            .lotCode(Entity.getLotEntity().getLotCode())
            .qty(Entity.getQty())
            .build();
    }


}
