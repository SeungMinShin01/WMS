package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.LotEntity;

public interface LotRepository
        extends JpaRepository<LotEntity, Integer> {

}
