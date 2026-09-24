package com.wms.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "partner")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PartnerEntity extends BaseTime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer partnerId;

    private String partnerCode;
    private String partnerName;
    private String partnerType; // SUPPLIER / CUSTOMER
    private String contact;
}