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

// 재고 폐기 기록 (V8) — 소비기한 지난 LOT 재고를 칸 단위로 전량 폐기한 기록 1줄
@Entity 
@Table (name="stock_disposal")
@NoArgsConstructor 
@AllArgsConstructor 
@Data 
@Builder 
public class StockDisposalEntity {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer disposalId;

    // 폐기한 재고 줄
    @JoinColumn(name = "stock_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private StockEntity stockEntity;

    private Integer qty;    // 폐기 수량
    private String reason;  // 사유

    // 처리자(관리자)
    @JoinColumn(name = "user_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private UserEntity userEntity;

    private LocalDateTime disposedAt;
}
