package com.wms.model.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.LotEntity;
import com.wms.model.entity.ProductEntity;

public interface LotRepository
        extends JpaRepository<LotEntity, Integer> {
                // 같은 품목 + 같은 LOT 번호 찾기(입고 품목 등록 시 재사용)
                Optional<LotEntity> findByProductEntityAndLotCode(ProductEntity productEntity, String lotcode);
                // 같은 품목 + 같은 제조일자·공급사(접두어)의 LOT가 이미 있는지 (있으면 재사용)
                Optional<LotEntity> findFirstByProductEntityAndLotCodeStartingWith(ProductEntity productEntity, String prefix);
                // 같은 접두어의 LOT 중 가장 큰 번호 (품목 상관없이, 순번 계산용)
                Optional<LotEntity> findTopByLotCodeStartingWithOrderByLotCodeDesc(String prefix);
}
