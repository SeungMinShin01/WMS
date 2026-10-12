package com.wms.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.TenantEntity;

public interface TenantRepository
        extends JpaRepository<TenantEntity, Integer> {
        // 엑셀 업로드: 화주명으로 찾기 (이름은 유일 보장이 없어 List)
        List<TenantEntity> findByTenantName(String tenantName);
}