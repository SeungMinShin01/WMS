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
@Table(name = "document_item")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class DocumentItemEntity extends BaseTime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer documentItemId;

    @JoinColumn(name = "document_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private DocumentEntity documentEntity;

    @JoinColumn(name = "product_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private ProductEntity productEntity;

    @JoinColumn(name = "lot_id") // 입고는 채움, 출고는 null
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private LotEntity lotEntity;

    private Integer expectedQty;
}