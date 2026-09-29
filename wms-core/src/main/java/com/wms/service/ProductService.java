package com.wms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wms.model.dto.product.ProductDto;
import com.wms.model.entity.ProductEntity;
import com.wms.model.repository.ProductRepository;

@Service
public class ProductService {
    @Autowired
    ProductRepository productRepository;

    public boolean 상품등록(ProductDto productDto) {
        ProductEntity entity = productDto.toEntity();
        ProductEntity savedEntity = productRepository.save(entity);
        if (savedEntity.getProductId() >= 1) {
            return true;
        } else {
            return false;
        }
    }
}
