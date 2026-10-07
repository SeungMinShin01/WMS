package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wms.model.entity.StockEntity;

public interface StockRepository
        extends JpaRepository<StockEntity, Integer> {
                // [ED-64 조건부 UPDATE] 할당 : 가용(qty − allocated)이 q 이상일 때만 선점 += q
        @Modifying(flushAutomatically = true)
        @Query("update StockEntity s set s.allocatedQty = s.allocatedQty + :q where s.stockId = :id and s.qty - s.allocatedQty >= :q")
        int allocate(@Param("id") Integer id, @Param("q") Integer q);

        // [ED-64 조건부 UPDATE] 출고 : 실물·선점이 둘 다 q 이상일 때만 실물 −= q, 선점 −= q
        @Modifying(flushAutomatically = true)
        @Query("update StockEntity s set s.qty = s.qty - :q, s.allocatedQty = s.allocatedQty - :q where s.stockId = :id and s.qty >= :q and s.allocatedQty >= :q")
        int ship(@Param("id") Integer id, @Param("q") Integer q);

}
