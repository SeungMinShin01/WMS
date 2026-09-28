
/*
package src.main.java.com.wms.model.dto.location;

import lombok.Getter;

@Getter @Setter @ToString @Builder @NoArgsConstructor @AllArgsConstructor
// 롬복 라이브러리를 이용해 코드를 간편하게 해줌
public class LocationDto {
    private Integer locationId;
    private String locationCode;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // 필드 선언을 해야지 this.locaionCode를 쓸 수 있다
public LocationEntity toEntity(){ 
        return LocationEntity.builder()
            .locationCode( this.locationCode )
            .isActive( this.isActive )
            .build();
    }
// toEntity() (DTO ➔ Entity)
// DTO 라는 택배 상자에 담긴 데이터를 DB에 넣을 설계도로 변환
// DTO에서 변환을 하기 때문에 static이 없다.
// DB에서 역으로 DTO로 가기 때문에 DB 컬럼 다 작성하는 것임.
public static LocationDto from( LocationEntity entity ){ 
        return LocationDto.builder()
            .locationId( entity.getLocationId() )
            .locationCode( entity.getLocationCode() )
            .isActive( entity.getIsActive() )
            .createdAt( entity.getCreatedAt() )
            .updatedAt( entity.getUpdatedAt() )
            .build();
    }
}
// from() (Entity ➔ DTO) DB에서 꺼내온 날것의 데이터(Entity)를 택배 상자 DTO로 변환
// static이 있는 이유는 Entity에서 변환을 하기 때문임.
// get 다음 대문자
*/