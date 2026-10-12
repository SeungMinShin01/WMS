package com.wms.model.entity;

import java.time.LocalDateTime;

import org.apache.commons.lang3.builder.ToStringExclude;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table (name = "picking_list")
@Data @NoArgsConstructor @AllArgsConstructor @Builder 
public class PickingListEntity extends BaseTime{
    @Id 
    @GeneratedValue ( strategy = GenerationType.IDENTITY)
    private Integer pickingListId;
    private String pickingNo; // 피킹 번호
    @JoinColumn(name = "document_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToStringExclude 
    private UserEntity workEntity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false )
   private PickingStatus status; // CREATED / ASSIGNED / PICKING / DONE / CANCELED

    private LocalDateTime assignedAt; // 배정한시각
    private LocalDateTime startedAt; // 첫스캔시각
    private LocalDateTime completedAt; // 다잡은시각



}
