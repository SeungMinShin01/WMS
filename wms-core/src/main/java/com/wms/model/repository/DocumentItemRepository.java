package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.LotEntity;

public interface DocumentItemRepository
        extends JpaRepository<DocumentItemEntity, Integer> {
        // 같은 문서에 같은 LOT가 이미 등록됐는지 (입고 품목 등록 중복 검사)
        boolean existsByDocumentEntityAndLotEntity(DocumentEntity documentEntity, LotEntity lotEntity);     
}
