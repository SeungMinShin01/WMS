package com.wms.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.LocationEntity;

public interface LocationRepository
        extends JpaRepository<LocationEntity, Integer> {
        // ED-71 사용 중인 칸만 (적치 추천) - 전에는 findall 후 isActive로 거름
        List<LocationEntity> findByIsActiveTrue();
}
