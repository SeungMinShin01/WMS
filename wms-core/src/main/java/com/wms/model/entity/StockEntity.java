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
@Table(name = "stock")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class StockEntity extends BaseTime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer stockId;

    @JoinColumn(name = "lot_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private LotEntity lotEntity;

    @JoinColumn(name = "location_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private LocationEntity locationEntity;

    @Builder.Default
    private Integer qty = 0;

    @Builder.Default
    private Integer allocatedQty = 0;
}