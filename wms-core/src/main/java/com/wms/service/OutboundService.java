package com.wms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundItemDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.model.entity.DetailStatus;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.DocumentStatus;
import com.wms.model.entity.DocumentType;
import com.wms.model.entity.StockEntity;
import com.wms.model.repository.DocumentItemDetailRepository;
import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.repository.StockRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional
public class OutboundService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentItemRepository documentItemRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DocumentItemDetailRepository documentItemDetailRepository;

    @Autowired
    private AllocationPlanService allocationPlanService;

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

        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate();   // 출고 가능 판단 기준일 (출고 예정일)
        List<StockEntity> allStocks = stockRepository.findAll();            // 재고 전체 (품목마다 재사용)

        List<DocumentItemEntity> documentItemEntities = documentItemRepository.findAll();
        for (DocumentItemEntity documentItemEntity : documentItemEntities) {   // 품목 줄 하나씩 꺼내서
            if (documentItemEntity.getDocumentEntity().getDocumentId().equals(documentId)) {   // 이 문서의 줄만
                OutboundItemDto outboundItemDto = OutboundItemDto.from(documentItemEntity);
                // 할당 수량 · 출고 가능 재고 채우기 (추천 받기 전에 화면에서 재고 부족을 미리 보게)
                outboundItemDto.setAllocatedQty(allocationPlanService.allocatedSum(documentItemEntity.getDocumentItemId()));
                outboundItemDto.setAvailableQty(allocationPlanService.shippableQty(documentItemEntity, shipDate, allStocks));
                outboundDetailDto.getItems().add(outboundItemDto);
            }
        }
        return outboundDetailDto;
    }

    // ED-20 출고확정 (문서 단위) : PICKING 문서의 피킹리스트 전체를 한 번에 출고
    // ED-52 모든 피킹 줄이(PICKED) 이어야 출고확정 가능, 확정하면 줄도 출고됨(SHIPPED)
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

        // 3. ED-52 이 문서의 피킹 줄(할당 실적)만 먼저 모은다
        //    할당 실적 전체 → 줄 → 품목 줄 → 문서 순으로 올라가 문서번호가 같은 것만 담는다
        List<DocumentItemDetailEntity> details = new ArrayList<>();
        for (DocumentItemDetailEntity detail : documentItemDetailRepository.findAll()) {
            if (detail.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)) {
                details.add(detail);
            }
        }

        // 4. ED-52 피킹 검사 : 한 줄이라도 집음(PICKED) 이 아니면 409
        //    재고를 빼기 전에 모든 줄을 먼저 검사한다 (몇 줄만 빼다가 중간에 멈추지 않게)
        for (DocumentItemDetailEntity detail : details) {
            if (detail.getStatus() != DetailStatus.PICKED) {
                throw new IllegalStateException("피킹이 끝나지 않은 줄이 있습니다");
            }
        }

        // 5. 줄마다 재고 차감 (실물 qty 와 선점 allocatedQty 를 같이 줄임) + 줄 상태 변경
        for (DocumentItemDetailEntity detail : details) {
            StockEntity stockEntity = detail.getStockEntity();   // 이 줄이 가리키는 재고 행 1개

            // 재고 숫자가 이상하면 DB 제약(CHECK) 오류(500) 대신 409 로 원인을 알려줌
            if (stockEntity.getAllocatedQty() < detail.getQty() || stockEntity.getQty() < detail.getQty()) {
                throw new IllegalStateException("재고 수량이 맞지 않습니다: " + detail.getLocationEntity().getLocationCode()
                        + " · 출고 " + detail.getQty() + " · 실물 " + stockEntity.getQty() + " · 선점 " + stockEntity.getAllocatedQty());
            }
            stockEntity.setQty(stockEntity.getQty() - detail.getQty());                     // 실물 차감
            stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() - detail.getQty());   // 선점 해제
            stockRepository.save(stockEntity);

            // ED-52 줄 상태 집음(PICKED) → 출고됨(SHIPPED)
            detail.moveTo(DetailStatus.SHIPPED);
            documentItemDetailRepository.save(detail);
        }

        // 6. 문서 상태 PICKING → SHIPPED + 완료 시각 기록
        documentEntity.moveTo(DocumentStatus.SHIPPED);
        documentEntity.setCompletedAt(LocalDateTime.now());
        documentRepository.save(documentEntity);

        return documentEntity.getStatus().name();
    }

    // 주문 취소 : 접수(WAITING) · 할당(ALLOCATED) 문서만 취소 가능 (피킹중 이후는 409)
    // 이 문서가 잡아둔 재고 선점을 할당 수량만큼 풀고, 할당 실적 줄을 삭제한 뒤 문서를 CANCELED 로 바꾼다
    public String cancelOutbound(Integer documentId) {

        // 1. 문서 검사 : 없음 404 / 출고 문서 아님 400
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }

        // 2. 상태 검사 : 접수·할당이 아니면 409 (피킹중이면 이미 집은 줄이 있음)
        if (documentEntity.getStatus() != DocumentStatus.WAITING && documentEntity.getStatus() != DocumentStatus.ALLOCATED) {
            throw new IllegalStateException("접수·할당 상태에서만 취소할 수 있습니다. 현재 상태: " + documentEntity.getStatus());
        }

        // 3. 이 문서의 할당 실적마다 선점 해제 + 줄 삭제
        for (DocumentItemDetailEntity detail : documentItemDetailRepository.findAll()) {
            if (!detail.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)) continue; // 다른 문서 건너뜀
            StockEntity stockEntity = detail.getStockEntity();
            stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() - detail.getQty());   // 이 줄이 잡아둔 만큼만 선점 해제
            stockRepository.save(stockEntity);
            documentItemDetailRepository.delete(detail);                                    // 할당 실적 줄 삭제
        }

        // 4. 문서 상태 → CANCELED
        documentEntity.moveTo(DocumentStatus.CANCELED);
        documentRepository.save(documentEntity);

        return documentEntity.getStatus().name();
    }
}