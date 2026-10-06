package com.wms.model.dto.inbound;

import java.time.LocalDate;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentType;
import com.wms.model.entity.PartnerEntity;
import com.wms.model.entity.TenantEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class InboundCreateDto {
    // 입고 문서 헤더 등록 요청 — 화주·거래처는 id만 받는다
    private Integer tenantId;
    private Integer partnerId;
    private LocalDate expectedDate;   // 입고예정일 (yyyy-MM-dd)

    // DTO -> ENTITY : 화주·거래처는 서비스가 찾아온 엔티티, 문서번호는 서비스가 만든 값
    public DocumentEntity toEntity(TenantEntity tenant, PartnerEntity partner, String documentNo) {
        return DocumentEntity.builder()
                .tenantEntity(tenant)
                .partnerEntity(partner)
                .documentNo(documentNo)
                .type(DocumentType.INBOUND)
                .expectedAt(this.expectedDate.atStartOfDay())
                .build();   // status=WAITING, source=WMS 는 엔티티 기본값
    }
}