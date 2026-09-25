package com.wms.model.dto.inbound;

import java.time.LocalDateTime;

import com.wms.model.entity.DocumentEntity;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundListDto {
    private Integer documentId;
    private String documentNo;
    private String partnerName;
    private LocalDateTime expectedAt;
    private LocalDateTime completedAt;

    public static InboundListDto from(DocumentEntity entity) {
        return InboundListDto.builder()
                .documentId(entity.getDocumentId())
                .documentNo(entity.getDocumentNo())
                .partnerName(entity.getPartnerEntity().getPartnerName())
                .expectedAt(entity.getExpectedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
