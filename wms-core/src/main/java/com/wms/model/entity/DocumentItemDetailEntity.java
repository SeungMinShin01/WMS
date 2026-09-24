package com.wms.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "document_item_detail")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class DocumentItemDetailEntity extends BaseTime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer detailId;

    @JoinColumn(name = "document_item_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private DocumentItemEntity documentItemEntity;

    @JoinColumn(name = "lot_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private LotEntity lotEntity;

    @JoinColumn(name = "location_id") // 적재 전 null
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private LocationEntity locationEntity;

    @JoinColumn(name = "stock_id") // 적재 전 null
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private StockEntity stockEntity;

    private Integer qty;
}