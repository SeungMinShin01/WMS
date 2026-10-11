package com.wms.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.ToString;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class ProductEntity extends BaseTime {
    // 유린님
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer productId;

    // 화주 (V4)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private TenantEntity tenantEntity;

    private String productCode;
    private String productName;
    private String spec;
    private String unit;

    @Builder.Default
    private Integer minShipDays = 0;

}
