package com.wms.model.dto.outbound;

import java.time.LocalDateTime;

import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.PickingListItemEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ED-76 피킹 리스트 상세의 품목 줄 1개 (작업자 화면 한 줄)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickingItemDto {
    private Integer pickingListItemId;
    private Integer seq;              // 집는 순서
    private Integer detailId;         // 할당 줄 번호
    private String locationCode;      // 어느 칸에 가서
    private String productCode;
    private String productName;       // 어떤 상품을
    private String lotCode;           // 어떤 LOT 으로
    private Integer qty;              // 몇 개 집어야 하는지 (할당 수량)
    private Integer pickedQty;        // 지금까지 집은 수량
    private Boolean done;             // 다 집었는지 (pickedQty 가 qty 만큼 찼는지)
    private LocalDateTime pickedAt;

    public static PickingItemDto from(PickingListItemEntity entity) {
        // 칸·상품·LOT·할당 수량은 모두 할당 줄(detail)에 있으므로 먼저 꺼내 둠
        DocumentItemDetailEntity detail = entity.getDetailEntity();

        return PickingItemDto.builder()
                .pickingListItemId(entity.getPickingListItemId())
                .seq(entity.getSeq())
                .detailId(detail.getDetailId())
                .locationCode(detail.getLocationEntity().getLocationCode())
                .productCode(detail.getDocumentItemEntity().getProductEntity().getProductCode())
                .productName(detail.getDocumentItemEntity().getProductEntity().getProductName())
                .lotCode(detail.getLotEntity().getLotCode())
                .qty(detail.getQty())
                .pickedQty(entity.getPickedQty())
                .done(entity.getPickedQty() >= detail.getQty())
                .pickedAt(entity.getPickedAt())
                .build();
    }
}