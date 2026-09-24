package com.wms.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.PartnerEntity;

public interface PartnerRepository
        extends JpaRepository<PartnerEntity, Integer> {

}
