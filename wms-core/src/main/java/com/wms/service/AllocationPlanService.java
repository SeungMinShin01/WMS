package com.wms.service;

import java.time.LocalDate;
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
// - DB 에 아무것도 저장하지 않음, 어느 재고에서 몇 개 꺼낼지 계산까지만
// - 예외는 GlobalExceptionHandler 가 응답으로 바꿈 : 404 없음 / 400 잘못된 요청 / 409 상태·재고 충돌
//
// 메서드 순서 (위 → 아래 = 실행되는 순서)
//   1. previewAllocate         : 추천 받기 시작 (컨트롤러가 부름)
//   2. checkAllocatable        : 문서 검사
//   3. selectItems             : 체크한 품목 줄 고르기
//      3-1. itemsOf            : 문서의 품목 줄 전체
//      3-2. allocatedSum       : 품목 줄의 할당 합계
//   4. buildPlan               : 추천 계산 (꺼낼 순서 계산은 FefoStrategy 파일)
//      4-1. isShippable        : 출고 가능한가 true/false
//      4-2. failReason         : 출고 가능 규칙 5개 (한 곳에 모음)
//      4-3. availableOf        : 계획 반영 가용수량
//   5. 다른 서비스에서만 부르는 메서드
//      5-1. checkShippable     : 사용자가 고른 재고 검사 (PickingListService)
//      5-2. shippableQty       : 출고 가능 재고 합계 (OutboundService ED-17)
//
// [ED-출고 할당 추천(buildPlan) 알고리즘 리팩터링]
//   buildPlan 의 역할을 "재고를 상품별로 묶기 → 그 상품 재고만 규칙으로 거르기 → 계산은 AllocationStrategy 에 맡기기 → DTO 변환" 으로 나눔
//   어떤 재고를 먼저 꺼낼지(FEFO 우선순위) 계산은 FefoStrategy (우선순위 큐 2단계) 가 담당
@Service
public class AllocationPlanService {

    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemRepository documentItemRepository;
    @Autowired private DocumentItemDetailRepository documentItemDetailRepository;
    @Autowired private StockRepository stockRepository;
    // [ED-출고 할당 추천(buildPlan) 알고리즘 리팩터링] 추천 계산 전략 (구현체 FefoStrategy 를 스프링이 넣어줌)
    @Autowired private AllocationStrategy allocationStrategy;

    // ===== 1. 추천 받기 시작 (컨트롤러가 부르는 메서드) =====

    // 선택한 품목 줄들의 추천 결과를 돌려줌 (저장 안 함)
    // documentItemIds 가 비어 있으면 문서의 전체 품목 줄이 대상
    // @Transactional(readOnly = true) : 읽기 전용 트랜잭션
    // 실수로 엔티티 값을 바꿔도 DB 에 반영(더티 체킹)되지 않게 막는 안전장치
    @Transactional(readOnly = true)
    public List<AllocationPreviewDto> previewAllocate(Integer documentId, List<Integer> documentItemIds) {
        DocumentEntity documentEntity = checkAllocatable(documentId);              // 2. 문서 검사 (404/400/409)
        List<DocumentItemEntity> items = selectItems(documentId, documentItemIds); // 3. 품목 줄 고르기 (400/409)
        List<AllocationPreviewDto> result = buildPlan(documentEntity, items);      // 4. 추천 계산 (재고 부족 409)

        // 로케이션 코드 순 정렬 (피킹 동선 순서)
        // list.sort((a, b) -> ...) : 목록 안의 두 값 a, b 를 비교하는 규칙을 주면 그 규칙대로 정렬
        // String 의 compareTo : 글자 순(사전 순)으로 비교 → "A-01-01" 이 "A-01-02" 보다 앞
        result.sort((a, b) -> a.getLocationCode().compareTo(b.getLocationCode()));
        return result;
    }

    // ===== 2. 문서 검사 =====

    // 문서 검사 : 없는 문서 404 / 출고 문서 아님 400 / 대기·할당 상태 아님 409
    // WAITING(할당 전), ALLOCATED(할당 중) 에서만 통과
    // 이미 PICKING 으로 넘어간 문서는 막힘 → 더블클릭 방어 역할도 함
    // PickingListService(피킹리스트 생성)도 같은 검사를 위해 호출
    public DocumentEntity checkAllocatable(Integer documentId) {
        // findById : PK 로 한 건 조회. 결과가 Optional(있을 수도, 없을 수도 있는 상자)로 옴
        // orElseThrow : 상자가 비어 있으면(없는 문서) 예외를 던짐 → 404
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));

        // enum 비교는 == / !=
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            // throw : 여기서 메서드를 즉시 멈추고 예외를 호출한 쪽으로 던짐
            //         → 컨트롤러까지 올라가서 GlobalExceptionHandler 가 400 응답으로 바꿈
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }
        DocumentStatus status = documentEntity.getStatus();
        if (status != DocumentStatus.WAITING && status != DocumentStatus.ALLOCATED) {
            throw new IllegalStateException("대기·할당 상태에서만 할당할 수 있습니다. 현재 상태: " + status); // 409
        }
        return documentEntity;
    }

    // ===== 3. 품목 줄 고르기 =====

    // 사용자가 체크한 품목 줄만 골라냄
    // 선택 없음 → 전체 / 이 문서에 없는 id 400 / 이미 할당된 줄 409
    private List<DocumentItemEntity> selectItems(Integer documentId, List<Integer> documentItemIds) {
        List<DocumentItemEntity> all = itemsOf(documentId); // 3-1. 이 문서의 품목 줄 전체
        if (all.isEmpty()) { // isEmpty : 목록이 비어 있으면 true
            throw new IllegalStateException("품목이 없는 문서입니다: " + documentId);
        }

        List<DocumentItemEntity> selected = new ArrayList<>(); // 골라낸 품목 줄
        if (documentItemIds == null || documentItemIds.isEmpty()) {
            selected.addAll(all); // 선택이 없으면 전체를 담음
        } else {
            List<Integer> doneIds = new ArrayList<>(); // 이미 처리한 id ([21, 21] 같은 중복 방지)
            for (Integer id : documentItemIds) {
                if (doneIds.contains(id)) continue; // contains : 목록에 그 값이 있으면 true
                doneIds.add(id);

                // 이 문서의 품목 줄 중에서 id 가 같은 줄 찾기
                DocumentItemEntity found = null;
                for (DocumentItemEntity item : all) {
                    if (item.getDocumentItemId().equals(id)) {
                        found = item;
                        break;
                    }
                }
                if (found == null) { // 못 찾음 = 다른 문서의 품목 줄
                    throw new IllegalArgumentException("이 문서의 품목 줄이 아닙니다: " + id);
                }
                selected.add(found);
            }
        }

        // 이미 할당된 줄이면 막음 (다른 사람이 먼저 할당했거나, 새로고침 안 한 화면에서 다시 누른 경우)
        for (DocumentItemEntity item : selected) {
            if (allocatedSum(item.getDocumentItemId()) > 0) { // 3-2. 할당 합계
                throw new IllegalStateException("이미 할당된 품목입니다: " + item.getProductEntity().getProductName());
            }
        }
        return selected;
    }

    // 3-1. 이 문서의 품목 줄 전체 (document_item 중 document_id 가 같은 것)
    // 사용 : selectItems / PickingListService / OutboundService
    public List<DocumentItemEntity> itemsOf(Integer documentId) {
        List<DocumentItemEntity> items = new ArrayList<>();
        for (DocumentItemEntity item : documentItemRepository.findAll()) {
            if (item.getDocumentEntity().getDocumentId().equals(documentId)) {
                items.add(item);
            }
        }
        return items;
    }

    // 3-2. 품목 줄 1개에 지금까지 할당된 수량 합계 (document_item_detail 의 qty 합)
    // 검사는 부르는 쪽에서 함
    //   추천·피킹리스트 생성 : 0 보다 크면 이미 할당된 줄 → 409
    //   집음·문서 상태 정리  : 요청 수량보다 작으면 아직 덜 할당된 줄
    //   ED-17 출고 문서 상세 조회  : 화면의 "할당 수량"
    public int allocatedSum(Integer documentItemId) {
        int sum = 0;
        for (DocumentItemDetailEntity d : documentItemDetailRepository.findAll()) { // detail은 피킹 리스트 생성을 누를 때 처음 생성되서 그 전 (출고 예정 상태)에는 detail이 없음
            if (d.getDocumentItemEntity().getDocumentItemId().equals(documentItemId)) {
                // 한 품목 줄에 detail이 여러 줄일 수 있음
                // detail 1줄 : 품목 줄 21, 재고 10, 60개
                // detail 1줄 : 품목 줄 21, 재고 11, 10개
                sum += d.getQty();
            }
        }
        return sum;
    }

    // ===== 4. 추천 계산 =====

    // [ED-출고 할당 추천(buildPlan) 알고리즘 리팩터링] buildPlan 구조 변경
    // 추천 계산 (DB 저장 X) : 품목 줄마다 어느 재고에서 몇 개 꺼낼지 정해 목록으로 돌려줌
    // 전 : 재고 전체를 품목마다 돌며 if 로 거르기 → lotTotal·lotFirstIn Map → 후보 전체를 비교 7단계로 sort → 앞에서부터 담기
    // 후 : (가) 재고를 상품별로 한 번 묶기
    //      (나) 품목 줄마다 그 상품 재고만 꺼내기
    //      (다) 규칙으로 거르기 + 가용 계산
    //      (라) 가용 부족 409
    //      (마) 꺼낼 재고·수량 계산은 allocationStrategy.plan() 에 맡기기 (FefoStrategy : LOT 큐 → 칸 큐)
    //      (바) 결과를 DTO 로 바꾸고 planned 에 기록
    // 우선순위 (FefoStrategy 안에서 그대로 유지)
    //   [LOT 정하기] 1 소비기한 빠른 순 → 2-1 LOT 가용 합계 많은 순 → 2-2 LOT 첫 적재일 빠른 순 → 2-3 lotId
    //   [칸 정하기]  3-1 칸 가용수량 많은 순 → 3-2 칸 적재일 빠른 순 → 3-3 stockId
    private List<AllocationPreviewDto> buildPlan(DocumentEntity documentEntity, List<DocumentItemEntity> items) {
        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate(); // 출고 예정일 (시간 떼고 날짜만)

        // (가) 재고를 상품별로 묶기 (buildPlan 시작할 때 한 번만)
        // 전 : allStocks(재고 전체)를 품목 줄마다 처음부터 끝까지 돌았음 → 품목 수 × 재고 수
        // 후 : 상품id → 그 상품의 재고 목록 으로 한 번 묶어 두고, 품목 줄마다 자기 상품 재고만 꺼냄
        //      (ED-17 OutboundService 의 stocksByProduct 와 같은 방식)
        Map<Integer, List<StockEntity>> stocksByProduct = new HashMap<>();
        for (StockEntity s : stockRepository.findAll()) {
            Integer productId = s.getLotEntity().getProductEntity().getProductId(); // 재고 → LOT → 상품 id
            if (!stocksByProduct.containsKey(productId)) {         // 처음 나온 상품이면
                stocksByProduct.put(productId, new ArrayList<>()); // 빈 목록을 먼저 만들어 둠
            }
            stocksByProduct.get(productId).add(s);                 // 그 상품 목록에 재고 추가
        }

        // planned : 재고번호(stockId) → 이번 계산에서 이미 쓴 수량
        //   처음엔 비어 있고, (바)에서 재고를 꺼낼 때마다 기록됨
        //   → 한 문서에 같은 상품 품목 줄이 2개면, 뒤 줄이 앞 줄이 쓴 수량을 또 쓰지 않게 막음
        Map<Integer, Integer> planned = new HashMap<>();
        List<AllocationPreviewDto> plan = new ArrayList<>(); // 결과 (추천 1줄 = DTO 1개)

        for (DocumentItemEntity item : items) {
            int need = item.getExpectedQty(); // 이 품목 줄에서 채워야 할 수량

            // (나) 이 품목 상품의 재고만 꺼내기 (없으면 빈 목록)
            Integer productId = item.getProductEntity().getProductId();
            List<StockEntity> productStocks = new ArrayList<>();
            if (stocksByProduct.containsKey(productId)) {
                productStocks = stocksByProduct.get(productId);
            }

            // (다) 후보 거르기 : 규칙 통과(4-1 isShippable) + 꺼낼 수량 있음(4-3 availableOf)
            // available : 재고번호 → 이번 계산 기준 가용수량 (실물 − 선점 − planned)
            //   후보마다 한 번 계산해 두고 (라) 합계 검사와 (마) 전략 계산에서 같이 씀
            List<StockEntity> candidates = new ArrayList<>();
            Map<Integer, Integer> available = new HashMap<>();
            for (StockEntity s : productStocks) {
                if (!isShippable(item, s, shipDate)) continue; // 규칙 하나라도 실패하면 후보 제외
                int canTake = availableOf(s, planned);
                if (canTake <= 0) continue;                    // 꺼낼 수량 없음
                candidates.add(s);
                available.put(s.getStockId(), canTake);
            }

            // (라) 후보 가용 합계 < 필요량 → 409
            int totalAvailable = 0;
            for (StockEntity s : candidates) {
                totalAvailable += available.get(s.getStockId());
            }
            if (totalAvailable < need) {
                throw new IllegalStateException(item.getProductEntity().getProductName()
                        + " 가용 부족 · 필요 " + need + " · 가용 " + totalAvailable);
            }

            // (마) 어떤 재고에서 몇 개 꺼낼지 전략에게 맡김 → FefoStrategy.plan()
            // 전 : 여기서 lotTotal·lotFirstIn 을 만들고 candidates.sort(비교 7단계) 후 for 로 담음
            // 후 : LOT 우선순위 큐 → 그 LOT 의 칸 우선순위 큐 순서로 need 만큼 꺼내서 돌려줌
            List<AllocationStrategy.Pick> picks = allocationStrategy.plan(need, candidates, available);

            // (바) 꺼낸 순서대로 추천 1줄씩 만들고 planned 에 기록 (저장 X)
            for (AllocationStrategy.Pick pick : picks) {
                StockEntity s = pick.getStock();
                int take = pick.getQty();
                plan.add(AllocationPreviewDto.from(item, s, take)); // 추천 1줄 (품목 + 재고 + 수량)

                // planned 에 이 재고를 쓴 수량 누적 → 다음 품목 줄이 같은 재고를 쓸 때 가용에서 빠짐
                int used = 0;
                if (planned.containsKey(s.getStockId())) {
                    used = planned.get(s.getStockId());
                }
                planned.put(s.getStockId(), used + take);
            }
        }
        return plan;
    }

    // 4-1. 재고 1행이 출고 가능한지 true/false (예외 안 던짐)
    // [ED-출고 할당 추천(buildPlan) 알고리즘 리팩터링]
    // 전 : 안에 if 4개를 직접 적음 (checkShippable 과 중복)
    // 후 : 4-2 failReason 이 null 이면(걸린 규칙이 없으면) 출고 가능
    private boolean isShippable(DocumentItemEntity item, StockEntity s, LocalDate shipDate) {
        return failReason(item, s, shipDate) == null;
    }

    // 4-2. 출고 가능 규칙 5개 (한 곳에 모음)
    // [ED-출고 할당 추천(buildPlan) 알고리즘 리팩터링] 새로 추가
    // 재고 1행이 이 품목 줄에 출고 가능한지 규칙 5개를 위에서부터 검사
    //   통과하면 null, 걸리면 그 규칙의 이유 메시지를 돌려줌 (처음 걸린 규칙에서 바로 return)
    // 추천(isShippable), 직접 선택 검증(checkShippable), 출고 가능 재고(shippableQty) 가 모두 이 메서드를 씀
    // → 규칙을 바꿀 때 여기 한 곳만 고치면 됨
    // 전 : 같은 규칙이 isShippable(if 4개) / checkShippable(if 4개) / buildPlan·shippableQty(화주 if) 에 흩어져 있었음
    private String failReason(DocumentItemEntity item, StockEntity s, LocalDate shipDate) {
        // 1. 같은 상품 (재고 → LOT → 상품 id 와 주문 품목의 상품 id 비교)
        //    buildPlan·shippableQty 는 같은 상품 재고만 받아서 항상 통과하지만,
        //    checkShippable(사용자가 직접 고른 재고) 에서는 다른 상품을 막아야 해서 남겨 둠
        if (!s.getLotEntity().getProductEntity().getProductId().equals(item.getProductEntity().getProductId())) {
            return "주문 품목과 다른 상품의 재고입니다 · 재고 " + s.getStockId();
        }

        // 2. 운영 중인 칸
        if (!s.getLocationEntity().getIsActive()) {
            return "사용하지 않는 칸의 재고입니다 · 재고 " + s.getStockId();
        }

        // 3. 소비기한 있음
        LocalDate expiry = s.getLotEntity().getExpiryDate();
        if (expiry == null) {
            return "소비기한이 없는 재고입니다 · 재고 " + s.getStockId();
        }

        // 4. 잔여일 충분 : 소비기한 >= 출고예정일 + 출고 허용 잔여일 (같은 날은 통과)
        // plusDays(n) : 그 날짜에 n 일을 더한 새 날짜
        // isBefore(날짜) : 앞 날짜가 괄호 날짜보다 이르면 true → 잔여일 부족
        int minShipDays = item.getProductEntity().getMinShipDays();
        LocalDate limitDate = shipDate.plusDays(minShipDays);
        if (expiry.isBefore(limitDate)) {
            return "소비기한 잔여일이 부족합니다 · 재고 " + s.getStockId()
                    + " · 소비기한 " + expiry + " · 필요 " + limitDate + " 이후 (최소 " + minShipDays + "일)";
        }

        // 5. [ED-61] 같은 화주 (같은 상품이면 화주도 같아야 정상이지만 잘못 들어간 데이터 방어)
        if (!s.getTenantEntity().getTenantId().equals(item.getDocumentEntity().getTenantEntity().getTenantId())) {
            return "다른 화주의 재고입니다 · 재고 " + s.getStockId();
        }

        return null; // 전부 통과
    }

    // 4-3. 계획 반영 가용수량 = 실물 − 선점 − 이번 계산에서 이미 쓴 수량(planned)
    private int availableOf(StockEntity s, Map<Integer, Integer> planned) {
        int used = 0; // planned 에 이 재고가 없으면 아직 안 쓴 것 → 0
        if (planned.containsKey(s.getStockId())) {
            used = planned.get(s.getStockId());
        }
        return s.getQty() - s.getAllocatedQty() - used;
    }

    // ===== 5. 다른 서비스에서만 부르는 메서드 =====

    // 5-1. 사용자가 고른 재고 1행이 이 품목 줄에 출고 가능한지 검사 (PickingListService 피킹리스트 생성에서 호출)
    // [ED-출고 할당 추천(buildPlan) 알고리즘 리팩터링]
    // 전 : isShippable 과 같은 조건을 if 4개로 한 번 더 적고 각각 throw
    // 후 : 같은 4-2 failReason 으로 검사하고, 걸린 이유로 예외 (메시지 그대로)
    //      다른 상품이면 요청이 잘못된 것 → 400 / 나머지(칸·소비기한·잔여일·화주) → 409
    // FEFO 순서(더 빠른 소비기한을 두고 늦은 걸 골랐는지)는 검사 안 함 → 사용자 수정 허용
    public void checkShippable(DocumentItemEntity item, StockEntity stock, LocalDate shipDate) {
        String reason = failReason(item, stock, shipDate);
        if (reason == null) {
            return; // 전부 통과
        }

        // 상품이 다르면 400 (failReason 이 상품을 맨 먼저 검사하므로 이때 reason 은 상품 메시지)
        boolean sameProduct = stock.getLotEntity().getProductEntity().getProductId()
                .equals(item.getProductEntity().getProductId());
        if (!sameProduct) {
            throw new IllegalArgumentException(reason); // 400
        }
        throw new IllegalStateException(reason);        // 409
    }

    // 5-2. 이 품목 줄의 출고 가능 재고 합계 (OutboundService ED-17 출고 문서 상세 조회에서 호출 → 화면 표시)
    // [ED-출고 할당 추천(buildPlan) 알고리즘 리팩터링]
    // 추천 후보(buildPlan (다))와 같은 기준 : 4-1 isShippable 통과(같은 화주 포함) + 가용 > 0
    // 전 : isShippable + 화주 if 를 따로 검사
    // 후 : isShippable 하나로 검사 (화주는 failReason 5번에 들어감)
    // productStocks : 이 품목 상품의 재고만 담긴 목록 (OutboundService 에서 상품별로 나눠서 넘겨줌)
    public int shippableQty(DocumentItemEntity item, LocalDate shipDate, List<StockEntity> productStocks) {
        int sum = 0;
        for (StockEntity s : productStocks) {
            if (!isShippable(item, s, shipDate)) continue;

            int available = s.getQty() - s.getAllocatedQty(); // 가용 = 실물 − 선점
            if (available > 0) sum += available;
        }
        return sum;
    }
}