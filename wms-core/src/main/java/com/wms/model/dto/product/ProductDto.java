package com.wms.model.dto.product;

import java.time.LocalDateTime;

import com.wms.model.entity.ProductEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @ToString @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductDto {
    private Integer productId;
    private String productCode;
    private String productName;
    private String spec;
    private String unit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
// 1. DTO -> ENTITY
    public ProductEntity toEntity(){
        return ProductEntity.builder()
                            .productCode( this.productCode )
                            .productName( this.productName )
                            .spec( this.spec )
                            .unit( this.unit )
                            .build();                           
    }
// 2. ENTITY -> DTO
    public static  ProductDto from( ProductEntity entity ){
        return ProductDto.builder()
            .productId( entity.getProductId() ) 
            .productCode( entity.getProductCode() )
            .productName( entity.getProductName() )
            .spec( entity.getSpec() )
            .unit( entity.getUnit() )
            .createdAt( entity.getCreatedAt() )
            .updatedAt( entity.getUpdatedAt() )
            .build();

    }




}
