package com.wms.model.entity;

import java.time.LocalDate;

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
@Table(name = "lot")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class LotEntity extends BaseTime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer lotId;

    @JoinColumn(name = "product_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private ProductEntity productEntity;

    private String lotCode;
    private LocalDate expiryDate;
}