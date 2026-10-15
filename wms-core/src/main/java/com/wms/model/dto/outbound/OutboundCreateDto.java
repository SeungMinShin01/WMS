package com.wms.model.dto.outbound;

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
public class OutboundCreateDto {
    // 출고 문서 헤더 등록 요청 — 화주·납품처는 id만 받는다 (입고 InboundCreateDto 와 같은 모양)
    private Integer tenantId;
    private Integer partnerId;
    private LocalDate expectedDate; // 출고요청일 (yyyy-MM-dd)

    public DocumentEntity toEntity(TenantEntity tenant, PartnerEntity partner, String documentNo) {
        return DocumentEntity.builder()
                .tenantEntity(tenant)
                .partnerEntity(partner)
                .documentNo(documentNo)
                .type(DocumentType.OUTBOUND)
                .expectedAt(this.expectedDate.atStartOfDay())
                .shipAddress(partner.getAddress())   // 납품처 주소를 문서에 복사
                .shipContact(partner.getContact())   // 납품처 연락처를 문서에 복사
                .build(); // status=WAITING, source=WMS 는 엔티티 기본값
    }
}