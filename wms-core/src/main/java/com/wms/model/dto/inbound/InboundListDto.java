package com.wms.model.dto.inbound;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wms.model.entity.DocumentEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class InboundListDto {
    // ED-10 입고 문서 목록 조회
    private Integer documentId;
    private String documentNo;
    private String tenantName;  // 화주명
    private String partnerName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime expectedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime completedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;
    private String status;
    private Integer itemCount;
    private Integer totalExpectedQty;   // 들어올 예정인 수량
    private Integer inspectedQty;   // 실제 입고수량 합계 

    public static InboundListDto from(DocumentEntity entity, int itemCount, int totalExpectedQty) {
        return InboundListDto.builder()
                .documentId(entity.getDocumentId())
                .documentNo(entity.getDocumentNo())
                .tenantName(entity.getTenantEntity().getTenantName())
                .partnerName(entity.getPartnerEntity().getPartnerName())
                .expectedAt(entity.getExpectedAt())
                .completedAt(entity.getCompletedAt())
                .createdAt(entity.getCreatedAt())
                .status(entity.getStatus().name())
                .itemCount(itemCount)
                .totalExpectedQty(totalExpectedQty)
                .build();
    }
}
