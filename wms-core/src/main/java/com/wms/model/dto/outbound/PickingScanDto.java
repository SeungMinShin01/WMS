package com.wms.model.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ED-77 스캔 요청
// 본문 : {"pickingListItemId": 12, "lotCode": "L-20261001-001", "qty": 3}
//   pickingListItemId : 화면에서 지금 집고 있는 줄
//   lotCode           : 물건에 붙은 QR 을 찍어서 읽은 LOT 코드 (맞는 물건인지 확인용)
//   qty               : 이번에 집은 수량
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PickingScanDto {
    private Integer pickingListItemId;
    private String lotCode;
    private Integer qty;
}