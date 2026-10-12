package com.wms.model.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ED-75 작업자 배정 요청
// 본문 : {"workerId": 3}  → 이 피킹 리스트를 3번 작업자에게 맡긴다
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PickingAssignDto {
    private Integer workerId; // 배정할 작업자 계정 번호 (user_account.user_id)
}