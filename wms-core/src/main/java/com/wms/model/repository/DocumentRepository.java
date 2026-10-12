package com.wms.model.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentStatus;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Integer> {
        // 문서번호 채번: 같은 접두어(화주-IN-날짜-) 중 가장 큰 번호 1건
        Optional<DocumentEntity> findTopByDocumentNoStartingWithOrderByDocumentNoDesc(String prefix);

        // ED-72 문서 상태가 from 일 때만 to 로 변경
        // 반환값 0 = 다른 요청이 먼저 바꿨음 → 409
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update DocumentEntity d set d.status = :to where d.documentId = :id and d.status = :from")
        int changeStatus(@Param("id") Integer id, @Param("from") DocumentStatus from, @Param("to") DocumentStatus to);
}