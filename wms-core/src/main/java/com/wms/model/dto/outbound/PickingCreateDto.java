package com.wms.model.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
// ED-75 피킹 리스트 생성 요청
// 본문 : {"documentId": 67}  → 이 출고 문서의 할당 줄로 피킹 리스트를 만든다
@Data @NoArgsConstructor @AllArgsConstructor @Builder 
public class PickingCreateDto {
    private Integer documentId; // 출고 문서 번호
}
