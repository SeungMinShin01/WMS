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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboundDetailDto {
    private Integer documentId;
    private String documentNo;
    private String partnerName;
    private LocalDateTime expectedAt;
    private LocalDateTime completedAt;
    private String status;
    private List<OutboundItemDto> items;

    public static OutboundDetailDto from(DocumentEntity Entity) {
        return OutboundDetailDto.builder()
                .documentId(Entity.getDocumentId())
                .documentNo(Entity.getDocumentNo())
                .partnerName(Entity.getPartnerEntity().getPartnerName())
                .expectedAt(Entity.getExpectedAt())
                .completedAt(Entity.getCompletedAt())
                .status(Entity.getStatus().name())
                .items(new ArrayList<>())
                .build();
    }

}
