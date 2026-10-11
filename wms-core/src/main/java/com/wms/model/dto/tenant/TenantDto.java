package com.wms.model.dto.tenant;

import com.wms.model.entity.TenantEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor 
public class TenantDto {
    private Integer tenantId;
    private String tenantCode;
    private String tenantName;

    public static TenantDto from(TenantEntity e) {
        return TenantDto.builder()
                .tenantId(e.getTenantId())
                .tenantCode(e.getTenantCode())
                .tenantName(e.getTenantName())
                .build();
    }
}