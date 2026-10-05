package com.wms.model.entity;

// 처리 결과(document_item_detail) 한 줄의 상태. "이 줄에 무엇이 끝났는가" 를 과거형으로 적는다.
//   문서 상태(DocumentStatus)  = 여러 줄을 모은 진행 상황 (WAITING, PICKING 처럼 진행형이 있음)
//   처리 결과 상태(이 enum)     = 한 칸에서 한 번 처리한 결과 (항상 끝난 일)
// detail 은 검수·할당할 때 생기므로 "대기" 가 없다. 취소된 출고 줄은 삭제한다(선점 해제와 같이).
public enum DetailStatus {
    INSPECTED, // 검수됨 (입고) — 칸 미정
    STORED, // 적재됨 (입고) — 칸·재고 연결됨
    ALLOCATED, // 할당됨 (출고) — 재고 선점함
    PICKED, // 집음 (출고) — 칸에서 꺼냄
    SHIPPED; // 출고됨 (출고) — 재고에서 빠짐

    // 이 상태가 type 문서의 줄에서 쓸 수 있는 상태인가
    public boolean belongsTo(DocumentType type) {
        switch (this) {
            case INSPECTED:
            case STORED:
                return type == DocumentType.INBOUND;
            default:
                return type == DocumentType.OUTBOUND;
        }
    }

    // 이 상태에서 next 로 갈 수 있는가
    public boolean canGoTo(DetailStatus next) {
        switch (this) {
            case INSPECTED:
                return next == STORED;
            case ALLOCATED:
                return next == PICKED;
            case PICKED:
                return next == SHIPPED;
            default:
                return false; // STORED, SHIPPED 는 끝
        }
    }
}