package com.wms.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.StockEntity;

public interface StockRepository
        extends JpaRepository<StockEntity, Integer> {

        // ED-70 적재: 같은 LOT + 같은 칸 재고 줄이 있으면 DB에서 실물+q
        // 전 : 행을 읽어 자바에서 qty + q 계산 → 행 전체 UPDATE (그 사이 할당이 바꾼 선점이 옛 값으로 덮어써짐)
        // 반환값 0 = 그 칸에 그 LOT 줄이 없음 → 새 줄 INSERT
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update StockEntity s set s.qty = s.qty + :q "
                + "where s.lotEntity.lotId = :lotId and s.locationEntity.locationId = :locationId")
        int addQty(@Param("lotId") Integer lotId, @Param("locationId") Integer locationId, @Param("q") Integer q);

        // ED-70 같은 LOT + 같은 칸 재고 줄 (적재 후 검수 기록에 연결할 재고)
        Optional<StockEntity> findByLotEntityLotIdAndLocationEntityLocationId(Integer lotId, Integer locationId);

        // ED-71 칸 하나의 실물 합계 (적재 용량 검사) - 전에는 재고 findall 후 for로 더함
        @Query("select coalesce(sum(s.qty), 0) from StockEntity s where s.locationEntity.locationId = :locationId")
        Long sumQtyByLocationId(@Param("locationId") Integer locationId);

        // ED-71 칸에 다른 화주 재고 줄 수 (수량 0 줄은 빈칸으로) - 전에는 재고 findall 후 for
        @Query("select count(s) from StockEntity s where s.locationEntity.locationId = :locationId "
                + "and s.qty > 0 and s.tenantEntity.tenantId <> :tenantId")
        long countOtherTenant(@Param("locationId") Integer locationId, @Param("tenantId") Integer tenantId);

        // ED-71 적치 추천: 수량있는 재고만, LOT-품목-칸-화주를 한번에 (줄마다 SELECT가 따로 안 나가게)
        @Query("select s from StockEntity s join fetch s.lotEntity l join fetch l.productEntity "
                + "join fetch s.locationEntity join fetch s.tenantEntity where s.qty > 0")
        List<StockEntity> findAllForRecommend();

        // 폐기 : 읽은 실물 그대로 선점이 0일때만 실물 0
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update StockEntity s set s.qty = 0 "
                + "where s.stockId = :id and s.qty = :q and s.allocatedQty = 0")
        int dispose(@Param("id") Integer id, @Param("q") Integer q);



        // ED-72 할당 : 가용(실물 - 선점)이 q 이상일 때만 선점 + q
        // 반환값 = 바뀐 행 수 (0 이면 가용 부족 → 409)
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update StockEntity s set s.allocatedQty = s.allocatedQty + :q "
                + "where s.stockId = :id and s.qty - s.allocatedQty >= :q")
        int allocate(@Param("id") Integer id, @Param("q") Integer q);

        // ED-72 출고확정 : 실물·선점이 둘 다 q 이상일 때만 실물 - q, 선점 - q
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update StockEntity s set s.qty = s.qty - :q, s.allocatedQty = s.allocatedQty - :q "
                + "where s.stockId = :id and s.qty >= :q and s.allocatedQty >= :q")
        int ship(@Param("id") Integer id, @Param("q") Integer q);

        // ED-72 출고취소 : 선점이 q 이상일 때만 선점 - q
        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update StockEntity s set s.allocatedQty = s.allocatedQty - :q "
                + "where s.stockId = :id and s.allocatedQty >= :q")
        int release(@Param("id") Integer id, @Param("q") Integer q);

        // ED-73 상품 1개의 재고만 조회 (전 : 재고 전체를 findAll 해서 상품별 Map 으로 나눔)
        // 출고 가능 규칙(failReason)에서 쓰는 LOT·칸·화주를 한 번에 같이 가져옴
        @Query("select s from StockEntity s join fetch s.lotEntity l join fetch s.locationEntity "
                + "join fetch s.tenantEntity where l.productEntity.productId = :productId")
        List<StockEntity> findByProductId(@Param("productId") Integer productId);
}