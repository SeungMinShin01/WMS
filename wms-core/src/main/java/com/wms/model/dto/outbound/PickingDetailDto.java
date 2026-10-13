package com.wms.model.dto.outbound;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ED-76 피킹 리스트 상세 = 머리 정보 1건 + 품목 줄 목록
// 응답 예 : { "picking": {pickingNo, 작업자, 상태 ...}, "items": [ {seq 1 ...}, {seq 2 ...} ] }
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PickingDetailDto {
    private PickingDto picking;          // 리스트 머리 정보 (ED-75 에서 만든 DTO 재사용)
    private List<PickingItemDto> items;  // 집는 순서대로
}