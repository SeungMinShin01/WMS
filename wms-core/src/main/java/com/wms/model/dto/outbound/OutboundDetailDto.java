package com.wms.model.dto.outbound;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.wms.model.entity.DocumentEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundDetailDto {
    // ED - 17 : 출고 문서 상세 조회
    private Integer documentID;
    private String documentNo;
    private String partnerName;
    private LocalDateTime expectedAt;
    private LocalDateTime completedAt;
    private String status;

    @Builder.Default
    private List<OutboundItemDto> items = new ArrayList<>();

    public static OutboundDetailDto from(DocumentEntity entity) {
        return OutboundDetailDto.builder()
                .documentID(entity.getDocumentId())
                .documentNo(entity.getDocumentNo())
                .partnerName(entity.getPartnerEntity().getPartnerName())
                .expectedAt(entity.getExpectedAt())
                .completedAt(entity.getCompletedAt())
                .status(entity.getStatus())
                .build();
    }
}
