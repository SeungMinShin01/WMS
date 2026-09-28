
package src.main.java.com.wms.model.dto.product;


@Getter @Setter @Tostring @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductDto {
    private Integer productId;
    private String productCode;
    private String productName;
    private String spec;
    private String unit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
public ProductEntity toEntity() {
        return ProductEntity.builder()
            .productCode( this.productCode )
            .productName( this.productName )
            .spec( this.spec )
            .unit( this.unit )
            .build();
        }
public static ProductDto from( ProductEntity entity ){
        return ProductDto.builder()
            .productId( entity.getProductId() )
            .productCode( entity.getProductCode() )
            .productName( entity.getproductName() )
            .spec( entity.getSpec() )
            .unit( entity.getUnit() )
            .createdAt( entity.getCreatedAt() )
            .updatedAt( entity.getupdateAt() )
            .build();
    }
}
