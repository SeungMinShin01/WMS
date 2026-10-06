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
//   1. 미리보기          : previewAllocate
//   2. 미리보기 단계     : checkAllocatable → selectItems → buildPlan
//   3. 계산 도우미       : isShippable, availableOf
//   4. 다른 서비스 호출용 : itemsOf, allocatedSum, checkShippable, shippableQty
@Service
public class AllocationPlanService {

    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemRepository documentItemRepository;
    @Autowired private DocumentItemDetailRepository documentItemDetailRepository;
    @Autowired private StockRepository stockRepository;

    // 1. 미리보기 (컨트롤러가 부르는 메서드)

    // 선택한 품목 줄들의 추천 결과를 돌려줌 (저장 안 함)
    // documentItemIds 가 비어 있으면 문서의 전체 품목 줄이 대상
    // @Transactional(readOnly = true) : 읽기 전용 트랜잭션
    // 실수로 엔티티 값을 바꿔도 DB 에 반영(더티 체킹)되지 않게 막는 안전장치
    @Transactional(readOnly = true)
    public List<AllocationPreviewDto> previewAllocate(Integer documentId, List<Integer> documentItemIds) {
        DocumentEntity documentEntity = checkAllocatable(documentId);              // 문서 검사 (404/400/409)
        List<DocumentItemEntity> items = selectItems(documentId, documentItemIds); // 품목 줄 고르기 (400/409)
        List<AllocationPreviewDto> result = buildPlan(documentEntity, items);      // 추천 계산 (재고 부족 409)

        // 로케이션 코드 순 정렬 (피킹 동선 순서)
        // list.sort((a, b) -> ...) : 목록 안의 두 값 a, b 를 비교하는 규칙을 주면 그 규칙대로 정렬
        // String 의 compareTo : 글자 순(사전 순)으로 비교 → "A-01-01" 이 "A-01-02" 보다 앞
        result.sort((a, b) -> a.getLocationCode().compareTo(b.getLocationCode()));
        return result;
    }

    // 2. 미리보기 단계

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

    // 사용자가 체크한 품목 줄만 골라냄
    // 선택 없음 → 전체 / 이 문서에 없는 id 400 / 이미 할당된 줄 409
    private List<DocumentItemEntity> selectItems(Integer documentId, List<Integer> documentItemIds) {
        List<DocumentItemEntity> all = itemsOf(documentId); // 이 문서의 품목 줄 전체
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
            if (allocatedSum(item.getDocumentItemId()) > 0) {
                throw new IllegalStateException("이미 할당된 품목입니다: " + item.getProductEntity().getProductName());
            }
        }
        return selected;
    }

    // 추천 계산 (DB 저장 X) : 품목 줄마다 어느 재고에서 몇 개 꺼낼지 정해 목록으로 돌려줌
    // [LOT 정하기] 1 소비기한 빠른 순 → 2-1 LOT 가용 합계 많은 순 → 2-2 LOT 첫 적재일 빠른 순 → 2-3 lotId
    // [칸 정하기]  3-1 칸 가용수량 많은 순 → 3-2 칸 적재일 빠른 순 → 3-3 stockId
    private List<AllocationPreviewDto> buildPlan(DocumentEntity documentEntity, List<DocumentItemEntity> items) {
        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate(); // 출고 예정일 (시간 떼고 날짜만)
        List<StockEntity> allStocks = stockRepository.findAll();           // 재고 전체 (품목마다 재사용)

        // Map<키, 값> : 이름표(키)로 값을 찾아 쓰는 메모장
        // HashMap : Map 의 실제 구현체. 키로 바로 찾아서 빠름 (순서는 보장 안 함)
        // planned : 재고번호(stockId) → 이번 계산에서 이미 쓴 수량
        //   처음엔 비어 있고, (5)에서 재고를 꺼낼 때마다 기록됨
        //   → 한 문서에 같은 상품 품목 줄이 2개면, 뒤 줄이 앞 줄이 쓴 수량을 또 쓰지 않게 막음
        Map<Integer, Integer> planned = new HashMap<>();
        List<AllocationPreviewDto> plan = new ArrayList<>(); // 결과 (추천 1줄 = DTO 1개)

        for (DocumentItemEntity item : items) {
            int need = item.getExpectedQty(); // 이 품목 줄에서 채워야 할 수량

            // (1) 후보 거르기 : 출고 가능 + 같은 화주 + 꺼낼 수량 있음
            List<StockEntity> candidates = new ArrayList<>();
            for (StockEntity s : allStocks) {
                if (!isShippable(item, s, shipDate)) continue; // 상품·칸·소비기한·잔여일 조건 탈락

                // [ED-61] 재고 화주 ≠ 문서 화주면 제외 (정상이면 같지만 잘못 들어간 데이터 방어)
                if (!s.getTenantEntity().getTenantId().equals(documentEntity.getTenantEntity().getTenantId())) continue;

                if (availableOf(s, planned) <= 0) continue; // 꺼낼 수량 없음
                candidates.add(s);
            }

            // (2) 후보 가용 합계 < 필요량 → 409
            int totalAvailable = 0;
            for (StockEntity s : candidates) {
                totalAvailable += availableOf(s, planned);
            }
            if (totalAvailable < need) {
                throw new IllegalStateException(item.getProductEntity().getProductName()
                        + " 가용 부족 · 필요 " + need + " · 가용 " + totalAvailable);
            }

            // (3) LOT 단위 값 만들기 (정렬 2-1, 2-2 에서 사용)
            // 한 LOT 가 여러 칸에 나뉘어 있을 수 있음 (재고 행 여러 개) → LOT 기준으로 모아야 함
            // lotTotal   : LOT번호 → 그 LOT 의 가용 합계
            // lotFirstIn : LOT번호 → 그 LOT 에서 가장 이른 적재 시각
            Map<Integer, Integer> lotTotal = new HashMap<>();
            Map<Integer, LocalDateTime> lotFirstIn = new HashMap<>();
            for (StockEntity s : candidates) {
                Integer lotId = s.getLotEntity().getLotId();

                // LOT 가용 합계 누적
                // containsKey(키) : Map 에 그 키가 있으면 true
                // get(키)         : 그 키에 적힌 값을 꺼냄
                // put(키, 값)     : 키가 없으면 새로 적고, 있으면 새 값으로 덮어씀
                // 예) LOT 7 이 칸 2개(가용 30, 20)에 있으면
                //     첫 재고 : 키 없음 → before 0 → put(7, 30)
                //     둘째 재고 : 키 있음 → before 30 → put(7, 50)
                int before = 0;
                if (lotTotal.containsKey(lotId)) {
                    before = lotTotal.get(lotId);
                }
                lotTotal.put(lotId, before + availableOf(s, planned));

                // LOT 첫 적재 시각 : 처음 나온 LOT 이거나, 지금 적힌 시각보다 더 이르면 덮어씀
                // → 끝나면 LOT 마다 가장 이른 적재 시각만 남음
                LocalDateTime in = s.getCreatedAt();
                if (!lotFirstIn.containsKey(lotId) || in.isBefore(lotFirstIn.get(lotId))) {
                    lotFirstIn.put(lotId, in);
                }
            }

            // (4) 정렬 (LOT 기준 전부 → 칸 기준 전부)
            // sort 규칙 : 음수를 돌려주면 a 가 앞, 양수면 b 가 앞, 0 이면 같은 순위
            // A.compareTo(B) : A 가 작거나 이르면 음수 / 같으면 0 / 크거나 늦으면 양수
            //   → a.compareTo(b) 는 작은 값이 앞 (오름차순)
            //   → b.compareTo(a) 처럼 자리를 바꾸면 큰 값이 앞 (내림차순, "많은 순")
            // 기준마다 result 가 0 이 아니면 순서가 정해진 것 → 바로 return
            //                  0 이면 그 기준으로는 같음 → 다음 기준으로 넘어감
            candidates.sort((a, b) -> {
                Integer lotA = a.getLotEntity().getLotId();
                Integer lotB = b.getLotEntity().getLotId();

                // 1 소비기한 빠른 순 (FEFO : 먼저 만료되는 것부터)
                int result = a.getLotEntity().getExpiryDate().compareTo(b.getLotEntity().getExpiryDate());
                if (result != 0) return result;

                // 2-1 LOT 가용 합계 많은 순 (b, a 자리 바꿈) : 한 LOT 를 통째로 먼저 소진 → LOT 섞임 줄임
                result = lotTotal.get(lotB).compareTo(lotTotal.get(lotA));
                if (result != 0) return result;

                // 2-2 LOT 첫 적재일 빠른 순 : 먼저 들어온 LOT 먼저 (FIFO)
                result = lotFirstIn.get(lotA).compareTo(lotFirstIn.get(lotB));
                if (result != 0) return result;

                // 2-3 lotId 작은 순 : 위가 다 같을 때 순서를 하나로 고정
                result = lotA.compareTo(lotB);
                if (result != 0) return result;

                // 3-1 칸 가용수량 많은 순 (b, a 자리 바꿈) : 적은 칸 수에서 꺼내 피킹 동선 줄임
                // availableOf 는 int(기본형)라 .compareTo 를 못 씀 → Integer.compare(x, y) 사용
                // Integer.compare : x < y 음수 / 같으면 0 / x > y 양수 (빼기 x - y 는 숫자가 넘칠 위험이 있어 안 씀)
                result = Integer.compare(availableOf(b, planned), availableOf(a, planned));
                if (result != 0) return result;

                // 3-2 칸 적재일 빠른 순
                result = a.getCreatedAt().compareTo(b.getCreatedAt());
                if (result != 0) return result;

                // 3-3 stockId 작은 순 (마지막 고정 기준)
                return a.getStockId().compareTo(b.getStockId());
            });

            // (5) 정렬 순서대로 필요한 만큼 담기 (저장 X, planned 에만 기록)
            for (StockEntity s : candidates) {
                if (need == 0) break; // 다 채웠으면 종료

                // Math.min(x, y) : 두 수 중 작은 값
                // 재고가 넉넉하면 need 만큼만, 모자라면 이 재고에 있는 만큼만 가져감
                int take = Math.min(need, availableOf(s, planned));

                plan.add(AllocationPreviewDto.from(item, s, take)); // 추천 1줄 (품목 + 재고 + 수량)

                // planned 에 이 재고를 쓴 수량 누적 (위 (3)의 containsKey → get → put 과 같은 방식)
                int used = 0;
                if (planned.containsKey(s.getStockId())) {
                    used = planned.get(s.getStockId());
                }
                planned.put(s.getStockId(), used + take);

                need -= take; // 남은 필요량 줄이기
            }
        }
        return plan;
    }

    // 3. 계산 (이 클래스 안에서만 씀)

    // 재고 1행이 이 품목 줄에 출고 가능한지 true/false 로만 판단 (예외 안 던짐)
    // buildPlan(후보 거르기), shippableQty(화면 표시값) 에서 사용
    // 조건 : 같은 상품 · 운영 중인 칸 · 소비기한 있음 · 잔여일 충분
    // 상품 비교는 buildPlan 이 재고 전체를 넘기기 때문에 필요 (shippableQty 는 같은 상품만 받아서 항상 통과)
    private boolean isShippable(DocumentItemEntity item, StockEntity s, LocalDate shipDate) {
        // 재고 → LOT → 상품 id 와 주문 품목의 상품 id 비교
        if (!s.getLotEntity().getProductEntity().getProductId().equals(item.getProductEntity().getProductId())) return false;
        if (!s.getLocationEntity().getIsActive()) return false; // 안 쓰는 칸

        LocalDate expiry = s.getLotEntity().getExpiryDate();
        if (expiry == null) return false; // 소비기한 없음

        // 잔여일 검사 : 소비기한 >= 출고예정일 + 출고 허용 잔여일 이어야 통과
        // plusDays(n) : 그 날짜에 n 일을 더한 새 날짜 (원래 날짜는 안 바뀜)
        //   예) shipDate 2026-10-05, minShipDays 30 → limitDate 2026-11-04
        // isBefore(날짜) : 앞 날짜가 괄호 날짜보다 이르면 true (같은 날은 false → 통과)
        //   예) 2026-10-25.isBefore(2026-11-04) → true → 잔여일 부족
        int minShipDays = item.getProductEntity().getMinShipDays();
        LocalDate limitDate = shipDate.plusDays(minShipDays);
        if (expiry.isBefore(limitDate)) return false;
        return true;
    }

    // 계획 반영 가용수량 = 실물 − 선점 − 이번 계산에서 이미 쓴 수량(planned)
    private int availableOf(StockEntity s, Map<Integer, Integer> planned) {
        int used = 0; // planned 에 이 재고가 없으면 아직 안 쓴 것 → 0
        if (planned.containsKey(s.getStockId())) {
            used = planned.get(s.getStockId());
        }
        return s.getQty() - s.getAllocatedQty() - used;
    }

   
    // 4. 다른 서비스에서도 부르는 메서드

    // 이 문서의 품목 줄 전체 (document_item 중 document_id 가 같은 것)
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

    // 품목 줄 1개에 지금까지 할당된 수량 합계 (document_item_detail 의 qty 합)
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

    // 재고 1행이 이 품목 줄에 출고 가능한지 검사 (PickingListService 피킹리스트 생성에서 호출)
    // isShippable 과 조건은 같지만, 이유를 알려줘야 해서 false 대신 예외를 던짐
    // 다른 상품 400 / 사용 안 하는 칸 409 / 소비기한 없음 409 / 잔여일 부족 409
    // FEFO 순서(더 빠른 소비기한을 두고 늦은 걸 골랐는지)는 검사 안 함 → 사용자 수정 허용
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
        int minShipDays = item.getProductEntity().getMinShipDays();
        LocalDate limitDate = shipDate.plusDays(minShipDays); // 출고예정일 + 최소 잔여일 (isShippable 과 같은 계산)
        if (expiry.isBefore(limitDate)) {
            throw new IllegalStateException("소비기한 잔여일이 부족합니다 · 재고 " + stock.getStockId()
                    + " · 소비기한 " + expiry + " · 필요 " + limitDate + " 이후 (최소 " + minShipDays + "일)");
        }
    }

    // 이 품목 줄의 출고 가능 재고 합계 (OutboundService ED-17 출고 문서 상세 조회에서 호출 → 화면 표시)
    // 추천 후보(buildPlan (1))와 같은 기준 : isShippable 통과 + 같은 화주 + 가용 > 0
    // productStocks : 이 품목 상품의 재고만 담긴 목록 (OutboundService 에서 상품별로 나눠서 넘겨줌)
    public int shippableQty(DocumentItemEntity item, LocalDate shipDate, List<StockEntity> productStocks) {
        int sum = 0;
        for (StockEntity s : productStocks) {
            if (!isShippable(item, s, shipDate)) continue;

            // [ED-61] 다른 화주 재고는 제외
            if (!s.getTenantEntity().getTenantId().equals(item.getDocumentEntity().getTenantEntity().getTenantId())) continue;

            int available = s.getQty() - s.getAllocatedQty(); // 가용 = 실물 − 선점
            if (available > 0) sum += available;
        }
        return sum;
    }
}