package com.wms.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditAction {

    // 입고
    INBOUND_CREATE("입고 문서 등록"),
    INBOUND_ITEM_ADD("입고 품목 추가"),
    INSPECT("검수 등록"),
    CARRY("적재"),

    // 출고
    ALLOCATE("할당"),
    PICK("피킹 확인"),
    SHIP("출고 확정"),
    OUTBOUND_CANCEL("출고 취소"),

    // 기준정보
    LOCATION_CREATE("로케이션 등록"),
    LOCATION_UPDATE("로케이션 수정"),
    PARTNER_CREATE("거래처 등록"),
    PARTNER_UPDATE("거래처 수정"),
    PRODUCT_CREATE("품목 등록"),
    PRODUCT_UPDATE("품목 수정");

    private final String label; // 한글 이름 (CSV detail 칸에 사용)

}