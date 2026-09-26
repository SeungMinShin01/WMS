package com.wms.model.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipDto {
    // ED - 20 : 출고확정 요청
    private Integer detailId;

}
