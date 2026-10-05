package com.wms.model.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.LotEntity;
import com.wms.model.entity.ProductEntity;

public interface LotRepository
        extends JpaRepository<LotEntity, Integer> {
                // 같은 품목 + 같은 LOT 번호 찾기(입고 품목 등록 시 재사용)
                Optional<LotEntity> findByProductEntityAndLotCode(ProductEntity productEntity, String lotcode);
}
