package com.wms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wms.controller.SaveException;
import com.wms.model.dto.product.ProductDto;
import com.wms.model.entity.ProductEntity;
import com.wms.model.repository.ProductRepository;

import jakarta.transaction.Transactional;

@Service
public class ProductService {
    @Autowired
    ProductRepository productRepository;

    public Integer 상품등록(ProductDto productDto) {
        ProductEntity entity = productDto.toEntity();
        try{
            ProductEntity savedEntity = productRepository.save(entity);
            if (savedEntity.getProductId() >= 1) {
                return savedEntity.getProductId();
            } else {
                throw new SaveException("상품 등록 실패] PK 생성 실패 했습니다.");
            }
        }catch(Exception e ){ 
            throw new SaveException("상품 등록 실패] 식별정보에 중복이 있습니다."); }
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


                            