package com.wms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.outbound.AllocationPreviewDto;
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

// ═══════════════════════════════════════════════════════════
// ED-18 할당 계획 (검증 + 추천 계산 + 미리보기)
//   DB 에 아무것도 저장하지 않는다. "어느 재고에서 몇 개 꺼내면 좋을지" 계산만 한다.
//   PickingListService 가 검증 메서드(checkAllocatable, checkShippable 등)를 가져다 쓴다.
// 예외는 GlobalExceptionHandler 가 응답으로 바꿈 : 404 없음 / 400 잘못된 요청 / 409 상태·재고 충돌
// ═══════════════════════════════════════════════════════════
@Service
public class AllocationPlanService {

    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemRepository documentItemRepository;
    @Autowired private DocumentItemDetailRepository documentItemDetailRepository;
    @Autowired private StockRepository stockRepository;

    // 계산 결과 1줄 : 어느 품목 줄(item)을 어느 재고 행(stock)에서 몇 개(qty) 꺼낼지
    private record PlanRow(DocumentItemEntity item, StockEntity stock, int qty) {}

    // ─────────────────────────────────────────────
    // 1. 미리보기 (컨트롤러가 부르는 메서드)
    // ─────────────────────────────────────────────

    // 선택한 품목 줄들에 대해 추천 결과를 돌려줌 (저장 안 함)
    // documentItemIds 가 비어 있으면 문서의 전체 품목 줄을 대상으로 함
    // readOnly : 실수로 엔티티 값이 바뀌어도 DB 에 반영되지 않게 하는 안전장치
    @Transactional(readOnly = true)
    public List<AllocationPreviewDto> previewAllocate(Integer documentId, List<Integer> documentItemIds) {
        DocumentEntity documentEntity = checkAllocatable(documentId);               // 문서 검사 (404/400/409)
        List<DocumentItemEntity> items = selectItems(documentId, documentItemIds);  // 선택 품목 검사 (400/409)
        List<PlanRow> plan = buildPlan(documentEntity, items);                      // 추천 계산 (재고 부족이면 409)

        List<AllocationPreviewDto> result = new ArrayList<>();
        for (PlanRow row : plan) {
            result.add(AllocationPreviewDto.from(row.item(), row.stock(), row.qty()));
        }
        result.sort(Comparator.comparing(AllocationPreviewDto::getLocationCode));   // 피킹 동선 순서(로케이션 코드순)
        return result;
    }

    // ─────────────────────────────────────────────
    // 2. 검증 메서드 (PickingListService 도 같이 씀)
    // ─────────────────────────────────────────────

    // 문서 검사 : 없는 문서 404 / 출고 문서 아님 400 / 대기 상태 아님 409
    // 대기 상태 검사가 "더블클릭 방어" 역할도 함 (이미 PICKING 으로 넘어간 문서는 다시 못 함)
    public DocumentEntity checkAllocatable(Integer documentId) {
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }
        if (documentEntity.getStatus() != DocumentStatus.WAITING) {
            throw new IllegalStateException("대기 상태에서만 할당할 수 있습니다. 현재 상태: " + documentEntity.getStatus());
        }
        return documentEntity;
    }

    // 이 문서의 품목 줄 전체 (document_item 중 document_id 가 같은 것)
    public List<DocumentItemEntity> itemsOf(Integer documentId) {
        List<DocumentItemEntity> items = new ArrayList<>();
        for (DocumentItemEntity item : documentItemRepository.findAll()) {
            if (item.getDocumentEntity().getDocumentId().equals(documentId)) {
                items.add(item);
            }
        }
        return items;
    }

    // 품목 줄 1개에 지금까지 할당된 수량 합계 (document_item_detail 중 이 줄의 것)
    public int allocatedSum(Integer documentItemId) {
        int sum = 0;
        for (DocumentItemDetailEntity d : documentItemDetailRepository.findAll()) {
            if (d.getDocumentItemEntity().getDocumentItemId().equals(documentItemId)) {
                sum += d.getQty();
            }
        }
        return sum;
    }

    // 재고 1행이 이 품목 줄에 출고 가능한지 검사
    // 다른 상품 400 / 사용 안 하는 칸 409 / 소비기한 없음 409 / 잔여일 부족 409
    // FEFO 순서(더 빠른 소비기한이 있는데 늦은 걸 골랐는지)는 검사하지 않음 → 사용자 수정 허용 (시연용)
    public void checkShippable(DocumentItemEntity item, StockEntity stock, LocalDate shipDate) {
        Integer productId = item.getProductEntity().getProductId();
        if (!stock.getLotEntity().getProductEntity().getProductId().equals(productId)) {
            throw new IllegalArgumentException("주문 품목과 다른 상품의 재고입니다 · 재고 " + stock.getStockId());
        }
        if (!stock.getLocationEntity().getIsActive()) {
            throw new IllegalStateException("사용하지 않는 칸의 재고입니다 · 재고 " + stock.getStockId());
        }
        LocalDate expiry = stock.getLotEntity().getExpiryDate();
        if (expiry == null) {
            throw new IllegalStateException("소비기한이 없는 재고입니다 · 재고 " + stock.getStockId());
        }
        long remainDays = ChronoUnit.DAYS.between(shipDate, expiry);
        int minShipDays = item.getProductEntity().getMinShipDays();
        if (remainDays < minShipDays) {
            throw new IllegalStateException("소비기한 잔여일이 부족합니다 · 재고 " + stock.getStockId()
                    + " · 잔여 " + remainDays + "일 · 필요 " + minShipDays + "일");
        }
    }

    // ─────────────────────────────────────────────
    // 3. 내부 계산
    // ─────────────────────────────────────────────

    // 사용자가 체크한 품목 줄만 골라냄
    // 선택 없음 → 전체 / 이 문서에 없는 id 400 / 이미 할당된 줄 409
    private List<DocumentItemEntity> selectItems(Integer documentId, List<Integer> documentItemIds) {
        List<DocumentItemEntity> all = itemsOf(documentId);
        if (all.isEmpty()) {
            throw new IllegalStateException("품목이 없는 문서입니다: " + documentId);
        }

        List<DocumentItemEntity> selected = new ArrayList<>();
        if (documentItemIds == null || documentItemIds.isEmpty()) {
            selected.addAll(all);                                   // 선택 안 했으면 전체
        } else {
            for (Integer id : documentItemIds.stream().distinct().toList()) {   // 같은 id 두 번 보내도 한 번만
                DocumentItemEntity found = null;
                for (DocumentItemEntity item : all) {
                    if (item.getDocumentItemId().equals(id)) {
                        found = item;
                        break;
                    }
                }
                if (found == null) {
                    throw new IllegalArgumentException("이 문서의 품목 줄이 아닙니다: " + id);
                }
                selected.add(found);
            }
        }

        for (DocumentItemEntity item : selected) {
            if (allocatedSum(item.getDocumentItemId()) > 0) {
                throw new IllegalStateException("이미 할당된 품목입니다: " + item.getProductEntity().getProductName());
            }
        }
        return selected;
    }

    // 재고 행의 "계획 반영 가용수량" = 실물 − 선점 − 이번 계획에서 이미 쓴 수량
    private int availableOf(StockEntity s, Map<Integer, Integer> planned) {
        return s.getQty() - s.getAllocatedQty() - planned.getOrDefault(s.getStockId(), 0);
    }

    // 추천 계산 (DB 저장 X) : 품목 줄마다 v3 규칙으로 재고 행과 수량을 정해 목록으로 돌려줌
    // [LOT 정하기] 1 소비기한 빠른 순 → 2-1 LOT 가용 합계 많은 순 → 2-2 LOT 첫 적재일 빠른 순 → 2-3 lotId
    // [칸 정하기]  3-1 칸 가용수량 많은 순 → 3-2 칸 적재일 빠른 순 → 3-3 stockId
    private List<PlanRow> buildPlan(DocumentEntity documentEntity, List<DocumentItemEntity> items) {
        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate();   // 잔여일 계산 기준일 (출고 예정일)

        List<StockEntity> allStocks = stockRepository.findAll();   // 재고 전체 (품목마다 재사용)
        Map<Integer, Integer> planned = new HashMap<>();           // stockId → 이번 계획에서 이미 쓴 수량
        List<PlanRow> plan = new ArrayList<>();                    // 결과

        for (DocumentItemEntity item : items) {
            Integer productId = item.getProductEntity().getProductId();   // 이 줄의 품목
            int minShipDays = item.getProductEntity().getMinShipDays();   // 출고 허용 잔여일
            int need = item.getExpectedQty();                             // 채워야 할 수량

            // (1) 자격 조건으로 후보 거르기
            List<StockEntity> candidates = new ArrayList<>();
            for (StockEntity s : allStocks) {
                if (!s.getLotEntity().getProductEntity().getProductId().equals(productId)) continue; // 다른 상품
                if (availableOf(s, planned) <= 0) continue;                                           // 꺼낼 게 없음
                if (!s.getLocationEntity().getIsActive()) continue;                                   // 안 쓰는 칸
                LocalDate expiry = s.getLotEntity().getExpiryDate();
                if (expiry == null) continue;                                                         // 소비기한 없음
                if (ChronoUnit.DAYS.between(shipDate, expiry) < minShipDays) continue;                // 잔여일 부족
                candidates.add(s);                                                                    // 모두 통과 → 후보
            }

            // (2) 후보 가용 합계가 필요량보다 적으면 409
            int totalAvailable = 0;
            for (StockEntity s : candidates) {
                totalAvailable += availableOf(s, planned);
            }
            if (totalAvailable < need) {
                throw new IllegalStateException(item.getProductEntity().getProductName()
                        + " 가용 부족 · 필요 " + need + " · 가용 " + totalAvailable);
            }

            // (3) LOT 단위 값 (lotTotal : LOT별 가용 합계 / lotFirstIn : LOT별 첫 적재일)
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

            // (4) 정렬 (LOT 기준 전부 → 칸 기준 전부)
            candidates.sort(Comparator
                    .comparing((StockEntity s) -> s.getLotEntity().getExpiryDate())                            // 1   소비기한 빠른 순
                    .thenComparing(s -> lotTotal.get(s.getLotEntity().getLotId()), Comparator.reverseOrder())  // 2-1 LOT 가용 합계 많은 순
                    .thenComparing(s -> lotFirstIn.get(s.getLotEntity().getLotId()))                           // 2-2 LOT 첫 적재일 빠른 순
                    .thenComparing(s -> s.getLotEntity().getLotId())                                           // 2-3 lotId 작은 순
                    .thenComparing(s -> availableOf(s, planned), Comparator.reverseOrder())                    // 3-1 칸 가용 많은 순
                    .thenComparing(s -> s.getCreatedAt())                                                      // 3-2 칸 적재일 빠른 순
                    .thenComparing(s -> s.getStockId()));                                                      // 3-3 stockId 작은 순

            // (5) 정렬 순서대로 필요한 만큼 담기 (저장 X, planned 에만 기록)
            for (StockEntity s : candidates) {
                if (need == 0) break;                                   // 다 채웠으면 종료
                int take = Math.min(need, availableOf(s, planned));     // 꺼낼 수량
                plan.add(new PlanRow(item, s, take));                   // 계획에 추가
                planned.merge(s.getStockId(), take, Integer::sum);      // 다음 품목이 같은 재고를 쓸 때 반영
                need -= take;                                           // 남은 필요량 감소
            }
        }
        return plan;
    }
}