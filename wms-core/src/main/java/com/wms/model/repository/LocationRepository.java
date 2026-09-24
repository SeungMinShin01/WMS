package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.LocationEntity;

public interface LocationRepository
        extends JpaRepository<LocationEntity, Integer> {

}
