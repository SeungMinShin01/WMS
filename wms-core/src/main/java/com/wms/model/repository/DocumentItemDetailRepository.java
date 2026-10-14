package com.wms.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.DetailStatus;
import com.wms.model.entity.DocumentItemDetailEntity;

public interface DocumentItemDetailRepository
        extends JpaRepository<DocumentItemDetailEntity, Integer> {

        // ED-71 이 문서에서 status 가 아닌 상세 줄 수 (입고 완료 판단) — 전 : 상세 findAll 후 for
        @Query("select count(d) from DocumentItemDetailEntity d "
                + "where d.documentItemEntity.documentEntity.documentId = :documentId and d.status <> :status")
        long countByDocumentIdAndStatusNot(@Param("documentId") Integer documentId, @Param("status") DetailStatus status);

        // 입고 취소 검사: 문서에 특정 상태(STORED)인 상세 줄이 몇개인지
        @Query("select count(d) from DocumentItemDetailEntity d "
                + "where d.documentItemEntity.documentEntity.documentId = :documentId and d.status = :status")
        long countByDocumentIdAndStatus(@Param("documentId") Integer documentId, @Param("status") DetailStatus status);

        // ED-71 이 품목 줄에 검수 기록이 있나
        // 메소드 이름 쿼리: documentItemEntity.documentItemId 로 찾아서 있으면 true
        boolean existsByDocumentItemEntity_DocumentItemId(Integer documentItemId);

        // ED-72 상세(피킹 1줄) 상태가 from 일 때만 to 로 변경 (피킹 확인용)
        // 반환값 0 = 이미 처리된 줄 → 409
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update DocumentItemDetailEntity d set d.status = :to where d.detailId = :id and d.status = :from")
        int changeStatus(@Param("id") Integer id, @Param("from") DetailStatus from, @Param("to") DetailStatus to);

        // ED-73 품목 줄 1개에 할당된 수량 합계를 DB 에서 계산 (전 : 상세 전체를 for 로 돌며 더함)
        // sum : 합계 / coalesce(값, 0) : 합칠 행이 없어서 null 이면 0 으로
        // JPA 의 sum 결과는 Long 이라 Long 으로 받음
        @Query("select coalesce(sum(d.qty), 0) from DocumentItemDetailEntity d "
                + "where d.documentItemEntity.documentItemId = :documentItemId")
        Long sumQtyByDocumentItemId(@Param("documentItemId") Integer documentItemId);

        // ED-73 이 문서의 상세(피킹 줄)만 조회 (전 : findAll 후 줄 → 품목 줄 → 문서로 올라가 비교)
        // 피킹리스트 화면에 필요한 품목 줄·상품·LOT·칸·재고를 한 번에 같이 가져옴
        // left join fetch : 칸·재고가 비어 있는 줄(입고 적재 전)도 빠지지 않게
        @Query("select d from DocumentItemDetailEntity d "
                + "join fetch d.documentItemEntity i join fetch i.productEntity join fetch d.lotEntity "
                + "left join fetch d.locationEntity left join fetch d.stockEntity "
                + "where i.documentEntity.documentId = :documentId order by d.detailId")
        List<DocumentItemDetailEntity> findByDocumentId(@Param("documentId") Integer documentId);
}