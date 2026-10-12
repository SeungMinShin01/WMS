package com.wms.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wms.model.dto.tenant.TenantDto;
import com.wms.model.repository.TenantRepository;

import org.springframework.transaction.annotation.Transactional;

@Service 
@Transactional (readOnly = true)
public class TenantService {
    @Autowired private TenantRepository tenantRepository;

    public List<TenantDto> 화주전체조회() {
        List<TenantDto> list = new ArrayList<>();
        tenantRepository.findAll().forEach(e -> list.add(TenantDto.from(e)));
        return list;
    }
}