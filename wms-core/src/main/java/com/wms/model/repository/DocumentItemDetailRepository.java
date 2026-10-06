package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.DetailStatus;
import com.wms.model.entity.DocumentItemDetailEntity;

public interface DocumentItemDetailRepository
        extends JpaRepository<DocumentItemDetailEntity, Integer> {
        // 적치용: 상태가 current일 때만 next로 변경, 바뀐 행 수 반환(0 = 다른 요청이 먼저 바꿈)
        @Modifying
        @Query("update DocumentItemDetailEntity d set d.status = : next " + "where d. detailId = : detailId and d.status = :current")
        int changeStatus(@Param("detailId")Integer detailId, @Param("current") DetailStatus current, @Param("next") DetailStatus next);       
}
