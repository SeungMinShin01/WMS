package com.wms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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


// ED-18 할당 계획 (검증 + 추천 계산 + 미리보기)
//   DB 에 아무것도 저장하지 않는다. "어느 재고에서 몇 개 꺼내면 좋을지" 계산만 한다.
//   PickingListService 가 검증 메서드(checkAllocatable, checkShippable 등)를 가져다 쓴다.
// 예외는 GlobalExceptionHandler 가 응답으로 바꿈 : 404 없음 / 400 잘못된 요청 / 409 상태·재고 충돌
@Service
public class AllocationPlanService {

    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemRepository documentItemRepository;
    @Autowired private DocumentItemDetailRepository documentItemDetailRepository;
    @Autowired private StockRepository stockRepository;

    // 1. 미리보기 (컨트롤러가 부르는 메서드)

    // 선택한 품목 줄들에 대해 추천 결과를 돌려줌 (저장 안 함)
    // documentItemIds 가 비어 있으면 문서의 전체 품목 줄을 대상으로 함
    // readOnly : 실수로 엔티티 값이 바뀌어도 DB 에 반영되지 않게 하는 안전장치
    @Transactional(readOnly = true)
    public List<AllocationPreviewDto> previewAllocate(Integer documentId, List<Integer> documentItemIds) {
        DocumentEntity documentEntity = checkAllocatable(documentId);               // 문서 검사 (404/400/409)
        List<DocumentItemEntity> items = selectItems(documentId, documentItemIds);  // 선택 품목 검사 (400/409)
        
        List<AllocationPreviewDto> result = buildPlan(documentEntity, items);       // 추천 계산 (재고 부족이면 409)

        // 로케이션 코드 순으로 정렬 (피킹 동선 순서) : a 와 b 의 로케이션 코드를 글자 순으로 비교
        result.sort((a, b) -> a.getLocationCode().compareTo(b.getLocationCode()));
        return result;
    }

    // 2. 검증 메서드 (PickingListService 도 같이 씀)

    // 문서 검사 : 없는 문서 404 / 출고 문서 아님 400 / 대기·할당 상태 아님 409
    // WAITING(아직 하나도 할당 안 함), ALLOCATED(일부만 할당함) 에서만 할당 가능
    // 이미 PICKING 으로 넘어간 문서는 다시 못 함 → "더블클릭 방어" 역할도 함
    public DocumentEntity checkAllocatable(Integer documentId) {
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        if (documentEntity.getType() != DocumentType.OUTBOUND) { // enum(클래스값.값이름) 문서 상태로 사용가능
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }
        DocumentStatus status = documentEntity.getStatus();
        if (status != DocumentStatus.WAITING && status != DocumentStatus.ALLOCATED) { // 문서 상태로 사용가능
            throw new IllegalStateException("대기·할당 상태에서만 할당할 수 있습니다. 현재 상태: " + status);
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

    // 이 품목 줄의 "출고 가능 재고" 합계 (주문 품목 화면에 보여줄 값)
    // 추천 계산(buildPlan)과 같은 조건 : 같은 상품 · 운영 중인 칸 · 소비기한 있음 · 잔여일 충분 · 가용 > 0
    public int shippableQty(DocumentItemEntity item, LocalDate shipDate, List<StockEntity> allStocks) {
        int sum = 0;
        for (StockEntity s : allStocks) {
            if (!isShippable(item, s, shipDate)) continue;          // 조건 불통과 재고는 제외
            int available = s.getQty() - s.getAllocatedQty();       // 가용 = 실물 − 선점
            if (available > 0) sum += available;
        }
        return sum;
    }

    // 재고 1행이 이 품목 줄에 출고 가능한지 true/false 로만 판단 (예외 안 던짐)
    private boolean isShippable(DocumentItemEntity item, StockEntity s, LocalDate shipDate) {
        if (!s.getLotEntity().getProductEntity().getProductId().equals(item.getProductEntity().getProductId())) return false; // 다른 상품
        if (!s.getLocationEntity().getIsActive()) return false;                                                             // 안 쓰는 칸
        LocalDate expiry = s.getLotEntity().getExpiryDate();
        if (expiry == null) return false;                                                                                   // 소비기한 없음
        return ChronoUnit.DAYS.between(shipDate, expiry) >= item.getProductEntity().getMinShipDays();                       // 잔여일 충분
    }

    // 재고 1행이 이 품목 줄에 출고 가능한지 검사
    // 다른 상품 400 / 사용 안 하는 칸 409 / 소비기한 없음 409 / 잔여일 부족 409
    // FEFO 순서(더 빠른 소비기한이 있는데 늦은 걸 골랐는지)는 검사하지 않음 → 사용자 수정 허용
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

    // 3. 내부 계산

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
            List<Integer> doneIds = new ArrayList<>();               // 이미 처리한 id (같은 id 두 번 보내도 한 번만)
            for (Integer id : documentItemIds) {
                if (doneIds.contains(id)) continue;                  // 이미 처리한 id 면 건너뜀
                doneIds.add(id);

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
        int used = 0;                                         // 이번 계획에서 이 재고를 이미 쓴 수량 (없으면 0)
        if (planned.containsKey(s.getStockId())) {
            used = planned.get(s.getStockId());
        }
        return s.getQty() - s.getAllocatedQty() - used;
    }

    // 추천 계산 (DB 저장 X) : 품목 줄마다 재고 행과 수량을 정해 목록으로 돌려줌
    // [LOT 정하기] 1 소비기한 빠른 순 → 2-1 LOT 가용 합계 많은 순 → 2-2 LOT 첫 적재일 빠른 순 → 2-3 lotId
    // [칸 정하기]  3-1 칸 가용수량 많은 순 → 3-2 칸 적재일 빠른 순 → 3-3 stockId
    private List<AllocationPreviewDto> buildPlan(DocumentEntity documentEntity, List<DocumentItemEntity> items) {
        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate();   // 잔여일 계산 기준일 (출고 예정일)

        List<StockEntity> allStocks = stockRepository.findAll();   // 재고 전체 (품목마다 재사용)
        Map<Integer, Integer> planned = new HashMap<>();           // stockId → 이번 계획에서 이미 쓴 수량
        List<AllocationPreviewDto> plan = new ArrayList<>();       // 결과 (추천 1줄 = DTO 1개)

        for (DocumentItemEntity item : items) {
            int need = item.getExpectedQty();                             // 채워야 할 수량

            // (1) 자격 조건으로 후보 거르기 (같은 상품 · 운영 칸 · 소비기한 · 잔여일 + 꺼낼 수량 있음)
            List<StockEntity> candidates = new ArrayList<>();
            for (StockEntity s : allStocks) {
                if (!isShippable(item, s, shipDate)) continue;   // 출고 조건 불통과
                if (availableOf(s, planned) <= 0) continue;      // 꺼낼 게 없음
                candidates.add(s);                               // 모두 통과 → 후보
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
                int before = 0;                                       // 지금까지 모인 이 LOT 의 가용 합계 (처음이면 0)
                if (lotTotal.containsKey(lotId)) {
                    before = lotTotal.get(lotId);
                }
                lotTotal.put(lotId, before + availableOf(s, planned));
                LocalDateTime in = s.getCreatedAt();
                if (!lotFirstIn.containsKey(lotId) || in.isBefore(lotFirstIn.get(lotId))) {
                    lotFirstIn.put(lotId, in);
                }
            }

            // (4) 정렬 (LOT 기준 전부 → 칸 기준 전부)
            //     a 가 앞이면 음수, b 가 앞이면 양수, 같으면 0 을 돌려줌 → 0 이면 다음 기준으로 비교
            //     "많은 순" 은 a 와 b 를 바꿔서 비교 (큰 값이 앞으로)
            candidates.sort((a, b) -> {
                Integer lotA = a.getLotEntity().getLotId();
                Integer lotB = b.getLotEntity().getLotId();

                // 1 소비기한 빠른 순
                int result = a.getLotEntity().getExpiryDate().compareTo(b.getLotEntity().getExpiryDate());
                if (result != 0) return result;

                // 2-1 LOT 가용 합계 많은 순 (b 와 a 를 바꿔서 비교)
                result = lotTotal.get(lotB).compareTo(lotTotal.get(lotA));
                if (result != 0) return result;

                // 2-2 LOT 첫 적재일 빠른 순
                result = lotFirstIn.get(lotA).compareTo(lotFirstIn.get(lotB));
                if (result != 0) return result;

                // 2-3 lotId 작은 순
                result = lotA.compareTo(lotB);
                if (result != 0) return result;

                // 3-1 칸 가용수량 많은 순 (b 와 a 를 바꿔서 비교)
                result = Integer.compare(availableOf(b, planned), availableOf(a, planned));
                if (result != 0) return result;

                // 3-2 칸 적재일 빠른 순
                result = a.getCreatedAt().compareTo(b.getCreatedAt());
                if (result != 0) return result;

                // 3-3 stockId 작은 순
                return a.getStockId().compareTo(b.getStockId());
            });

            // (5) 정렬 순서대로 필요한 만큼 담기 (저장 X, planned 에만 기록)
            for (StockEntity s : candidates) {
                if (need == 0) break;                                   // 다 채웠으면 종료
                int take = Math.min(need, availableOf(s, planned));     // 꺼낼 수량
                plan.add(AllocationPreviewDto.from(item, s, take));     // 추천 1줄 추가 : 이 품목을 이 재고에서 take 개

                // 이 재고를 이번 계획에서 쓴 수량 누적 → 다음 품목이 같은 재고를 쓸 때 가용에서 빠짐
                int used = 0;
                if (planned.containsKey(s.getStockId())) {
                    used = planned.get(s.getStockId());
                }
                planned.put(s.getStockId(), used + take);
                need -= take;                                           // 남은 필요량 감소
            }
        }
        return plan;
    }
}