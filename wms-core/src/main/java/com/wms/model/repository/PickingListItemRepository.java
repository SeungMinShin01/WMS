package com.wms.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.PickingListItemEntity;

public interface PickingListItemRepository extends JpaRepository<PickingListItemEntity, Integer> {
        // 피킹 리스트 1개의 품목 줄 전체 (집는 순서대로)
        @Query("select i from PickingListItemEntity i where i.pickingListEntity.pickingListId = :pickingListId order by i.seq")
        List<PickingListItemEntity> findByPickingListId(@Param("pickingListId") Integer pickingListId);
}