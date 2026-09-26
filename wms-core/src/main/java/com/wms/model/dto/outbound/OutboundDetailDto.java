package com.wms.model.dto.outbound;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.wms.model.entity.DocumentEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder 
public class OutboundDetailDto {
    private Integer documentId;
    private String documentNo;
    private String partnerName;
    private LocalDateTime expectedAt;
    private LocalDateTime completedAt;
    private String status; 
    private List<OutboundDetailItemDto> items;

    public static OutboundDetailDto from(DocumentEntity documentEntity) {
        return OutboundDetailDto.builder()
                .documentId(documentEntity.getDocumentId())
                .documentNo(documentEntity.getDocumentNo())
                .partnerName(documentEntity.getPartnerEntity().getPartnerName())
                .expectedAt(documentEntity.getExpectedAt())
                .completedAt(documentEntity.getCompletedAt())
                .status(documentEntity.getStatus())
                .items(new ArrayList<>())
                .build();
    }

}
