package com.wms.model.dto;

import java.time.LocalDateTime;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.PartnerEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder 
public class OutboundListDto {
    private Integer documentId;
    private String documentNo; // IN-20261001-001
    private String type; // INBOUND / OUTBOUND
    private String partnerName;
    private LocalDateTime expectedAt;
    private LocalDateTime completedAt;
    private String status;

    public static OutboundListDto from(DocumentEntity entity) {
        return OutboundListDto.builder()
                .documentId(entity.getDocumentId())
                .documentNo(entity.getDocumentNo())
                .type(entity.getType())
                .partnerName(entity.getPartnerEntity().getPartnerName())
                .expectedAt(entity.getExpectedAt())
                .completedAt(entity.getCompletedAt())
                .status(entity.getStatus())
                .build();
    }
}
