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
    // ED-61 tenantId 가 있으면 그 화주의 문서만, 없으면(null) 전체
    public List<OutboundListDto> getOutboundList(Integer tenantId) {
        // 1. 문서 전체를 가져와서 출고 문서만 고른다 (화주를 골랐으면 그 화주 문서만)
        List<DocumentEntity> documentEntities = new ArrayList<>();
        for (DocumentEntity documentEntity : documentRepository.findAll()) {
            // 출고 문서가 아니면 건너뜀
            if (documentEntity.getType() != DocumentType.OUTBOUND) {
                continue;
            }
            // 화주를 골랐는데 이 문서의 화주와 다르면 건너뜀
            if (tenantId != null && !documentEntity.getTenantEntity().getTenantId().equals(tenantId)) {
                continue;
            }
            documentEntities.add(documentEntity);
        }

        // 2. 출고 예정일 빠른 순으로 정렬 (a 의 예정일이 더 이르면 음수 → a 가 앞)
        documentEntities.sort((a, b) -> a.getExpectedAt().compareTo(b.getExpectedAt()));

        // 3. 화면용 목록 객체로 바꿔서 담는다
        List<OutboundListDto> documentDtos = new ArrayList<>();
        for (DocumentEntity documentEntity : documentEntities) {
            documentDtos.add(OutboundListDto.from(documentEntity));
        }
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
    // [ED-64 조건부 UPDATE] 확인(PICKING 인가) + 변경(SHIPPED) 을 UPDATE 한 문장으로 맨 앞에서 처리
    public String confirmShipment(Integer documentId) {

        // 0. 조건부 UPDATE : 문서가 PICKING 일 때만 SHIPPED 로 (바뀐 행 수 0 = 이미 누가 했거나 상태가 안 맞음)
        //    clearAutomatically 때문에 엔티티 조회는 반드시 이 뒤에 (함정 2)
        int changed = documentRepository.changeStatus(documentId, DocumentStatus.PICKING, DocumentStatus.SHIPPED);

        // 1. 문서 검사 : 없음 404 / 출고 문서 아님 400 (UPDATE 뒤에 조회 → 최신 상태가 보임)
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }

        // 2. 상태를 못 바꿨으면 409 (이미 출고됨 / 피킹 중이 아님)
        if (changed == 0) {
            if (documentEntity.getStatus() == DocumentStatus.SHIPPED) {
                throw new IllegalStateException("이미 출고된 문서입니다");
            }
            throw new IllegalStateException("피킹 중인 문서만 출고확정할 수 있습니다. 현재 상태: " + documentEntity.getStatus());
        }

        // 3. 이 문서의 피킹 줄 모으기
        List<DocumentItemDetailEntity> details = new ArrayList<>();
        for (DocumentItemDetailEntity detail : documentItemDetailRepository.findAll()) {
            if (detail.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)) {
                details.add(detail);
            }
        }

        // 4. 피킹 검사 : 한 줄이라도 집음(PICKED) 이 아니면 409 → 예외라서 0번의 상태 변경도 롤백됨
        for (DocumentItemDetailEntity detail : details) {
            if (detail.getStatus() != DetailStatus.PICKED) {
                throw new IllegalStateException("피킹이 끝나지 않은 줄이 있습니다");
            }
        }

        // 5. 줄마다 재고 차감 : 조건부 UPDATE (실물·선점이 출고 수량 이상일 때만)
        //    기존 setQty / setAllocatedQty / stockRepository.save 는 지움 (함정 1 : 남기면 옛 값으로 다시 덮어씀)
        for (DocumentItemDetailEntity detail : details) {
            int rows = stockRepository.ship(detail.getStockEntity().getStockId(), detail.getQty());
            if (rows == 0) {
                throw new IllegalStateException("재고 수량이 맞지 않습니다: " + detail.getLocationEntity().getLocationCode()
                        + " · 출고 " + detail.getQty());
            }
            // 줄 상태 집음(PICKED) → 출고됨(SHIPPED)
            detail.moveTo(DetailStatus.SHIPPED);
            documentItemDetailRepository.save(detail);
        }

        // 6. 완료 시각 기록 (상태는 0번에서 이미 SHIPPED 로 바뀜 → moveTo 안 함)
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