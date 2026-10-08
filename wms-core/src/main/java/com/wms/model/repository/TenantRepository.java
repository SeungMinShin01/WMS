package com.wms.model.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.TenantEntity;

public interface TenantRepository
        extends JpaRepository<TenantEntity, Integer> {
        // 엑셀 업로드: 화주코드로 찾기
        Optional<TenantEntity> findByTenantCode(String tenantCode);
}