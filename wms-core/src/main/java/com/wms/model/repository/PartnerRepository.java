package com.wms.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.PartnerEntity;
import com.wms.model.entity.TenantEntity;

public interface PartnerRepository
        extends JpaRepository<PartnerEntity, Integer> {
        // 엑셀 업로드: 그 화주 안에서 거래처명으로 찾기
        List<PartnerEntity> findByTenantEntityAndPartnerName(TenantEntity tenantEntity, String partnerName);
}
