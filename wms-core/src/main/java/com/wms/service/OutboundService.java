package com.wms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

// 출고 문서 목록·상세 조회 + 출고확정 + 주문 취소
// 메서드 순서 : 목록 조회(ED-12) → 상세 조회(ED-17) → 출고확정(ED-20) → 주문 취소
// 예) 출고확정에서 재고 3줄 중 2줄 차감 후 예외 → 앞의 2줄 차감도 취소됨
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
        // 1. 문서 전체를 가져와서 출고 문서만 고른다
        // findAll : 테이블 전체 행을 엔티티 목록으로 가져옴 (SELECT * FROM document)
        List<DocumentEntity> documentEntities = new ArrayList<>();
        for (DocumentEntity documentEntity : documentRepository.findAll()) {
            // 출고 문서가 아니면 건너뜀
            // enum 비교는 == / != 
            if (documentEntity.getType() != DocumentType.OUTBOUND) {
                continue;
            }
            // 화주를 골랐는데 이 문서의 화주와 다르면 건너뜀
            // tenantId != null 을 먼저 검사 : 화주를 안 골랐으면(null) 뒤는 실행 안 하고 통과 (&& 는 앞이 false 면 뒤를 안 봄)
            // Integer 끼리는 == 대신 equals 로 값 비교 (== 는 객체 주소 비교라 127 넘으면 틀릴 수 있음)
            if (tenantId != null && !documentEntity.getTenantEntity().getTenantId().equals(tenantId)) {
                continue;
            }
            documentEntities.add(documentEntity);
        }

        // 2. 출고 예정일 빠른 순으로 정렬 (a 의 예정일이 더 이르면 음수 → a 가 앞)
        // list.sort((a, b) -> ...) : 두 값을 비교하는 규칙대로 목록을 정렬
        // compareTo : a 가 이르면 음수 / 같으면 0 / 늦으면 양수 → 음수면 a 가 앞에 옴
        documentEntities.sort((a, b) -> a.getExpectedAt().compareTo(b.getExpectedAt()));

        // 3. 화면용 목록 객체로 바꿔서 담는다
        // DTO 변환 : 화면에 필요한 값만 보내고, 연관 엔티티가 줄줄이 따라가는 것을 막음
        List<OutboundListDto> documentDtos = new ArrayList<>();
        for (DocumentEntity documentEntity : documentEntities) {
            documentDtos.add(OutboundListDto.from(documentEntity));
        }
        return documentDtos;
    }

        // ED-17 출고 문서 상세 조회
    public OutboundDetailDto getOutboundDetail(Integer documentId) {
        // 1. 문서 찾기 + 검사 (없으면 404 / 출고 문서 아니면 400)
        // findById : PK 로 한 건 조회 → Optional(있을 수도 없을 수도 있는 상자)로 옴
        // orElseThrow : 상자가 비어 있으면 그 예외를 던짐 → GlobalExceptionHandler 가 404 응답으로 바꿈
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElseThrow(() ->
            new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            // throw : 메서드를 즉시 멈추고 예외를 호출한 쪽으로 던짐 → 400 응답
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }

        // 2. 문서 정보로 상세 DTO 만들기 (위쪽 "출고 정보" 부분)
        OutboundDetailDto outboundDetailDto = OutboundDetailDto.from(documentEntity);

        // 3. 품목 계산에 쓸 값 준비 (출고 예정일 · 상품별 재고)
        // toLocalDate : LocalDateTime(날짜+시간)에서 시간을 떼고 날짜만 남김 (소비기한과 날짜끼리 비교하기 위해)
        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate();   // 출고 가능 판단 기준일 (출고 예정일)

        // 3-1. 재고 전체를 상품별로 나눠 둔다 : 상품id → 그 상품의 재고 목록
        // 품목마다 재고 전체를 넘기지 않고, 그 품목 상품의 재고만 꺼내 넘기기 위해, 재고 전체는 여기서 한 번만 돈다
        Map<Integer, List<StockEntity>> stocksByProduct = new HashMap<>();
        for (StockEntity stockEntity : stockRepository.findAll()) {
            Integer productId = stockEntity.getLotEntity().getProductEntity().getProductId(); // 재고 → LOT → 상품 id
            // 이 상품이 처음 나왔으면 빈 목록을 먼저 만들어 둔다
            if (!stocksByProduct.containsKey(productId)) {
                stocksByProduct.put(productId, new ArrayList<>());
            }
            // 이 상품의 목록을 꺼내서 재고를 추가 (꺼낸 목록이 Map 안의 목록이라 따로 put 안 해도 반영됨)
            stocksByProduct.get(productId).add(stockEntity);
        }

        // 4. 이 문서의 품목 줄만 골라 DTO 로 바꾸고, 할당수량 · 출고가능재고 채워서 상세 DTO 에 담기 (아래쪽 "주문 품목" 표)
        List<DocumentItemEntity> documentItemEntities = documentItemRepository.findAll();
        for (DocumentItemEntity documentItemEntity : documentItemEntities) {   // 품목 줄 하나씩 꺼내서
            if (documentItemEntity.getDocumentEntity().getDocumentId().equals(documentId)) {   // 이 문서의 줄만
                OutboundItemDto outboundItemDto = OutboundItemDto.from(documentItemEntity);

                // 이 품목 상품의 재고만 꺼낸다 (창고에 이 상품 재고가 하나도 없으면 빈 목록)
                Integer productId = documentItemEntity.getProductEntity().getProductId();
                List<StockEntity> productStocks = new ArrayList<>();
                if (stocksByProduct.containsKey(productId)) {
                    productStocks = stocksByProduct.get(productId);
                }

                // 할당 수량 · 출고 가능 재고 채우기 (추천 받기 전에 화면에서 재고 부족을 미리 보게)
                // from 으로 기본 값을 채운 뒤, 계산이 필요한 두 값은 set 으로 따로 넣음
                outboundItemDto.setAllocatedQty(allocationPlanService.allocatedSum(documentItemEntity.getDocumentItemId()));
                outboundItemDto.setAvailableQty(allocationPlanService.shippableQty(documentItemEntity, shipDate, productStocks));
                // getItems() 로 DTO 안의 품목 목록을 꺼내 그 목록에 바로 추가
                outboundDetailDto.getItems().add(outboundItemDto);
            }
        }
        return outboundDetailDto;
    }

    // ED-20 출고확정 (문서 단위) : PICKING 문서의 피킹리스트 전체를 한 번에 출고
    // ED-52 모든 피킹 줄이(PICKED) 이어야 출고확정 가능, 확정하면 줄도 출고됨(SHIPPED)
    // 문서 상태가 "출고됨 표시" 역할을 한다 → 이미 SHIPPED 면 409 (더블클릭·새로고침 후 재클릭 방어)
    public synchronized String confirmShipment(Integer documentId) {

        // 1. 문서 검사 : 없음 404 / 출고 문서 아님 400
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }

        // 2. 상태 검사 : 이미 출고됨 409 / 피킹 중이 아님 409
        // IllegalStateException : 요청 자체는 맞지만 지금 상태에서는 할 수 없을 때 → 409
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
            // moveTo : 엔티티에 만든 상태 변경 메서드 (허용된 이동인지 확인하고 status 값을 바꿈)
            detail.moveTo(DetailStatus.SHIPPED);
            documentItemDetailRepository.save(detail);
        }

        // 6. 문서 상태 PICKING → SHIPPED + 완료 시각 기록
        // moveTo 안에서 canGoTo 로 PICKING → SHIPPED 가 허용된 이동인지 검사, 안 되면 409
        documentEntity.moveTo(DocumentStatus.SHIPPED);
        documentEntity.setCompletedAt(LocalDateTime.now());   // LocalDateTime.now() : 지금 날짜+시간
        documentRepository.save(documentEntity);

        // name() : enum 값을 글자로 바꿈 (DocumentStatus.SHIPPED → "SHIPPED")
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
            // delete : 엔티티에 해당하는 행을 DB 에서 삭제
            documentItemDetailRepository.delete(detail);                                    // 할당 실적 줄 삭제
        }

        // 4. 문서 상태 → CANCELED
        documentEntity.moveTo(DocumentStatus.CANCELED);
        documentRepository.save(documentEntity);

        return documentEntity.getStatus().name();
    }
}