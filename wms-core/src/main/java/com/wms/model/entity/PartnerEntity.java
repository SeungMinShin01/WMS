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
@Table(name = "partner")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PartnerEntity extends BaseTime {
    // 유린님
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer partnerId;

    // 화주 (V4)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private TenantEntity tenantEntity;

    private String partnerCode;
    private String partnerName;
    private String partnerType; // SUPPLIER / CUSTOMER
    private String contact;
    private String address; // 추가

    // 사용 여부 (V10) — false = 삭제(미사용). 실제 DELETE 대신 사용 여부로 막음
    @Builder.Default
    private Boolean isActive = true;
}