package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.DetailStatus;
import com.wms.model.entity.DocumentItemDetailEntity;

public interface DocumentItemDetailRepository
        extends JpaRepository<DocumentItemDetailEntity, Integer> {

        // ED-72 상세(피킹 1줄) 상태가 from 일 때만 to 로 변경 (피킹 확인용)
        // 반환값 0 = 이미 처리된 줄 → 409
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update DocumentItemDetailEntity d set d.status = :to where d.detailId = :id and d.status = :from")
        int changeStatus(@Param("id") Integer id, @Param("from") DetailStatus from, @Param("to") DetailStatus to);
}