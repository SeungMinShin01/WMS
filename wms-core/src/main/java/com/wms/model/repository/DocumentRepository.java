package com.wms.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentType;

import jakarta.persistence.LockModeType;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Integer> {
        // ED-12 출고 문서 목록 조회
        List<DocumentEntity> findByTypeOrderByExpectedAtAsc(DocumentType type);
        // 문서번호 채번: 같은 접두어(화주-IN-날짜-) 중 가장 큰 번호 1건
        Optional<DocumentEntity> findTopByDocumentNoStartingWithOrderByDocumentNoDesc(String prefix);

        // [ED-64 비관적 락]
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select d from DocumentEntity d where d.documentId = :id")
        Optional<DocumentEntity> findByIdForUpdate(@Param("id") Integer id);
}
