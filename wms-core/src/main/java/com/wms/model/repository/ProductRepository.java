package com.wms.model.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.ProductEntity;
import com.wms.model.entity.TenantEntity;

public interface ProductRepository
        extends JpaRepository<ProductEntity, Integer> {
        // 엑셀 업로드: 품목코드는 화주별로 유일
        Optional<ProductEntity> findByTenantEntityAndProductCode(TenantEntity tenantEntity, String productCode);
}
