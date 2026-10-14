package com.wms.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;

@Getter
@RequiredArgsConstructor
public enum AuditAction {

    // 입고
    INBOUND_CREATE("입고 문서 등록"),
    INBOUND_ITEM_ADD("입고 품목 추가"),
    INBOUND_UPLOAD("입고 엑셀 일괄 등록"),
    INSPECT("검수 등록"),
    CARRY("적재"),
    INBOUND_CANCEL("입고 취소"),

    // 출고
    OUTBOUND_CREATE("출고 문서 등록"),
    OUTBOUND_ITEM_ADD("출고 품목 추가"),
    OUTBOUND_UPLOAD("출고 엑셀 일괄 등록"),
    ALLOCATE("할당"),
    PICK("피킹 확인"),
    SHIP("출고 확정"),
    OUTBOUND_CANCEL("출고 취소"),
    // qr
    PICKING_CREATE("피킹 리스트 생성"),
    PICKING_ASSIGN("피킹 작업자 배정"),
    PICKING_SCAN("피킹 스캔"),

    // 재고
    STOCK_DISPOSE("재고 폐기"),

    // 기준정보
    LOCATION_CREATE("로케이션 등록"),
    LOCATION_UPDATE("로케이션 수정"),
    PARTNER_CREATE("거래처 등록"),
    PARTNER_UPDATE("거래처 수정"),
    PRODUCT_CREATE("품목 등록"),
    PRODUCT_UPDATE("품목 수정"),

    // 계정
    WORKER_CREATE("작업자 계정 생성"),
    WORKER_ACTIVE("작업자 계정 활성/비활성"),

    AUDIT_DOWNLOAD("감사 로그 다운로드");

    private final String label; // 한글 이름 (CSV detail 칸에 사용)

}