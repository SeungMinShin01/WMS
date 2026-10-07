package com.wms.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentType;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Integer> {
        // 문서번호 채번: 같은 접두어(화주-IN-날짜-) 중 가장 큰 번호 1건
        Optional<DocumentEntity> findTopByDocumentNoStartingWithOrderByDocumentNoDesc(String prefix);
}
