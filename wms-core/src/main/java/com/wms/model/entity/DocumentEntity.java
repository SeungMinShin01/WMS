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

@Entity
@Table(name = "document")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class DocumentEntity extends BaseTime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer documentId;

    private String documentNo; // IN-20261001-001
    private String type; // INBOUND / OUTBOUND

    @JoinColumn(name = "partner_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private PartnerEntity partnerEntity;

    private LocalDateTime expectedAt;
    private LocalDateTime completedAt;

    @Builder.Default
    private String status = "WAITING";
}
