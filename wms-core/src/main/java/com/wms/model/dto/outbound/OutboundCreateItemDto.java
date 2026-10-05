package com.wms.model.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder 
public class OutboundCreateItemDto {
    private Integer productId; // 품목
    private Integer qty; // 주문 수량
}
