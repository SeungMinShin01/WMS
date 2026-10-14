package com.wms.model.dto.product;

import java.time.LocalDateTime;

import com.wms.model.entity.ProductEntity;
import com.wms.model.entity.TenantEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @ToString @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductDto {
    private Integer tenantId;
    private String tenantCode;
    private String tenantName;

    private Integer productId;
    private String productCode;
    private String productName;
    private String spec;
    private String unit;
    private Integer minShipDays;   // 출고허용 잔여일 (지금 DTO 에 없어서 항상 0 이었다)
    // [추가] 사용 여부. true=활성, false=비활성. 화면과 키 이름이 같다(isActive)
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
// 1. DTO -> ENTITY
    public ProductEntity toEntity(TenantEntity tenantEntity){   // 변경: 괄호 안에 추가
        return ProductEntity.builder()
                            .tenantEntity(tenantEntity)
                            .productCode( this.productCode )
                            .productName( this.productName )
                            .spec( this.spec )
                            .unit( this.unit )
                            // null 을 넣으면 DB NOT NULL 위반 → 안 보냈으면 0
                            .minShipDays(this.minShipDays == null ? 0 : this.minShipDays)
                            // [추가] 안 보냈으면 활성. null 을 그대로 넣으면 기본값(true)이 덮어써져 NOT NULL 위반이 난다
                            .isActive(this.isActive == null ? true : this.isActive)
                            .build();                           
    }

// 2. ENTITY -> DTO
    public static  ProductDto from( ProductEntity entity ){
        return ProductDto.builder()
            .productId( entity.getProductId() )
            .tenantId(entity.getTenantEntity().getTenantId())
            .tenantCode(entity.getTenantEntity().getTenantCode())
            .tenantName(entity.getTenantEntity().getTenantName())
            .productCode( entity.getProductCode() )
            .productName( entity.getProductName() )
            .spec( entity.getSpec() )
            .unit( entity.getUnit() )
            .minShipDays(entity.getMinShipDays())
            // [추가] 목록·상세 응답에 상태를 싣는다
            .isActive(entity.getIsActive())
            .createdAt( entity.getCreatedAt() )
            .updatedAt( entity.getUpdatedAt() )
            .build();
    }
}
