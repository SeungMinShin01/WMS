package com.wms.model.dto.inbound;

import java.time.LocalDate;

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
    // ED-15 품목별 검수 현황(검수 전이면 detailId / qty / locationCode 가 null)
    private Integer documentItemId;
    private String productName;
    private String lotCode;
    private Integer expectedQty;
    private Integer detailId;
    private Integer qty;
    private String locationCode;
    private String remark;
    private String status;  // detail 상태

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
                .remark(entity.getRemark())
                .status(entity.getStatus().name())
                .build();
    }
}
