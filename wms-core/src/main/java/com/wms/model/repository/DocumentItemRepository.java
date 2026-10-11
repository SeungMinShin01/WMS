package com.wms.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.LotEntity;

public interface DocumentItemRepository
        extends JpaRepository<DocumentItemEntity, Integer> {
        // 같은 문서에 같은 LOT가 이미 등록됐는지 (입고 품목 등록 중복 검사)
        boolean existsByDocumentEntityAndLotEntity(DocumentEntity documentEntity, LotEntity lotEntity);

        // ED-73 이 문서의 품목 줄만 조회 (전 : findAll 후 for 로 문서번호 비교)
        // join fetch : 품목 줄을 가져올 때 상품도 한 번에 같이 가져옴 (줄마다 상품 SELECT 가 따로 나가지 않게)
        @Query("select i from DocumentItemEntity i join fetch i.productEntity "
                + "where i.documentEntity.documentId = :documentId order by i.documentItemId")
        List<DocumentItemEntity> findByDocumentId(@Param("documentId") Integer documentId);
}