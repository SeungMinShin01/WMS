package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.ProductEntity;

public interface ProductRepository
        extends JpaRepository<ProductEntity, Integer> {

}
