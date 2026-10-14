package com.wms.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.PickingListItemEntity;

public interface PickingListItemRepository extends JpaRepository<PickingListItemEntity, Integer> {
        // 피킹 리스트 1개의 품목 줄 전체 (집는 순서대로)
        // ED-76 할당 줄 → 품목 줄 → 상품 / LOT / 칸을 한 번에 같이 가져옴
        //   (안 하면 줄 10개일 때 상품·LOT·칸 조회가 줄마다 따로 나가서 쿼리가 30개 넘게 나감)
        @Query("select i from PickingListItemEntity i "
                        + "join fetch i.detailEntity d join fetch d.documentItemEntity di join fetch di.productEntity "
                        + "join fetch d.lotEntity left join fetch d.locationEntity "
                        + "where i.pickingListEntity.pickingListId = :pickingListId order by i.seq")
        List<PickingListItemEntity> findByPickingListId(@Param("pickingListId") Integer pickingListId);

        // ED-76 피킹 리스트 1개의 품목 줄 수 (목록 화면용, 줄 내용은 필요 없어서 개수만)
        @Query("select count(i) from PickingListItemEntity i where i.pickingListEntity.pickingListId = :pickingListId")
        int countByPickingListId(@Param("pickingListId") Integer pickingListId);

        // ED-77 집은 수량 더하기 : 더한 값이 할당 수량(maxQty)을 넘지 않을 때만
        // 자바에서 더해서 save 하지 않고 DB 가 더함 (ED-72 allocate 와 같은 방식)
        // 반환값 0 = 할당 수량을 넘음 (같은 줄을 두 사람이 동시에 찍은 경우 포함) → 409
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update PickingListItemEntity i set i.pickedQty = i.pickedQty + :qty "
                        + "where i.pickingListItemId = :id and i.pickedQty + :qty <= :maxQty")
        int addPickedQty(@Param("id") Integer id, @Param("qty") Integer qty, @Param("maxQty") Integer maxQty);

        // ED-77 한 줄을 다 집은 시각 기록
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update PickingListItemEntity i set i.pickedAt = CURRENT_TIMESTAMP where i.pickingListItemId = :id")
        int markPicked(@Param("id") Integer id);

        // ED-77 아직 다 못 집은 줄 수 (집은 수량 < 할당 수량) → 0 이면 리스트 전체 완료
        @Query("select count(i) from PickingListItemEntity i "
                        + "where i.pickingListEntity.pickingListId = :pickingListId and i.pickedQty < i.detailEntity.qty")
        int countNotDone(@Param("pickingListId") Integer pickingListId);

        // ED-77 출고 취소 연동 : 피킹 리스트의 품목 줄 전부 삭제
        // 품목 줄이 할당 줄(detail)을 FK 로 물고 있어서, 할당 줄을 지우기 전에 먼저 지워야 함
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("delete from PickingListItemEntity i where i.pickingListEntity.pickingListId = :pickingListId")
        int deleteByPickingListId(@Param("pickingListId") Integer pickingListId);
}