package com.wms.model.dto.inbound;

import com.wms.model.entity.LocationEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Data 
@Builder 
public class LocationOptionDto {
    // 적치 로케이션 선택 목록용
    private Integer locationId;
    private String locationCode;

    public static LocationOptionDto from(LocationEntity entity){
        return LocationOptionDto.builder()
        .locationId(entity.getLocationId())
        .locationCode(entity.getLocationCode())
        .build();
    }
}
