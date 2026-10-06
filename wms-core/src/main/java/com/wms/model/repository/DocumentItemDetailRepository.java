package com.wms.model.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.DocumentItemDetailEntity;

import jakarta.persistence.LockModeType;

public interface DocumentItemDetailRepository
        extends JpaRepository<DocumentItemDetailEntity, Integer> {
        // 적치용: 검수 기록을 읽는 순간 행 잠금 - 커밋까지 다른 요청은 대기
        @Lock (LockModeType.PESSIMISTIC_WRITE)
        @Query("select d from DocumentItemDetailEntity d where d.detailId = :detailId")
        Optional<DocumentItemDetailEntity> findByIdForUpdate(@Param("detailId") Integer detailId);
}
