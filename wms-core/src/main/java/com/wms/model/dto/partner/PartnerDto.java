
package src.main.java.com.wms.model.dto.partner;

import java.time.LocalDateTime;

import com.wms.model.entity.PartnerEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @ToString @Builder @NoArgsConstructor @AllArgsConstructor
public class PartnerDto {
    private Integer partnerId;
    private String partnerCode;
    private String partnerName;
    private String partnerType;
    private String contact;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public PartnerEntity toEntity(){
        return PartnerEntity.builder()
            .partnerCode( this.partnerCode )
            .partnerName( this.partnerName )
            .partnerType( this.partnerType )
            .Contact( this.contact )
            .build();
            }
public static PartnerDto from( PartnerEntity entity ){
            return PartnerDto.builder()
                .partnerId( entity.getPartnerId() )
                .partnerCode( entity.partnerCode()  )
                .partnerName( entity.partnerName()  )
                .partnerType( entity.partnerType()  )
                .Contact( entity.Contact() )
                .createdAt( entity.getCreatedAt() )
                .updatedAt( entity.getUpdatedAt() )
                .build();
    }
}
