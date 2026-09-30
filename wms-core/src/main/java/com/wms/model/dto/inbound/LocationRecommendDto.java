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
    private Integer freeQty;    // 여유 수량(null = 제한없음)
    private Boolean fits;       // 수량이 전부 들어가는지
    private Integer priority;   // 1. 같은 LOT 2. 잔량 칸 3. 빈 칸
}
