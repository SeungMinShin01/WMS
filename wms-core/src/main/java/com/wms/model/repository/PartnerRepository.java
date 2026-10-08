package com.wms.model.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.PartnerEntity;
import com.wms.model.entity.TenantEntity;

public interface PartnerRepository
        extends JpaRepository<PartnerEntity, Integer> {
        // 엑셀 업로드: 거래처코드는 화주별로 유일
        Optional<PartnerEntity> findByTenantEntityAndPartnerCode(TenantEntity tenantEntity, String partnerCode);
}
