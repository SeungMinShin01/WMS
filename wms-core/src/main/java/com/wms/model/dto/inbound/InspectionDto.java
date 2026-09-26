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
    // ED-14 : 검수 결과 하나 등록
    private Integer documentItemId;
    private Integer qty;

    public DocumentItemDetailEntity toEntity(DocumentItemEntity documentItemEntity) {
        return DocumentItemDetailEntity.builder()
                .documentItemEntity(documentItemEntity)
                .lotEntity(documentItemEntity.getLotEntity())
                .qty(this.qty)
                .build();
    }

}
