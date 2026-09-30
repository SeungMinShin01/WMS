package com.wms.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentType;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Integer> {
        // ED-12 출고 문서 목록 조회
        List<DocumentEntity> findByTypeOrderByExpectedAtAsc(DocumentType type);
}
