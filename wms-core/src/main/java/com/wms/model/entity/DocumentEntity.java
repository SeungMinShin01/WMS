package com.wms.model.entity;

import java.time.LocalDateTime;

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
    @Enumerated(EnumType.STRING)
    private DocumentType type;

    @JoinColumn(name = "partner_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private PartnerEntity partnerEntity;

    private LocalDateTime expectedAt;
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.WAITING;

    // 상태를 바꾸는 유일한 문. setStatus 는 직접 호출하지 않는다
    public void moveTo(DocumentStatus next) {
        if (!next.belongsTo(this.type)) {
            throw new IllegalStateException(
                    "문서 " + documentNo + "(" + type + ")는 " + next + " 상태가 될 수 없습니다");
        }
        if (!this.status.canGoTo(next)) {
            throw new IllegalStateException(
                    "문서 " + documentNo + " 상태를 " + status + " → " + next + " 로 바꿀 수 없습니다");
        }
        this.status = next;
    }
}
