package com.wms.controller.tenant;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.tenant.TenantDto;
import com.wms.service.TenantService;

@RestController 
public class TenantController {
    @Autowired private TenantService tenantService;

    @GetMapping ("/wms/tenants")
    public ResponseEntity<List<TenantDto>> 화주전체조회() {
        return ResponseEntity.ok(tenantService.화주전체조회());
    }
}