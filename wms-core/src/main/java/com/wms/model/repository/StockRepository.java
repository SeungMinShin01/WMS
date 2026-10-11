package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.StockEntity;

public interface StockRepository
        extends JpaRepository<StockEntity, Integer> {

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
}