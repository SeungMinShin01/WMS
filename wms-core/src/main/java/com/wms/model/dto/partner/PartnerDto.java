package com.wms.model.dto.partner;

import java.security.PublicKey;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.wms.model.entity.PartnerEntity;
import com.wms.model.entity.ProductEntity;
import com.wms.model.entity.TenantEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerDto {
    private Integer tenantId;
    private String tenantCode;
    private String tenantName;

    private Integer partnerId;
    private String partnerCode;
    private String partnerName;
    private String partnerType;
    private String contact;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 1. DTO -> ENTITY
    public PartnerEntity toEntity(TenantEntity tenantEntity) {
        return PartnerEntity.builder()
                .tenantEntity(tenantEntity)
                .partnerCode(this.partnerCode)
                .partnerName(this.partnerName)
                .partnerType(this.partnerType)
                .contact(this.contact)
                .address(this.address)
                .build();
    }

    // 2. ENTITY -> DTO
    public static PartnerDto from(PartnerEntity entity) {
        return PartnerDto.builder()
                .partnerId(entity.getPartnerId())
                .tenantId(entity.getTenantEntity().getTenantId())
                .tenantCode(entity.getTenantEntity().getTenantCode())
                .tenantName(entity.getTenantEntity().getTenantName())
                .partnerCode(entity.getPartnerCode())
                .partnerName(entity.getPartnerName())
                .partnerType(entity.getPartnerType())
                .contact(entity.getContact())
                .address(entity.getAddress())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

}
