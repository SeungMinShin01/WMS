package com.wms.model.dto.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PutawayDto {
    // ED-16 : 적재 1건
    private Integer detailId;
    private Integer locationId;
}