package com.wms.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.wms.model.dto.product.ProductDto;
import com.wms.model.entity.ProductEntity;
import com.wms.model.entity.TenantEntity;
import com.wms.model.repository.ProductRepository;
import com.wms.model.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional(readOnly = true)
public class ProductService {
    @Autowired
    ProductRepository productRepository;
    @Autowired
    private TenantRepository tenantRepository;

    @Transactional
    public Integer 상품등록(ProductDto productDto) {

        // 1. 필수값 검사 → 400 (비운 항목을 모두 모아서 한 번에 알린다)
        List<String> errors = new ArrayList<>();
        if (productDto.getTenantId() == null) {
            errors.add("화주를 선택하세요");
        }
        if (!StringUtils.hasText(productDto.getProductCode())) {
            errors.add("품목코드는 필수입니다");
        }
        if (!StringUtils.hasText(productDto.getProductName())) {
            errors.add("품목명은 필수입니다");
        }
        if (!StringUtils.hasText(productDto.getSpec())) {
            errors.add("규격은 필수입니다");
        }
        if (!StringUtils.hasText(productDto.getUnit())) {
            errors.add("단위는 필수입니다");
        }
        if (productDto.getMinShipDays() != null && productDto.getMinShipDays() < 0) {
            errors.add("출고허용 잔여일은 0 이상입니다");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }

        // 2. 화주 찾기 → 없으면 404
        TenantEntity tenant = tenantRepository.findById(productDto.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("화주가 없습니다: " + productDto.getTenantId()));

        // 3. 같은 화주 안에서 품목코드 중복 → 409 (다른 화주의 같은 코드는 허용)
        for (ProductEntity p : productRepository.findAll()) {
            boolean sameTenant = p.getTenantEntity().getTenantId().equals(tenant.getTenantId());
            boolean sameCode = p.getProductCode().equals(productDto.getProductCode());
            if (sameTenant && sameCode) {
                throw new IllegalStateException("이미 등록된 품목코드입니다: " + productDto.getProductCode());
            }
        }

        // 4. 저장 → 새 품목 번호를 돌려준다
        ProductEntity saved = productRepository.save(productDto.toEntity(tenant));
        return saved.getProductId();
    }

    // [변경] tenantId가 있으면 그 화주 품목만, 없으면(null) 전체
    public List<ProductDto> 상품전체조회(Integer tenantId) {
        List<ProductDto> list = new ArrayList<>();
        for (ProductEntity entity : productRepository.findAll()) {
            if (tenantId == null || entity.getTenantEntity().getTenantId().equals(tenantId)) {
                list.add(ProductDto.from(entity));
            }
        }
        return list;
    }

    // [변경] 없으면 null(200)이 아니라 404
    public ProductDto 상품개별조회(int productid) {
        ProductEntity entity = productRepository.findById(productid)
                .orElseThrow(() -> new EntityNotFoundException("품목이 없습니다: " + productid));
        return ProductDto.from(entity);
    }

    // [변경] 404 / 화주 변경 400 / 필수값 400 / 같은 화주 코드 중복 409 / minShipDays 수정
    @Transactional
    public boolean 상품수정(ProductDto productDto) {
        // 1. 없으면 404
        if (productDto.getProductId() == null) {
            throw new IllegalArgumentException("품목 번호가 없습니다");
        }
        ProductEntity entity = productRepository.findById(productDto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("품목이 없습니다: " + productDto.getProductId()));

        // 2. 화주는 바꿀 수 없다 → 400
        Integer currentTenantId = entity.getTenantEntity().getTenantId();
        if (productDto.getTenantId() != null && !productDto.getTenantId().equals(currentTenantId)) {
            throw new IllegalArgumentException("화주는 바꿀 수 없습니다");
        }

        // 3. 필수값 검사 → 400 (비운 항목을 모아서)
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(productDto.getProductCode())) {
            errors.add("품목코드는 필수입니다");
        }
        if (!StringUtils.hasText(productDto.getProductName())) {
            errors.add("품목명은 필수입니다");
        }
        if (!StringUtils.hasText(productDto.getSpec())) {
            errors.add("규격은 필수입니다");
        }
        if (!StringUtils.hasText(productDto.getUnit())) {
            errors.add("단위는 필수입니다");
        }
        if (productDto.getMinShipDays() != null && productDto.getMinShipDays() < 0) {
            errors.add("출고허용 잔여일은 0 이상입니다");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }

        // 4. 같은 화주 + 같은 코드인데 자기 자신이 아닌 것이 있으면 → 409
        for (ProductEntity p : productRepository.findAll()) {
            boolean sameTenant = p.getTenantEntity().getTenantId().equals(currentTenantId);
            boolean sameCode = p.getProductCode().equals(productDto.getProductCode());
            boolean self = p.getProductId().equals(entity.getProductId());
            if (sameTenant && sameCode && !self) {
                throw new IllegalStateException("이미 등록된 품목코드입니다: " + productDto.getProductCode());
            }
        }

        // 5. 값 바꾸기 (화주는 안 바꿈)
        entity.setProductCode(productDto.getProductCode());
        entity.setProductName(productDto.getProductName());
        entity.setSpec(productDto.getSpec());
        entity.setUnit(productDto.getUnit());
        if (productDto.getMinShipDays() != null) {
            entity.setMinShipDays(productDto.getMinShipDays());
        }
        return true;
    }
}