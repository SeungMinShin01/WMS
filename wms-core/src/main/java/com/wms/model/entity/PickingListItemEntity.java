package com.wms.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

// ED-75 피킹 리스트 품목 줄 : 할당 줄(document_item_detail) 1개 = 품목 줄 1개
// 작업자가 스캔할 때마다 pickedQty 가 쌓이고, 할당 수량(detail.qty)만큼 차면 그 줄은 다 집은 것
@Entity
@Table(name = "picking_list_item")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PickingListItemEntity extends BaseTime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer pickingListItemId;

    // 어느 피킹 리스트의 줄인지
    @JoinColumn(name = "picking_list_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private PickingListEntity pickingListEntity;

    // 어느 할당 줄인지 (칸·LOT·재고·할당 수량은 여기서 꺼냄, DB 에 UNIQUE)
    @JoinColumn(name = "detail_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private DocumentItemDetailEntity detailEntity;

    private Integer seq;              // 집는 순서 (칸 코드 순 1, 2, 3 ...)
    private Integer pickedQty;        // 지금까지 집은 수량 (생성할 때 0 으로 넣음)
    private LocalDateTime pickedAt;   // 다 집은 시각
}