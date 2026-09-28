package com.wms.model.dto.inbound;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.wms.model.entity.DocumentEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class InboundDetailDto {
    // ED - 13 : 입고 문서 상세 조회
    private Integer documentId;
    private String documentNo;
    private String partnerName;
    private LocalDateTime expectedAt;
    private LocalDateTime completedAt;
    private String status;

    @Builder.Default
    private List<InboundItemDto> items = new ArrayList<>();

    public static InboundDetailDto from(DocumentEntity entity) {
        return InboundDetailDto.builder()
                .documentId(entity.getDocumentId())
                .documentNo(entity.getDocumentNo())
                .partnerName(entity.getPartnerEntity().getPartnerName())
                .expectedAt(entity.getExpectedAt())
                .completedAt(entity.getCompletedAt())
                .status(entity.getStatus())
                .build();
    }
}
