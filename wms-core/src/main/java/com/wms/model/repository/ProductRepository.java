package com.wms.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.ProductEntity;
import com.wms.model.entity.TenantEntity;

public interface ProductRepository
        extends JpaRepository<ProductEntity, Integer> {
        // 엑셀 업로드: 그 화주 안에서 품목명으로 찾기
        List<ProductEntity> findByTenantEntityAndProductName(TenantEntity tenantEntity, String productName);
}
