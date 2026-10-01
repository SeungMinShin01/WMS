package com.wms.model.dto.inbound;

import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class InspectionDto {
    private Integer documentItemId;
    private Integer qty;
    private String remark;  // 작업자 비고

    public DocumentItemDetailEntity toEntity(DocumentItemEntity documentItemEntity) {
        return DocumentItemDetailEntity.builder()
                .documentItemEntity(documentItemEntity)
                .lotEntity(documentItemEntity.getLotEntity())
                .qty(this.qty)
                .remark(this.remark)
                .build();
    }

}
