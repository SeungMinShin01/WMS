package com.wms.model.dto.outbound;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder 
public class OutboundCreateDto {
    private Integer tenantId;                       // 화주
    private Integer partnerId;                      // 거래처
    private LocalDateTime expextedad;               // 출고 요청일
    private List<OutboundCreateItemDto> items;      // 품목 줄 목록

    
}
