package com.wms.model.dto.outbound;

import java.lang.annotation.Documented;

import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.StockEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class AllocationDto {
    // ED - 18 : 할당 1건
    private Integer documentItemId;
    private Integer stockId;
    private Integer qty;

    public DocumentItemDetailEntity toEntity(DocumentItemEntity documentItemEntity, StockEntity entity) {
        return DocumentItemDetailEntity.builder()
                .documentItemEntity(documentItemEntity)
                .lotEntity(entity.getLotEntity())
                .locationEntity(entity.getLocationEntity())
                .stockEntity(entity)
                .qty(this.qty)
                .build();
    }

}
