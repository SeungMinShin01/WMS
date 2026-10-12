package com.wms.model.dto.outbound;

import java.time.LocalDateTime;

import com.wms.model.entity.PickingListEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ED-75 피킹 리스트 1건 (머리 정보)
// PickingListDto 와 다름 : PickingListDto 는 할당 줄 1개, 이건 피킹 리스트 전체 1건
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickingDto {
    private Integer pickingListId;
    private String pickingNo;        // PK-20261012-001
    private Integer documentId;
    private String documentNo;       // 어떤 출고 문서의 리스트인지
    private Integer workerId;        // 배정 전 null
    private String workerName;       // 배정 전 null
    private String status;           // CREATED / ASSIGNED / PICKING / DONE / CANCELED
    private Integer itemCount;       // 품목 줄 수
    private LocalDateTime assignedAt;
    private LocalDateTime createdAt;

    public static PickingDto from(PickingListEntity entity, int itemCount) {
        // 배정 전에는 작업자가 없음 → null 인 채로 getUserId() 를 부르면 NullPointerException(500)
        // 그래서 작업자가 있을 때만 꺼냄
        Integer workerId = null;
        String workerName = null;
        if (entity.getWorkerEntity() != null) {
            workerId = entity.getWorkerEntity().getUserId();
            workerName = entity.getWorkerEntity().getUserName();
        }

        return PickingDto.builder()
                .pickingListId(entity.getPickingListId())
                .pickingNo(entity.getPickingNo())
                .documentId(entity.getDocumentEntity().getDocumentId())
                .documentNo(entity.getDocumentEntity().getDocumentNo())
                .workerId(workerId)
                .workerName(workerName)
                .status(entity.getStatus().name())
                .itemCount(itemCount)
                .assignedAt(entity.getAssignedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}