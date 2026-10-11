package com.wms.model.dto.outbound;

import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.StockEntity;
import com.wms.model.entity.DetailStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationDto {
    private Integer documentItemId;
    private Integer stockId;
    private Integer qty;

    public DocumentItemDetailEntity toEntity(DocumentItemEntity documentItemEntity, StockEntity stockEntity) {
        return DocumentItemDetailEntity.builder()
                .documentItemEntity(documentItemEntity)
                .lotEntity(stockEntity.getLotEntity())
                .locationEntity(stockEntity.getLocationEntity())
                .stockEntity(stockEntity)
                .qty(this.qty)
                .status(DetailStatus.ALLOCATED)
                .build();
    }
}
