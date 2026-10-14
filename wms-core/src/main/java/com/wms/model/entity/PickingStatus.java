package com.wms.model.entity;

public enum PickingStatus {
    CREATED, // CREATED  : 관리자가 리스트를 만듦 (작업자 미배정)
    ASSIGNED, // ASSIGNED : 관리자가 작업자에게 배정함 (재배정 가능)
    PICKING, // PICKING  : 작업자가 첫 스캔을 함 (이때부터 재배정 불가)
    DONE, // DONE : 리스트의 모든 품목을 다 집음 → 관리자가 출고확정
    CANCELED // CANCELED : 출고 문서가 취소돼서 리스트도 취소
}
