package com.wms.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "picking_list")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PickingListEntity extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer pickingListId;

    private String pickingNo;

    @JoinColumn(name = "document_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private DocumentEntity documentEntity;

    @JoinColumn(name = "worker_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private UserEntity workerEntity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PickingStatus status;

    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}