package com.wms.service;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;   // 필수값 검사용 (스프링에 이미 들어 있음)

import com.wms.controller.SaveException;
import com.wms.model.dto.product.ProductDto;
import com.wms.model.entity.ProductEntity;
import com.wms.model.entity.TenantEntity;
import com.wms.model.repository.ProductRepository;
import com.wms.model.repository.TenantRepository;

import jakarta.transaction.Transactional;

@Service
public class ProductService {
    @Autowired
    ProductRepository productRepository;
    @Autowired
    private TenantRepository tenantRepository;   // 추가

    public Integer 상품등록(ProductDto productDto) {
    // 추가: 필수값 검사 (없으면 거부)
    if (productDto.getTenantId() == null
            || !StringUtils.hasText(productDto.getProductCode())
            || !StringUtils.hasText(productDto.getProductName())) {
        throw new SaveException("상품 등록 실패] 필수값이 없습니다.");
    }
    // 추가: 화주 조회 (없으면 거부)
    TenantEntity tenant = tenantRepository.findById(productDto.getTenantId())
            .orElseThrow(() -> new SaveException("상품 등록 실패] 없는 화주입니다."));

    ProductEntity entity = productDto.toEntity(tenant); // 변경: toEntity() → toEntity(tenant)
    
        ProductEntity savedEntity = productRepository.save(entity);
        if (savedEntity.getProductId() >= 1) {
            return savedEntity.getProductId();
        } else {
                throw new SaveException("상품 등록 실패] PK 생성 ");
            }
   
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
        if(optional.isPresent()) {// 2. 조회 결과 존재하면
            ProductEntity entity = optional.get(); // 3. 엔티티 꺼내기
            return ProductDto.from(entity);
        }
        return null; // 참조(객체) 에서는 null 없다는 뜻
    }

    @Transactional
    public boolean 상품수정( ProductDto productDto ){
        Optional<ProductEntity> optional
            = productRepository.findById( productDto.getProductId() );
        if( optional.isPresent() ){ // 2. 존재하면 엔티티 수정한다.
            ProductEntity entity = optional.get();
            entity.setProductCode( productDto.getProductCode() );
            entity.setProductName( productDto.getProductName() );
            entity.setSpec( productDto.getSpec() );
            entity.setUnit( productDto.getUnit() );
            return true;
        }
        return false;
    }
}


                            