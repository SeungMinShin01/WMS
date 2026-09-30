package com.wms.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundItemDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.DocumentStatus;
import com.wms.model.entity.StockEntity;
import com.wms.model.repository.DocumentItemDetailRepository;
import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.repository.StockRepository;
import jakarta.persistence.EntityNotFoundException;
import com.wms.model.entity.DocumentType;
@Service
@Transactional
public class OutboundService {
    // ED-12
    @Autowired
    private DocumentRepository documentRepository;
    // ED-17
    @Autowired
    private DocumentItemRepository documentItemRepository;
    // ED-20
    @Autowired
    private StockRepository stockRepository;
    // ED-20
    @Autowired
    private DocumentItemDetailRepository documentItemDetailRepository;

    // ED-12 출고 문서 목록 조회
    public List<OutboundListDto> getOutboundList() {
        List<DocumentEntity> documentEntities = documentRepository
                .findByTypeOrderByExpectedAtAsc(DocumentType.OUTBOUND);
        List<OutboundListDto> documentDtos = documentEntities.stream().map((entity) -> {
            return OutboundListDto.from(entity);
        }).toList();
        return documentDtos;
    }

    // ED-17 출고 문서 상세 조회
    public OutboundDetailDto getOutboundDetail(Integer documentId) {
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElseThrow(() -> 
            new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
                if (documentEntity.getType() != DocumentType.OUTBOUND) {
                    throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
                }
        OutboundDetailDto outboundDetailDto = OutboundDetailDto.from(documentEntity);

        List<DocumentItemEntity> documentItemEntities = documentItemRepository.findAll();
        documentItemEntities.forEach((documentItemEntity) -> {

            if (documentItemEntity.getDocumentEntity().getDocumentId().equals(documentId)) {
                OutboundItemDto outboundItemDto = OutboundItemDto.from(documentItemEntity);
                outboundDetailDto.getItems().add(outboundItemDto);
            }
        });
        return outboundDetailDto;
    }

    // ED-20 출고확정 (문서 단위) : PICKING 문서의 피킹리스트 전체를 한 번에 출고
    // 문서 상태가 "출고됨 표시" 역할을 한다 → 이미 SHIPPED 면 409 (더블클릭·새로고침 후 재클릭 방어)
    public String confirmShipment(Integer documentId) {

        // 1. 문서 검사 : 없음 404 / 출고 문서 아님 400
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }

        // 2. 상태 검사 : 이미 출고됨 409 / 피킹 중이 아님 409
        if (documentEntity.getStatus() == DocumentStatus.SHIPPED) {
            throw new IllegalStateException("이미 출고된 문서입니다");
        }
        if (documentEntity.getStatus() != DocumentStatus.PICKING) {
            throw new IllegalStateException("피킹 중인 문서만 출고확정할 수 있습니다. 현재 상태: " + documentEntity.getStatus());
        }

        // 3. 이 문서의 할당 내역(detail)마다 재고 차감 : 실물 qty 와 선점 allocatedQty 를 같이 줄임
        for (DocumentItemDetailEntity detail : documentItemDetailRepository.findAll()) {
            if (!detail.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)) continue; // 다른 문서 건너뜀
            StockEntity stockEntity = detail.getStockEntity();
            stockEntity.setQty(stockEntity.getQty() - detail.getQty());
            stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() - detail.getQty());
            stockRepository.save(stockEntity);
        }

        // 4. 문서 상태 PICKING → SHIPPED + 완료 시각 기록
        documentEntity.moveTo(DocumentStatus.SHIPPED);
        documentEntity.setCompletedAt(LocalDateTime.now());
        documentRepository.save(documentEntity);

        return documentEntity.getStatus().name();
    }
}
