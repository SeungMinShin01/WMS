package com.wms.model.dto.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor @AllArgsConstructor @Data @Builder 
public class LocationRecommendDto {
    private Integer locationId;
    private String locationCode;
    private String reason;  // 추천 사유
    private Integer currentQty; // 현재 그 칸에 적치된 수량
}
