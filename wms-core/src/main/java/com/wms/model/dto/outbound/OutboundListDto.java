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
    // ED - 12 : 출고 문서 목록 조회 응답용
    private Integer documentId;
    private String documentNo;
    private String partnerName;
    private LocalDateTime expectedAt;
    private LocalDateTime completedAt;
    private String status;

    public static OutboundListDto from(DocumentEntity entity) {
        return OutboundListDto.builder()
                .documentId(entity.getDocumentId())
                .documentNo(entity.getDocumentNo())
                .partnerName(entity.getPartnerEntity().getPartnerName())
                .expectedAt(entity.getExpectedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
