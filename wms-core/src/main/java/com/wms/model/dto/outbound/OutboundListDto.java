package com.wms.model.dto.outbound;

import java.time.LocalDateTime;

import com.wms.model.entity.DocumentEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboundListDto {
    private Integer documentId;
    private String documentNo; // IN-20261001-001
    private String type; // INBOUND / OUTBOUND
    private Integer tenantId;     // 화주 번호 (ED-61 추가)
    private String tenantName;    // 화주 이름 (ED-61 추가)
    private String source;        // 들어온 경로 WMS / PORTAL (ED-61 추가)
    private String partnerName;
    private LocalDateTime expectedAt;
    private LocalDateTime completedAt;
    private String status;
    private LocalDateTime createdAt;

    public static OutboundListDto from(DocumentEntity entity) {
        return OutboundListDto.builder()
                .documentId(entity.getDocumentId())
                .documentNo(entity.getDocumentNo())
                .type(entity.getType().name())
                .tenantId(entity.getTenantEntity().getTenantId())       // (ED-61 추가)
                .tenantName(entity.getTenantEntity().getTenantName())   // (ED-61 추가)
                .source(entity.getSource().name())                      // (ED-61 추가)
                .partnerName(entity.getPartnerEntity().getPartnerName())
                .expectedAt(entity.getExpectedAt())
                .completedAt(entity.getCompletedAt())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
