package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.DocumentEntity;

public interface DocumentRepository
        extends JpaRepository<DocumentEntity, Integer> {

}
