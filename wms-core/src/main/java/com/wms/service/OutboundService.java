package com.wms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.outbound.AllocationDto;
import com.wms.model.dto.outbound.ConfirmShipmentDto;
import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundItemDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.model.dto.outbound.PickingListDto;
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
    // ED-17, ED-18
    @Autowired
    private DocumentItemRepository documentItemRepository;
    // ED-18
    @Autowired
    private StockRepository stockRepository;
    // ED-18
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

    // ED-18 할당 1건 (피킹리스트 생성)
    public Integer allocate(AllocationDto allocationDto) {
        DocumentItemEntity documentItemEntity = documentItemRepository.findById(allocationDto.getDocumentItemId())
                .orElse(null);
        StockEntity stockEntity = stockRepository.findById(allocationDto.getStockId()).orElse(null);

        DocumentItemDetailEntity detailEntity = allocationDto.toEntity(documentItemEntity, stockEntity);
        DocumentItemDetailEntity savedDetail = documentItemDetailRepository.save(detailEntity);

        stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() + allocationDto.getQty());
        stockRepository.save(stockEntity);

        return savedDetail.getDetailId();
    }

    // ED-18 확장 : 출고 문서 1건 자동 할당
    // [LOT 정하기] 1 소비기한 빠른 순 → 2-1 LOT 가용 합계 많은 순 → 2-2 LOT 첫 적재일 빠른 순 → 2-3 lotId
    // [칸 정하기]  3-1 칸 가용수량 많은 순 → 3-2 칸 적재일 빠른 순 → 3-3 stockId
    // 예외는 GlobalExceptionHandler 가 응답으로 바꿈 : 404 없음 / 400 잘못된 요청 / 409 상태·재고 충돌
    public List<PickingListDto> autoAllocate(Integer documentId) {

        // 문서 검사
        // 없는 문서 → 404
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));

        // 입고 문서 번호로 요청 → 400
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }

        // 대기 상태가 아님 (이미 할당·출고·취소) → 409 : 더블 클릭, 재요청 방어
        if (documentEntity.getStatus() != DocumentStatus.WAITING) {
            throw new IllegalStateException("대기 상태에서만 할당할 수 있습니다. 현재 상태: "
                    + documentEntity.getStatus());
        }

        // 잔여일 계산 기준일 = 출고예정일 날짜+시각에서 날짜만 꺼냄
        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate();

        // 이 문서의 품목 줄 모으기
        List<DocumentItemEntity> items = new ArrayList<>();
        for (DocumentItemEntity item : documentItemRepository.findAll()) {
            // 품목 줄 → 문서 → 문서번호가 요청 번호와 같으면 담기
            if (item.getDocumentEntity().getDocumentId().equals(documentId)) {
                items.add(item);
            }
        }
        // 품목이 없는 문서 → 409
        if (items.isEmpty()) {
            throw new IllegalStateException("품목이 없는 문서입니다: " + documentId);
        }

        // 전체 재고를 한 번만 조회해서 품목마다 재사용
        List<StockEntity> allStocks = stockRepository.findAll();

        // 품목 줄마다 할당 
        for (DocumentItemEntity item : items) {
            Integer productId = item.getProductEntity().getProductId();   // 이 줄의 품목
            int minShipDays = item.getProductEntity().getMinShipDays();   // 이 품목의 출고 허용 잔여일
            int need = item.getExpectedQty();                             // 채워야 할 수량

            // 3-1.자격 조건으로 후보 거르기
            List<StockEntity> candidates = new ArrayList<>();
            for (StockEntity s : allStocks) {
                // 주문한 품목의 재고만 (재고 → 로트 → 품목)
                if (!s.getLotEntity().getProductEntity().getProductId().equals(productId)) continue;
                // 가용수량(실물 − 선점) > 0
                int available = s.getQty() - s.getAllocatedQty();
                if (available <= 0) continue;
                // 운영 중인 칸만 (막아 둔 칸의 재고는 꺼낼 수 없음)
                if (!s.getLocationEntity().getIsActive()) continue;
                // 소비기한 없는 재고는 제외
                LocalDate expiry = s.getLotEntity().getExpiryDate();
                if (expiry == null) continue;
                // 소비기한 − 출고예정일 ≥ 출고 허용 잔여일
                long remainDays = ChronoUnit.DAYS.between(shipDate, expiry);
                if (remainDays < minShipDays) continue;

                candidates.add(s);   // 모든 조건 통과 → 후보
            }

            // 3-2. 가용 합계로 충분한지 먼저 확인
            int totalAvailable = 0;
            for (StockEntity s : candidates) {
                totalAvailable += s.getQty() - s.getAllocatedQty();
            }
            // 재고 부족 → 409 (앞 품목에서 저장한 것까지 전부 되돌림)
            if (totalAvailable < need) {
                throw new IllegalStateException(item.getProductEntity().getProductName()
                        + " 가용 부족 · 필요 " + need + " · 가용 " + totalAvailable);
            }

            // 3-3. LOT 단위 값 계산 (자격 조건 통과한 후보만 대상)
            //   lotTotal   : LOT별 가용 합계                      → 2-1 기준
            //   lotFirstIn : LOT별 첫 적재일 (가장 이른 created_at) → 2-2 기준
            Map<Integer, Integer> lotTotal = new HashMap<>();
            Map<Integer, LocalDateTime> lotFirstIn = new HashMap<>();
            for (StockEntity s : candidates) {
                Integer lotId = s.getLotEntity().getLotId();          // 이 재고 행의 LOT 번호
                int available = s.getQty() - s.getAllocatedQty();     // 이 행의 가용수량
                LocalDateTime in = s.getCreatedAt();                  // 이 행이 적재된 시각

                // LOT 합계 : 처음 보는 LOT이면 0부터 시작해서 이 행의 가용을 더함
                lotTotal.put(lotId, lotTotal.getOrDefault(lotId, 0) + available);

                // LOT 첫 적재일 : 처음 보는 LOT이거나 더 이른 시각이면 교체
                if (!lotFirstIn.containsKey(lotId) || in.isBefore(lotFirstIn.get(lotId))) {
                    lotFirstIn.put(lotId, in);
                }
            }

            // 3-4. 정렬 — 재고 행 두 개씩 비교, 앞 기준이 같을 때만 다음 기준으로
            //   LOT 기준(1 ~ 2-3)을 칸 기준(3-1 ~ 3-3)보다 모두 앞에 둬서 LOT이 한 덩어리로 묶임
            candidates.sort(Comparator
                // [LOT] 1 : 소비기한 빠른 순 (FEFO)
                .comparing((StockEntity s) -> s.getLotEntity().getExpiryDate())
                // [LOT] 2-1 : LOT 가용 합계 많은 순 (주문을 한 LOT으로 채우기)
                .thenComparing(s -> lotTotal.get(s.getLotEntity().getLotId()), Comparator.reverseOrder())
                // [LOT] 2-2 : 합계 같으면 LOT 첫 적재일 빠른 순 (LOT 선입선출)
                .thenComparing(s -> lotFirstIn.get(s.getLotEntity().getLotId()))
                // [LOT] 2-3 : 그래도 같으면 lotId 작은 순 → 다른 LOT끼리는 여기서 반드시 갈림
                .thenComparing(s -> s.getLotEntity().getLotId())
                // [칸] 3-1 : 같은 LOT 안에서 칸 가용수량 많은 순 (최대한 한 로케이션)
                .thenComparing(s -> s.getQty() - s.getAllocatedQty(), Comparator.reverseOrder())
                // [칸] 3-2 : 가용 같으면 칸 적재일 빠른 순 (칸 선입선출)
                .thenComparing(s -> s.getCreatedAt())
                // [칸] 3-3 : 전부 같으면 stockId 작은 순 (같은 초 적재 보완, 결과 고정)
                .thenComparing(s -> s.getStockId()));

            // 3-5. 정렬 순서대로 필요한 만큼 꺼내기 (재고 행마다 detail 1줄 + 선점 증가)
            for (StockEntity s : candidates) {
                if (need == 0) break;                                  // 다 채웠으면 종료

                int available = s.getQty() - s.getAllocatedQty();      // 이 행의 가용수량
                int take = Math.min(need, available);                  // 꺼낼 수량

                // 기존 AllocationDto.toEntity() 재사용 → 이 행의 LOT·칸·재고가 detail 에 채워짐
                AllocationDto allocationDto = AllocationDto.builder()
                        .documentItemId(item.getDocumentItemId())      // 어느 품목 줄의 결과인지
                        .stockId(s.getStockId())                       // 어느 재고 행에서
                        .qty(take)                                     // 몇 개
                        .build();
                documentItemDetailRepository.save(allocationDto.toEntity(item, s));

                s.setAllocatedQty(s.getAllocatedQty() + take);         // 선점 증가
                stockRepository.save(s);

                need -= take;                                          // 남은 필요량 감소
            }
        }

        // 문서 상태 변경
        documentEntity.moveTo(DocumentStatus.ALLOCATED);                         // 대기 → 할당됨
        documentEntity.moveTo(DocumentStatus.PICKING);                           // ALLOCATED → PICKING (피킹 단계 확정 전까지는 조건 없이 바로 이동)
        documentRepository.save(documentEntity);

        // 결과로 피킹리스트(로케이션 코드순) 반환
        return getPickingList(documentId);
    }

    // ED-19 피킹 리스트 조회
    public List<PickingListDto> getPickingList(Integer documentId){
        if (!documentRepository.existsById(documentId)) {
            throw new EntityNotFoundException("출고 문서가 없습니다: " + documentId);
        }
        List<PickingListDto> pickingListDtos = documentItemDetailRepository.findAll().stream()
                .filter((detail) -> detail.getDocumentItemEntity().getDocumentEntity().getDocumentId()
                        .equals(documentId))
                .sorted((a, b) -> a.getLocationEntity().getLocationCode()
                        .compareTo(b.getLocationEntity().getLocationCode()))
                .map((detail) -> PickingListDto.from(detail))
                .toList();
        return pickingListDtos;
    }

    // ED-20 출고확정
    public Boolean confirmShipment(ConfirmShipmentDto confirmShipmentDto) {
        DocumentItemDetailEntity detailEntity = documentItemDetailRepository.findById(confirmShipmentDto.getDetailId())
        .orElseThrow(() -> new EntityNotFoundException("할당 내역이 없습니다: " + confirmShipmentDto.getDetailId()));

        StockEntity stockEntity = detailEntity.getStockEntity();
        stockEntity.setQty(stockEntity.getQty() - detailEntity.getQty());
        stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() - detailEntity.getQty());
        stockRepository.save(stockEntity);

        return true;
    }
}
