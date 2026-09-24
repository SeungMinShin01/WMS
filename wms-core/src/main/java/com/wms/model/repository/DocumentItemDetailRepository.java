package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.DocumentItemDetailEntity;

public interface DocumentItemDetailRepository
        extends JpaRepository<DocumentItemDetailEntity, Integer> {

}
