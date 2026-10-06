package com.wms.model.entity;

// 문서 상태. 어디서 어디로 갈 수 있는지를 여기 한 곳에서만 정한다.
// ※ 전이 표는 초안 — 회의 6번(PICKING 사용 여부, 취소 범위) 확정 후 switch 만 수정
public enum DocumentStatus {
    WAITING, // 등록 (입고·출고 공통 시작)
    INSPECTED, // 검수완료 (입고)
    COMPLETED, // 입고완료 (입고 끝)
    ALLOCATED, // 할당완료 (출고)
    PICKING, // 피킹중 (출고)
    SHIPPED, // 출고완료 (출고 끝)
    CANCELED; // 취소 (공통 끝)

    // 이 상태가 type 문서에서 쓸 수 있는 상태인가
    public boolean belongsTo(DocumentType type) {
        switch (this) {
            case INSPECTED:
            case COMPLETED:
                return type == DocumentType.INBOUND;

            case ALLOCATED:
            case PICKING:
            case SHIPPED:
                return type == DocumentType.OUTBOUND;

            default:
                return true; // WAITING, CANCELED 는 공통
        }
    }

    // 이 상태에서 next 로 갈 수 있는가
    public boolean canGoTo(DocumentStatus next) {
        switch (this) {
            case WAITING:
                return next == INSPECTED || next == ALLOCATED || next == CANCELED;
            case INSPECTED:
                return next == COMPLETED || next == CANCELED;
            case ALLOCATED:
                return next == PICKING || next == CANCELED;
            case PICKING:
                return next == SHIPPED;
            default:
                return false; // COMPLETED, SHIPPED, CANCELED 는 끝
        }
    }

    // 끝난 상태인가 (더 이상 작업 불가)
    public boolean isFinal() {
        return this == COMPLETED || this == SHIPPED || this == CANCELED;
    }
}