package com.wms.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.PickingListEntity;
import com.wms.model.entity.PickingStatus;
import com.wms.model.entity.UserEntity;

public interface PickingListRepository extends JpaRepository<PickingListEntity, Integer> {
        // 피킹번호 채번: 같은 접두어(PK-날짜-) 중 가장 큰 번호 1건
        Optional<PickingListEntity> findTopByPickingNoStartingWithOrderByPickingNoDesc(String prefix);

        // 이 문서로 만든 피킹 리스트가 이미 있는지 (있으면 409)
        @Query("select count(p) > 0 from PickingListEntity p where p.documentEntity.documentId = :documentId")
        boolean existsByDocumentId(@Param("documentId") Integer documentId);

        // ED-75 작업자 배정: 상태가 fromList(CREATED, ASSIGNED) 안에 있을 때만 배정
        // 반환값 0 = 이미 피킹 시작했거나 끝났거나 취소됨 → 409
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update PickingListEntity p set p.workerEntity = :worker, p.status = :to, p.assignedAt = CURRENT_TIMESTAMP "
                        + "where p.pickingListId = :id and p.status in :fromList")
        int assign(@Param("id") Integer id, @Param("worker") UserEntity worker,
                        @Param("fromList") List<PickingStatus> fromList, @Param("to") PickingStatus to);

        // 피킹 리스트 상태가 from 일 때만 to 로 변경 (ED-77 스캔 시작·완료·취소에서 사용)
        // 반환값 0 = 다른 요청이 먼저 바꿨음 → 409
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update PickingListEntity p set p.status = :to where p.pickingListId = :id and p.status = :from")
        int changeStatus(@Param("id") Integer id, @Param("from") PickingStatus from, @Param("to") PickingStatus to);
}