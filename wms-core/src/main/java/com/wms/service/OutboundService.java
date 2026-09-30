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
import com.wms.model.dto.outbound.AllocationPreviewDto;
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

        // ═══════════════════════════════════════════════════════════
    // ED-18 출고 할당
    //   수동 allocate       : 사람이 재고를 직접 골라 품목 줄 1개에 할당 (예외용)
    //   previewAllocate     : 자동 할당 미리보기 (추천만, 저장 안 함)
    //   autoAllocate        : 자동 할당 확정 (계산 결과를 저장) → 문서 ALLOCATED
    //   startPicking        : 피킹 시작 ALLOCATED → PICKING
    // 예외는 GlobalExceptionHandler 가 응답으로 바꿈 : 404 없음 / 400 잘못된 요청 / 409 상태·재고 충돌
    // ═══════════════════════════════════════════════════════════

    // ED-18 할당 1건 (수동 · 예외용) : 사람이 재고를 직접 골라 품목 줄 1개에 할당
    // 재고를 고르는 "순서"는 사람이 정하지만, 상태·출고 가능 조건·수량 검사는 자동 할당과 같다
    public Integer allocate(AllocationDto allocationDto) {

        // 1. 수량 검사 → 400
        if (allocationDto.getQty() == null || allocationDto.getQty() <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다");
        }

        // 2. 품목 줄 · 재고 조회 → 없으면 404
        DocumentItemEntity documentItemEntity = documentItemRepository.findById(allocationDto.getDocumentItemId())
                .orElseThrow(() -> new EntityNotFoundException("품목 줄이 없습니다: " + allocationDto.getDocumentItemId()));
        StockEntity stockEntity = stockRepository.findById(allocationDto.getStockId())
                .orElseThrow(() -> new EntityNotFoundException("재고가 없습니다: " + allocationDto.getStockId()));

        // 3. 문서 검사 (품목 줄 → 문서)
        DocumentEntity documentEntity = documentItemEntity.getDocumentEntity();
        // 출고 문서가 아님 → 400
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentEntity.getDocumentId());
        }
        // 대기 상태가 아님 (이미 할당·출고·취소) → 409
        if (documentEntity.getStatus() != DocumentStatus.WAITING) {
            throw new IllegalStateException("대기 상태에서만 할당할 수 있습니다. 현재 상태: "
                    + documentEntity.getStatus());
        }

        // 4. 주문한 품목의 재고가 맞는지 (재고 → 로트 → 품목) → 400
        Integer productId = documentItemEntity.getProductEntity().getProductId();
        if (!stockEntity.getLotEntity().getProductEntity().getProductId().equals(productId)) {
            throw new IllegalArgumentException("주문 품목과 다른 상품의 재고입니다");
        }

        // 5. 출고 가능 조건 (자동 할당과 동일) → 409
        if (!stockEntity.getLocationEntity().getIsActive()) {
            throw new IllegalStateException("사용하지 않는 칸의 재고입니다");
        }
        LocalDate expiry = stockEntity.getLotEntity().getExpiryDate();
        if (expiry == null) {
            throw new IllegalStateException("소비기한이 없는 재고입니다");
        }
        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate();
        long remainDays = ChronoUnit.DAYS.between(shipDate, expiry);
        int minShipDays = documentItemEntity.getProductEntity().getMinShipDays();
        if (remainDays < minShipDays) {
            throw new IllegalStateException("소비기한 잔여일이 부족합니다 · 잔여 " + remainDays + "일 · 필요 " + minShipDays + "일");
        }

        // 6. 그 재고의 가용수량(실물 − 선점)을 넘는지 → 409
        int available = stockEntity.getQty() - stockEntity.getAllocatedQty();
        if (allocationDto.getQty() > available) {
            throw new IllegalStateException("가용 수량을 넘습니다 · 요청 " + allocationDto.getQty() + " · 가용 " + available);
        }

        // 7. 이 품목 줄의 "남은 수량"을 넘는지 → 409 (같은 줄을 또 눌러 중복 할당되는 것도 여기서 막힘)
        int remain = documentItemEntity.getExpectedQty() - allocatedSum(documentItemEntity.getDocumentItemId());
        if (allocationDto.getQty() > remain) {
            throw new IllegalStateException("이 품목의 남은 수량을 넘습니다 · 요청 " + allocationDto.getQty() + " · 남은 " + remain);
        }

        // 8. 저장 : 할당 내역 + 선점수량 증가
        DocumentItemDetailEntity savedDetail = documentItemDetailRepository
                .save(allocationDto.toEntity(documentItemEntity, stockEntity));
        stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() + allocationDto.getQty());
        stockRepository.save(stockEntity);

        // 9. 문서의 모든 품목 줄이 다 채워졌으면 상태 이동 (WAITING → ALLOCATED)
        boolean allFilled = true;
        for (DocumentItemEntity item : documentItemRepository.findAll()) {
            if (!item.getDocumentEntity().getDocumentId().equals(documentEntity.getDocumentId())) continue; // 다른 문서 줄은 건너뜀
            if (allocatedSum(item.getDocumentItemId()) < item.getExpectedQty()) {
                allFilled = false;   // 덜 채워진 줄이 하나라도 있으면 아직 대기 상태 유지
                break;
            }
        }
        if (allFilled) {
            documentEntity.moveTo(DocumentStatus.ALLOCATED);
            documentRepository.save(documentEntity);
        }

        return savedDetail.getDetailId();
    }

    // 품목 줄 1개에 지금까지 할당된 수량 합계 (document_item_detail 중 이 줄의 것)
    private int allocatedSum(Integer documentItemId) {
        int sum = 0;
        for (DocumentItemDetailEntity d : documentItemDetailRepository.findAll()) {
            if (d.getDocumentItemEntity().getDocumentItemId().equals(documentItemId)) {
                sum += d.getQty();
            }
        }
        return sum;
    }

    // 자동 할당 계산 결과 1줄 : 어느 품목 줄(item)이 어느 재고 행(stock)에서 몇 개(qty)
    private record PlanRow(DocumentItemEntity item, StockEntity stock, int qty) {}

    // 재고 행의 "계획 반영 가용수량" = 실물 − 선점 − 이번 계획에서 이미 쓴 수량
    private int availableOf(StockEntity s, Map<Integer, Integer> planned) {
        return s.getQty() - s.getAllocatedQty() - planned.getOrDefault(s.getStockId(), 0);
    }

    // 문서 검사 : 없는 문서 404 / 출고 문서 아님 400 / 대기 상태 아님 409
    private DocumentEntity checkAllocatable(Integer documentId) {
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }
        if (documentEntity.getStatus() != DocumentStatus.WAITING) {
            throw new IllegalStateException("대기 상태에서만 할당할 수 있습니다. 현재 상태: "
                    + documentEntity.getStatus());
        }
        return documentEntity;
    }

    // 할당 계산 (DB에 아무것도 저장하지 않음) : 품목 줄마다 v3 규칙으로 재고 행과 수량을 정해 목록으로 돌려줌
    // [LOT 정하기] 1 소비기한 빠른 순 → 2-1 LOT 가용 합계 많은 순 → 2-2 LOT 첫 적재일 빠른 순 → 2-3 lotId
    // [칸 정하기]  3-1 칸 가용수량 많은 순 → 3-2 칸 적재일 빠른 순 → 3-3 stockId
    private List<PlanRow> buildPlan(DocumentEntity documentEntity) {
        Integer documentId = documentEntity.getDocumentId();
        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate();   // 잔여일 계산 기준일

        // 이 문서의 품목 줄 모으기
        List<DocumentItemEntity> items = new ArrayList<>();
        for (DocumentItemEntity item : documentItemRepository.findAll()) {
            if (item.getDocumentEntity().getDocumentId().equals(documentId)) {
                items.add(item);
            }
        }
        // 품목이 없는 문서 → 409
        if (items.isEmpty()) {
            throw new IllegalStateException("품목이 없는 문서입니다: " + documentId);
        }
        // 수동으로 이미 일부 할당된 문서 → 409 (같은 품목을 또 할당하는 것 방지)
        for (DocumentItemEntity item : items) {
            if (allocatedSum(item.getDocumentItemId()) > 0) {
                throw new IllegalStateException("이미 일부 할당된 문서입니다. 수동 할당으로 마저 채우세요");
            }
        }

        List<StockEntity> allStocks = stockRepository.findAll();   // 재고 전체 (품목마다 재사용)
        Map<Integer, Integer> planned = new HashMap<>();           // stockId → 이번 계획에서 이미 쓴 수량
        List<PlanRow> plan = new ArrayList<>();                    // 결과

        // 품목 줄마다 계획
        for (DocumentItemEntity item : items) {
            Integer productId = item.getProductEntity().getProductId();   // 이 줄의 품목
            int minShipDays = item.getProductEntity().getMinShipDays();   // 출고 허용 잔여일
            int need = item.getExpectedQty();                             // 채워야 할 수량

            // 3-1. 자격 조건으로 후보 거르기
            List<StockEntity> candidates = new ArrayList<>();
            for (StockEntity s : allStocks) {
                // 주문한 품목의 재고만 (재고 → 로트 → 품목)
                if (!s.getLotEntity().getProductEntity().getProductId().equals(productId)) continue;
                // 가용수량(실물 − 선점 − 계획에서 이미 쓴 것) > 0
                if (availableOf(s, planned) <= 0) continue;
                // 운영 중인 칸만
                if (!s.getLocationEntity().getIsActive()) continue;
                // 소비기한 없는 재고는 제외
                LocalDate expiry = s.getLotEntity().getExpiryDate();
                if (expiry == null) continue;
                // 소비기한 − 출고예정일 ≥ 출고 허용 잔여일
                if (ChronoUnit.DAYS.between(shipDate, expiry) < minShipDays) continue;

                candidates.add(s);   // 모든 조건 통과 → 후보
            }

            // 3-2. 가용 합계가 필요량보다 적으면 409 (저장 전에 계산 단계에서 바로 실패)
            int totalAvailable = 0;
            for (StockEntity s : candidates) {
                totalAvailable += availableOf(s, planned);
            }
            if (totalAvailable < need) {
                throw new IllegalStateException(item.getProductEntity().getProductName()
                        + " 가용 부족 · 필요 " + need + " · 가용 " + totalAvailable);
            }

            // 3-3. LOT 단위 값 계산 (lotTotal : LOT별 가용 합계 / lotFirstIn : LOT별 첫 적재일)
            Map<Integer, Integer> lotTotal = new HashMap<>();
            Map<Integer, LocalDateTime> lotFirstIn = new HashMap<>();
            for (StockEntity s : candidates) {
                Integer lotId = s.getLotEntity().getLotId();
                lotTotal.put(lotId, lotTotal.getOrDefault(lotId, 0) + availableOf(s, planned));
                LocalDateTime in = s.getCreatedAt();
                if (!lotFirstIn.containsKey(lotId) || in.isBefore(lotFirstIn.get(lotId))) {
                    lotFirstIn.put(lotId, in);
                }
            }

            // 3-4. 정렬 (LOT 기준 전부 → 칸 기준 전부)
            candidates.sort(Comparator
                    .comparing((StockEntity s) -> s.getLotEntity().getExpiryDate())                            // 1   소비기한 빠른 순
                    .thenComparing(s -> lotTotal.get(s.getLotEntity().getLotId()), Comparator.reverseOrder())  // 2-1 LOT 가용 합계 많은 순
                    .thenComparing(s -> lotFirstIn.get(s.getLotEntity().getLotId()))                           // 2-2 LOT 첫 적재일 빠른 순
                    .thenComparing(s -> s.getLotEntity().getLotId())                                           // 2-3 lotId 작은 순
                    .thenComparing(s -> availableOf(s, planned), Comparator.reverseOrder())                    // 3-1 칸 가용 많은 순
                    .thenComparing(s -> s.getCreatedAt())                                                      // 3-2 칸 적재일 빠른 순
                    .thenComparing(s -> s.getStockId()));                                                      // 3-3 stockId 작은 순

            // 3-5. 정렬 순서대로 필요한 만큼 계획에 담기 (저장 X, planned 에만 기록)
            for (StockEntity s : candidates) {
                if (need == 0) break;                                      // 다 채웠으면 종료
                int take = Math.min(need, availableOf(s, planned));        // 꺼낼 수량
                plan.add(new PlanRow(item, s, take));                      // 계획에 추가
                planned.merge(s.getStockId(), take, Integer::sum);         // 같은 재고를 다음 품목이 또 쓸 때 반영
                need -= take;                                              // 남은 필요량 감소
            }
        }
        return plan;
    }

    // ED-18 확장 : 자동 할당 미리보기 (추천만, 저장 안 함)
    // readOnly : 실수로 엔티티가 바뀌어도 DB에 반영되지 않게 하는 안전장치
    @Transactional(readOnly = true)
    public List<AllocationPreviewDto> previewAllocate(Integer documentId) {
        DocumentEntity documentEntity = checkAllocatable(documentId);
        List<PlanRow> plan = buildPlan(documentEntity);

        List<AllocationPreviewDto> result = new ArrayList<>();
        for (PlanRow row : plan) {
            result.add(AllocationPreviewDto.from(row.item(), row.stock(), row.qty()));
        }
        result.sort(Comparator.comparing(AllocationPreviewDto::getLocationCode));   // 피킹 순서(로케이션 코드순)
        return result;
    }

    // ED-18 확장 : 자동 할당 확정 (계산한 결과를 저장) → 문서 ALLOCATED
    public List<PickingListDto> autoAllocate(Integer documentId) {
        DocumentEntity documentEntity = checkAllocatable(documentId);
        List<PlanRow> plan = buildPlan(documentEntity);   // 확정 때도 서버에서 다시 계산 (재고가 바뀌었을 수 있으므로)

        for (PlanRow row : plan) {
            AllocationDto allocationDto = AllocationDto.builder()
                    .documentItemId(row.item().getDocumentItemId())   // 어느 품목 줄의 결과인지
                    .stockId(row.stock().getStockId())                // 어느 재고 행에서
                    .qty(row.qty())                                   // 몇 개
                    .build();
            documentItemDetailRepository.save(allocationDto.toEntity(row.item(), row.stock()));   // detail 저장

            row.stock().setAllocatedQty(row.stock().getAllocatedQty() + row.qty());               // 선점 증가
            stockRepository.save(row.stock());
        }

        documentEntity.moveTo(DocumentStatus.ALLOCATED);   // WAITING → ALLOCATED (PICKING 은 "피킹 시작"에서)
        documentRepository.save(documentEntity);

        return getPickingList(documentId);   // 결과로 피킹리스트(로케이션 코드순) 반환
    }

    // 피킹 시작 : ALLOCATED → PICKING (다른 상태에서 부르면 moveTo 가 409)
    public String startPicking(Integer documentId) {
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }
        documentEntity.moveTo(DocumentStatus.PICKING);
        documentRepository.save(documentEntity);
        return documentEntity.getStatus().name();
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
