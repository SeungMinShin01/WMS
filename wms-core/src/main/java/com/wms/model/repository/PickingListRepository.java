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

        // 피킹 리스트 상태가 from 일 때만 to 로 변경 (출고 취소 연동에서 사용)
        // 반환값 0 = 다른 요청이 먼저 바꿨음 → 409
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update PickingListEntity p set p.status = :to where p.pickingListId = :id and p.status = :from")
        int changeStatus(@Param("id") Integer id, @Param("from") PickingStatus from, @Param("to") PickingStatus to);

        // ED-76 내 피킹 리스트 : 이 작업자에게 배정된 것 중 상태가 statusList 안에 있는 것 (최근 것부터)
        // join fetch p.documentEntity : 화면에 문서번호를 보여주려고 문서를 한 번에 같이 가져옴
        @Query("select p from PickingListEntity p join fetch p.documentEntity "
                        + "where p.workerEntity.userId = :workerId and p.status in :statusList "
                        + "order by p.pickingListId desc")
        List<PickingListEntity> findByWorkerId(@Param("workerId") Integer workerId,
                        @Param("statusList") List<PickingStatus> statusList);

        // ED-76 전체 피킹 리스트 (관리자 화면, 최근 것부터)
        // left join fetch p.workerEntity : 배정 전(작업자 null) 리스트도 빠지지 않게
        @Query("select p from PickingListEntity p join fetch p.documentEntity left join fetch p.workerEntity "
                        + "order by p.pickingListId desc")
        List<PickingListEntity> findAllWithDocument();

        // ED-77 첫 스캔 : from(ASSIGNED) 일 때만 to(PICKING) 로 바꾸고 시작 시각 기록
        // 반환값 0 = 같은 순간 다른 스캔이 먼저 시작시킴 → 에러 아님, 그대로 진행
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update PickingListEntity p set p.status = :to, p.startedAt = CURRENT_TIMESTAMP "
                        + "where p.pickingListId = :id and p.status = :from")
        int start(@Param("id") Integer id, @Param("from") PickingStatus from, @Param("to") PickingStatus to);

        // ED-77 마지막 줄까지 다 집음 : from(PICKING) 일 때만 to(DONE) 로 바꾸고 완료 시각 기록
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update PickingListEntity p set p.status = :to, p.completedAt = CURRENT_TIMESTAMP "
                        + "where p.pickingListId = :id and p.status = :from")
        int complete(@Param("id") Integer id, @Param("from") PickingStatus from, @Param("to") PickingStatus to);

        // ED-77 출고 취소 연동 : 이 문서로 만든 피킹 리스트 (없으면 빈 Optional)
        @Query("select p from PickingListEntity p where p.documentEntity.documentId = :documentId")
        Optional<PickingListEntity> findByDocumentId(@Param("documentId") Integer documentId);
}