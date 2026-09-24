package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.DocumentItemEntity;

public interface DocumentItemRepository
        extends JpaRepository<DocumentItemEntity, Integer> {

}
