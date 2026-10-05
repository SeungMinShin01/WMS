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
        // 문서 전체를 가져와서 출고 문서만 골라 담는다
        List<DocumentEntity> documentEntities = new ArrayList<>(); // 출고문서를 담을 빈배열
        for (DocumentEntity documentEntity : documentRepository.findAll()) { // 문서를 하나씩 꺼냄
            if (documentEntity.getType() == DocumentType.OUTBOUND) { // Type이 outbound인지 확인
                documentEntities.add(documentEntity); // outbound면 리스트에 추가
            }
        }

        // 출고 예정일 빠른 순으로 정렬 (a 의 예정일이 더 이르면 음수 → a 가 앞)
        // .sort( 비교 규칙 ) : 새 리스트 생성이 아닌 리스트 안에 순서를 변경
        documentEntities.sort((a, b) -> a.getExpectedAt().compareTo(b.getExpectedAt())); // 출고 예정일 빠른 순으로 정렬됨

        List<OutboundListDto> documentDtos = documentEntities.stream().map((entity) -> { // 문서를 한개씩 entity에 넣어서 
            return OutboundListDto.from(entity); // entity->dto로 변환
        }).toList(); // 변환된 dto들을 리스트로 생성
        return documentDtos; 
    }

    // ED-17 출고 문서 상세 조회
    public OutboundDetailDto getOutboundDetail(Integer documentId) {
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElseThrow(() -> 
            new EntityNotFoundException("출고 문서가 없습니다: " + documentId)); // 문서가 없을시 예외
                if (documentEntity.getType() != DocumentType.OUTBOUND) { 
                    throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId); // 출고 문서가 아닐시 예외
                }
        OutboundDetailDto outboundDetailDto = OutboundDetailDto.from(documentEntity); // entity -> dto 변환

        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate();   // 출고 가능 판단 기준일 (출고 예정일)
        List<StockEntity> allStocks = stockRepository.findAll();            // 재고 전체

        List<DocumentItemEntity> documentItemEntities = documentItemRepository.findAll(); // 모든 문서의 품목줄을 가져옴
        for (DocumentItemEntity documentItemEntity : documentItemEntities) {   // 품목 줄 하나씩 꺼내서

            if (documentItemEntity.getDocumentEntity().getDocumentId().equals(documentId)) { // 품목줄에 속한 문서 id와 파라미터로 받은 문서id를 비교 
                OutboundItemDto outboundItemDto = OutboundItemDto.from(documentItemEntity); // 맞으면 품목을 entity -> dto로 변환
                // 할당 수량 · 출고 가능 재고 채우기 (추천 받기 전에 화면에서 재고 부족을 미리 보게)
                // AllocationPlanService가서 현재 할당 수량을 받아옴
                // 추천만 받고 피킹리스트를 만들기 전이면 0
                outboundItemDto.setAllocatedQty(allocationPlanService.allocatedSum(documentItemEntity.getDocumentItemId()));
                // 이 품목 줄에 쓸 수 있는 재고(같은 상품·쓰는 칸·소비기한 충분)의 가용수량 합계를 계산해서 Dto에 넣음
                outboundItemDto.setAvailableQty(allocationPlanService.shippableQty(documentItemEntity, shipDate, allStocks));
                // 문서 상세 객체에 완성된 품목추가
                outboundDetailDto.getItems().add(outboundItemDto);
            }
        }
        return outboundDetailDto;
    }

    // ED-20 출고확정 (문서 단위) : PICKING 문서의 피킹리스트 전체를 한 번에 출고
    // 문서 상태가 "출고됨 표시" 역할을 한다 → 이미 SHIPPED 면 409 (더블클릭·새로고침 후 재클릭 방어)
    public String confirmShipment(Integer documentId) {

        // 1. 문서 검사 : 없음 404 / 출고 문서 아님 400
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId)); // 문서가 없을시 예외
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId); // 출고 문서가 아닐시
        }

        // 2. 상태 검사 : 이미 출고됨 409 / 피킹 중이 아님 409
        if (documentEntity.getStatus() == DocumentStatus.SHIPPED) {
            throw new IllegalStateException("이미 출고된 문서입니다");  // 출고된 문서일시
        }
        if (documentEntity.getStatus() != DocumentStatus.PICKING) { // 피킹 중이 아닐시
            throw new IllegalStateException("피킹 중인 문서만 출고확정할 수 있습니다. 현재 상태: " + documentEntity.getStatus());
        }

        // 3. 이 문서의 할당 내역(detail)마다 재고 차감 : 실물 qty 와 선점 allocatedQty 를 같이 줄임
        for (DocumentItemDetailEntity detail : documentItemDetailRepository.findAll()) {
            // 할당 기록(detail) → 품목 줄 → 문서 순으로 올라가 파라미터로 받은 문서id랑 비교해서 해당 문서의 할당 기록을 가져옴
            if (!detail.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)) continue; // 다른 문서는 건너뜀
            // 재고 행을 entity객체로 꺼냄 안에는 실물, 선점 , lot , 로케이션 , 칸 등
            // 할당기록에는 그 재고의 현재 수량을 볼 수 없기 때문에 재고 행을 꺼내와야함
            StockEntity stockEntity = detail.getStockEntity();
            // 재고 숫자가 이상하면 DB 제약(CHECK) 오류(500) 대신 409 로 원인을 알려줌
            if (stockEntity.getAllocatedQty() < detail.getQty() || stockEntity.getQty() < detail.getQty()) {
                // 선점 수량이 출고 수량보다 적거나 재고 수량이 출고수량보다 적거나
                throw new IllegalStateException("재고 수량이 맞지 않습니다: " + detail.getLocationEntity().getLocationCode()
                        + " · 출고 " + detail.getQty() + " · 실물 " + stockEntity.getQty() + " · 선점 " + stockEntity.getAllocatedQty());
            }
            // 실물 수량 = 지금 실물 수량 − 이번에 출고한 수량
            stockEntity.setQty(stockEntity.getQty() - detail.getQty());
            // 선점 수량 = 지금 선점 수량 − 이번에 출고한 수량
            stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() - detail.getQty());
            // DB에 반영
            stockRepository.save(stockEntity);
        }

        

        // 4. 문서 상태 PICKING → SHIPPED + 완료 시각 기록

        // 문서 상태를 출고 완료로 바꿈
        // DocumentStatus.canGoTo 이동 규칙
        documentEntity.moveTo(DocumentStatus.SHIPPED); 
        documentEntity.setCompletedAt(LocalDateTime.now()); // 완료시작에 지금 시각을 넣음
        documentRepository.save(documentEntity); // 바뀐 상태랑 완료 시각을 DB에 반영

        // 문서의 상태(SHIPPED).name() enum값을 글자로 변경 "SHIPPED"
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