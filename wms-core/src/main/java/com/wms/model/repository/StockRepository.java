package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.StockEntity;

public interface StockRepository
        extends JpaRepository<StockEntity, Integer> {

}
