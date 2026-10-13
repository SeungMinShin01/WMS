package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.wms.model.entity.StockDisposalEntity;

import java.util.List;

public interface StockDisposalRepository extends JpaRepository<StockDisposalEntity, Integer>{
    // 입출고 이력용: 폐기 기록 + 재고-lot-품목-칸-처리자를 한번에
        @Query("select d from StockDisposalEntity d join fetch d.stockEntity s join fetch s.lotEntity l "
                + "join fetch l.productEntity join fetch s.locationEntity join fetch d.userEntity")
        List<StockDisposalEntity> findAllForHistory();
}
