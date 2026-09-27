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

    public static PickingListDto from( DocumentItemDetailEntity documentItemDetailEntity ){
        return PickingListDto.builder()
            .detailId(documentItemDetailEntity.getDetailId())
            .locationCode(documentItemDetailEntity.getLocationEntity().getLocationCode())
            .productCode(documentItemDetailEntity.getDocumentItemEntity().getProductEntity().getProductCode())
            .productName(documentItemDetailEntity.getDocumentItemEntity().getProductEntity().getProductName())
            .lotCode(documentItemDetailEntity.getLotEntity().getLotCode())
            .qty(documentItemDetailEntity.getQty())
            .build();
    }


}
