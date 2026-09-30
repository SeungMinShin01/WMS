package com.wms.model.dto.inbound;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.wms.model.entity.DocumentItemEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundItemDto {
    // ED - 13 : 입고 문서 상세 조회
    private Integer documentItemId;
    private String productCode;
    private String productName;
    private String lotCode;
    private LocalDate expiryDate;
    private Integer expectedQty;
    private Long remainingDays; // 소비기한까지 남은 일수(오늘 기준, 지나면 음수)

    public static InboundItemDto from(DocumentItemEntity entity) {
        return InboundItemDto.builder()
                .documentItemId(entity.getDocumentItemId())
                .productCode(entity.getProductEntity().getProductCode())
                .productName(entity.getProductEntity().getProductName())
                .lotCode(entity.getLotEntity().getLotCode())
                .expiryDate(entity.getLotEntity().getExpiryDate())
                .expectedQty(entity.getExpectedQty())
                // ChronoUnit.DAYS.between(A, B): A부터B까지 며칠인지 계산
                .remainingDays(ChronoUnit.DAYS.between(LocalDate.now(),entity.getLotEntity().getExpiryDate()))
                .build();
    }
}
