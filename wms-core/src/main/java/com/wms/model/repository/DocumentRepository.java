package com.wms.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.wms.model.entity.DocumentStatus;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentStatus;
import com.wms.model.entity.DocumentType;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Integer> {
        // [ED-64 조건부 UPDATE] 문서 상태가 from 일 때만 to 로 바꾼다 → 바뀐 행 수 리턴 (0 이면 누가 먼저 바꿈)
        // flushAutomatically : 실행 전에 메모리의 변경을 DB 에 먼저 반영
        // clearAutomatically : 실행 후 영속성 컨텍스트를 비움 → 이후 findById 가 DB 의 최신 값을 다시 읽음
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update DocumentEntity d set d.status = :to where d.documentId = :id and d.status = :from")
        int changeStatus(@Param("id") Integer id, @Param("from") DocumentStatus from, @Param("to") DocumentStatus to);
        // ED-12 출고 문서 목록 조회
        List<DocumentEntity> findByTypeOrderByExpectedAtAsc(DocumentType type);
        // 문서번호 채번: 같은 접두어(화주-IN-날짜-) 중 가장 큰 번호 1건
        Optional<DocumentEntity> findTopByDocumentNoStartingWithOrderByDocumentNoDesc(String prefix);
}
