package com.wms.model.dto.location;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.wms.model.entity.LocationEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @ToString @Builder @NoArgsConstructor @AllArgsConstructor 
public class LocationDto {
    private Integer locationId;
    private String locationCode;
    private Boolean isActive;
    private Integer capacity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 1. DTO->ENTITY 등록시
    public LocationEntity toEntity(){
        return LocationEntity.builder()
                             .locationCode( this.locationCode )
                             .isActive( this.isActive )
                             .capacity( this.capacity )
                             .build();
    }

    // 2. ENTITY->DTO: 주로 조회시
    public static LocationDto from( LocationEntity entity ){
        return LocationDto.builder()
            .locationId( entity.getLocationId() )
            .locationCode( entity.getLocationCode() )
            .isActive( entity.getIsActive() )
            .capacity( entity.getCapacity() )
            .createdAt( entity.getCreatedAt() )
            .updatedAt( entity.getUpdatedAt() )
            .build();
    }
}


