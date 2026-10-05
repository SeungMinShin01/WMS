package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.TenantEntity;

public interface TenantRepository
        extends JpaRepository<TenantEntity, Integer> {
}