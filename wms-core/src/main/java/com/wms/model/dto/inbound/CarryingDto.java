package com.wms.model.dto.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Data @Builder 
public class CarryingDto {
    private Integer detailId;
    private Integer locationId;
    private Boolean mixLot; // 혼용적재 모드(null/false = 기본)
    private Boolean force;  // 경고를 확인하고 그래도 적재 (true면 정책 경고 무시)
}
