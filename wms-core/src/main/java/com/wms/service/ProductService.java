package com.wms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils; // 필수값 검사용 (스프링에 이미 들어 있음)

import com.wms.controller.SaveException;
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
    private TenantRepository tenantRepository; // 추가

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
        if (!errors.isEmpty()) { // 추가: 모은 오류를 한 번에 던진다 (이 줄이 빠져 있었어요)
            throw new IllegalArgumentException(String.join(", ", errors));
        }

        // 2. 화주 찾기 → 없으면 404 (EntityNotFoundException)
        TenantEntity tenant = tenantRepository.findById(productDto.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("화주가 없습니다: " + productDto.getTenantId()));

        // 3. 같은 화주 안에서 품목코드 중복 → 409 (IllegalStateException)
        // 다른 화주의 같은 코드는 허용
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

    public List<ProductDto> 상품전체조회() {
        List<ProductEntity> entities = productRepository.findAll(); // findAll 전체조회
        List<ProductDto> list = new ArrayList<>(); // 2. 엔티티 -> dto 변환
        entities.forEach((entity) -> {
            ProductDto dto = ProductDto.from(entity);
            list.add(dto);
        });
        return list;
    }

    public ProductDto 상품개별조회(int productid) {
        Optional<ProductEntity> optional = productRepository.findById(productid);
        if (optional.isPresent()) {// 2. 조회 결과 존재하면
            ProductEntity entity = optional.get(); // 3. 엔티티 꺼내기
            return ProductDto.from(entity);
        }
        return null; // 참조(객체) 에서는 null 없다는 뜻
    }

    @Transactional
    public boolean 상품수정(ProductDto productDto) {
        Optional<ProductEntity> optional = productRepository.findById(productDto.getProductId());
        if (optional.isPresent()) { // 2. 존재하면 엔티티 수정한다.
            ProductEntity entity = optional.get();
            entity.setProductCode(productDto.getProductCode());
            entity.setProductName(productDto.getProductName());
            entity.setSpec(productDto.getSpec());
            entity.setUnit(productDto.getUnit());
            return true;
        }
        return false;
    }
}
