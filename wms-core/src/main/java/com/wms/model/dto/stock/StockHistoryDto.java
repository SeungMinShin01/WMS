package com.wms.model.dto.stock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentStatus;
import com.wms.model.entity.DocumentType;
import com.wms.model.entity.ProductEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Data 
@Builder 
public class StockHistoryDto {
    // 입출고 이력 1줄 - document_item_detail
    private String historyKey;  
    private Integer detailId;
    private Integer stockId;    // 변경 후 수량 계산용
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime occurredAt;    // 발생일시
    private String txType;              // 입고,선점,출고
    private String productCode;
    private String productName;
    private String locationCode;
    private String lotCode;
    private LocalDate expiryDate;
    private Integer qtyChange;          // 실물 증감
    private Integer allocatedChange;    // 선점 증감
    private Integer afterQty;           // 변경 후 실물
    private Integer afterAllocated;     // 변경 후 선점
    private String documentNo;          // 근거 문서
    private String remark;              // 사유
    private String handler;             // 처리자
    
    // detail 1건 -> 이력 1~2줄
    public static List<StockHistoryDto> from(DocumentItemDetailEntity detail){
        DocumentEntity doc = detail.getDocumentItemEntity().getDocumentEntity();
        int qty = detail.getQty();
        List<StockHistoryDto> rows = new ArrayList<>();

        if(doc.getType()==DocumentType.INBOUND){
            // 입고 적재: 실물 + (위치가 채워진 = 적재된 시각)
            rows.add(of(detail, doc, "INBOUND", detail.getUpdatedAt(), qty, 0));
        }else{
            // 출고: 피킹리스트 생성 때 선점 +
            rows.add(of(detail, doc, "ALLOCATE", detail.getCreatedAt(), 0, qty));
            // 출고 완료면 실물 -, 선점 - 한줄 더
            if(doc.getStatus() == DocumentStatus.SHIPPED)
                rows.add(of(detail, doc, "OUTBOUND", doc.getCompletedAt(), -qty, -qty));
        }
        return rows;
    }

    private static StockHistoryDto of(DocumentItemDetailEntity detail, DocumentEntity doc, String txType, LocalDateTime occurredAt, int qtyChange, int allocatedChange){
        ProductEntity product = detail.getLotEntity().getProductEntity();
        return StockHistoryDto.builder()
                        .historyKey(detail.getDetailId()+"-"+txType)
                        .detailId(detail.getDetailId())
                        .stockId(detail.getStockEntity().getStockId())
                        .occurredAt(occurredAt)
                        .txType(txType)
                        .productCode(product.getProductCode())
                        .productName(product.getProductName())
                        .locationCode(detail.getLocationEntity().getLocationCode())
                        .lotCode(detail.getLotEntity().getLotCode())
                        .expiryDate(detail.getLotEntity().getExpiryDate())
                        .qtyChange(qtyChange)
                        .allocatedChange(allocatedChange)
                        .documentNo(doc.getDocumentNo())
                        .remark(detail.getRemark())
                        .handler("admin")
                        .build();
    }
}
