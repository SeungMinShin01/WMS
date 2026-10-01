package com.wms.service;

import java.time.LocalDate;
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
    // ED-17 : 할당 수량 · 출고 가능 재고 계산에 사용
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
        documentItemEntities.forEach((documentItemEntity) -> {

            if (documentItemEntity.getDocumentEntity().getDocumentId().equals(documentId)) {
                OutboundItemDto outboundItemDto = OutboundItemDto.from(documentItemEntity);
                // 할당 수량 · 출고 가능 재고 채우기 (추천 받기 전에 화면에서 재고 부족을 미리 보게)
                outboundItemDto.setAllocatedQty(allocationPlanService.allocatedSum(documentItemEntity.getDocumentItemId()));
                outboundItemDto.setAvailableQty(allocationPlanService.shippableQty(documentItemEntity, shipDate, allStocks));
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
            // 재고 숫자가 이상하면 DB 제약(CHECK) 오류(500) 대신 409 로 원인을 알려줌
            if (stockEntity.getAllocatedQty() < detail.getQty() || stockEntity.getQty() < detail.getQty()) {
                throw new IllegalStateException("재고 수량이 맞지 않습니다: " + detail.getLocationEntity().getLocationCode()
                        + " · 출고 " + detail.getQty() + " · 실물 " + stockEntity.getQty() + " · 선점 " + stockEntity.getAllocatedQty());
            }
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

    // 주문 취소 (문서 단위) : 거래처가 주문을 철회했을 때
    // 접수(WAITING)·할당(ALLOCATED) 상태에서만 가능 → 피킹이 시작되면 물건을 이미 꺼냈을 수 있어서 불가
    // 할 일 : 이 문서가 잡은 선점을 풀고 → 피킹 줄 삭제 → 문서 CANCELED
    public String cancelOutbound(Integer documentId) {

        // 1. 문서 조회 : 없으면 404
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        // 출고 문서가 아니면 400
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }

        // 2. 상태 검사
        DocumentStatus status = documentEntity.getStatus();
        // 이미 취소된 문서 → 409 (더블클릭 방어 : 두 번째 요청은 여기서 막힘)
        if (status == DocumentStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 문서입니다");
        }
        // 접수·할당이 아니면 (피킹중·출고완료) → 409
        if (status != DocumentStatus.WAITING && status != DocumentStatus.ALLOCATED) {
            throw new IllegalStateException("접수·할당 상태에서만 취소할 수 있습니다. 현재 상태: " + status);
        }

        // 3. 이 문서의 피킹 줄(할당 내역)마다 선점 풀기 + 줄 삭제
        //    접수 상태면 피킹 줄이 없어서 이 반복은 그냥 지나감
        for (DocumentItemDetailEntity detail : documentItemDetailRepository.findAll()) {
            // 다른 문서의 줄은 건너뜀
            if (!detail.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)) continue;

            StockEntity stockEntity = detail.getStockEntity();   // 이 줄이 선점한 재고 행
            // 선점 수량이 이 줄 수량보다 적으면 데이터가 꼬인 것 → DB 제약 오류(500) 대신 409 로 알려줌
            if (stockEntity.getAllocatedQty() < detail.getQty()) {
                throw new IllegalStateException("재고 선점 수량이 맞지 않습니다: " + detail.getLocationEntity().getLocationCode()
                        + " · 줄 수량 " + detail.getQty() + " · 선점 " + stockEntity.getAllocatedQty());
            }
            // 이 문서가 잡은 만큼만 선점에서 뺌 → 가용(실물 − 선점)이 그만큼 늘어남
            stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() - detail.getQty());
            stockRepository.save(stockEntity);

            // 피킹 줄 삭제 (취소된 문서의 피킹리스트는 필요 없음)
            documentItemDetailRepository.delete(detail);
        }

        // 4. 문서 상태 → CANCELED (WAITING·ALLOCATED → CANCELED 는 canGoTo 에서 이미 허용)
        documentEntity.moveTo(DocumentStatus.CANCELED);
        documentRepository.save(documentEntity);

        return documentEntity.getStatus().name();   // "CANCELED"
    }
}
