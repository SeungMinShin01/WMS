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
@Table(name = "tenant")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class TenantEntity extends BaseTime {
    // 화주 (V4 시드 3곳 고정: HLT / BEV / FOD)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer tenantId;

    private String tenantCode; // 문서번호·코드 접두어
    private String tenantName; // 회사명
}