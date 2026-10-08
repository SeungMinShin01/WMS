package com.wms.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.outbound.AllocationDto;
import com.wms.model.dto.outbound.PickingListDto;
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

// ED-18 피킹리스트 생성 (실제 할당) + ED-19 피킹리스트 조회
//   사용자가 미리보기를 보고 수정한 값을 받아서 → 다시 검증 → 저장 → 상태 전이 , 미리보기와 확정 사이에 재고가 바뀌었을 수 있으므로 여기서 한 번 더 검사
// 메서드 순서 (위 → 아래 = 실행 흐름)
//   1. ED-18 피킹리스트 생성 : createPickingList
//      ├ 7번에서 호출 : updateStatusAfterAllocation
//      └ 8번에서 호출 : getPickingList
//   2. ED-19 피킹리스트 조회 : getPickingList (화면에서도 따로 호출)
//   3. ED-52 피킹 확인(집음) : pickDetail (피킹리스트를 본 뒤 작업자가 호출)
@Service
@Transactional
public class PickingListService {

    // @Autowired : 스프링이 만들어 둔 객체(빈)를 이 변수에 자동으로 넣어줌 (new 로 직접 만들지 않음)
    @Autowired private AllocationPlanService allocationPlanService;   // 검증 메서드 재사용
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemRepository documentItemRepository;
    @Autowired private DocumentItemDetailRepository documentItemDetailRepository;
    @Autowired private StockRepository stockRepository;
    // 1. ED-18 피킹리스트 생성 (할당 확정)

    // ED-18 피킹리스트 생성 (할당 확정)
    // rows : 사용자가 최종 확정한 [ {documentItemId, stockId, qty}, ... ]
    public synchronized List<PickingListDto> createPickingList(Integer documentId, List<AllocationDto> rows) {

        // 1. 문서 검사 (404 / 400 / 409 대기·할당 상태 아님)
        DocumentEntity documentEntity = allocationPlanService.checkAllocatable(documentId);
        // getExpectedAt() 은 LocalDateTime(날짜+시간) → toLocalDate() 로 시간을 떼고 날짜만 남김
        // 소비기한(LocalDate)과 날짜끼리 비교하기 위해
        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate();

        // 2. 빈 요청 → 400
        // null 검사를 먼저 해야 함 : rows 가 null 인데 rows.isEmpty() 를 부르면 NullPointerException(500)
        // || 는 앞이 true 면 뒤를 실행하지 않아서 안전함
        if (rows == null || rows.isEmpty()) {
            // throw : 메서드를 즉시 멈추고 예외를 호출한 쪽으로 던짐
            //         → 컨트롤러까지 올라가 GlobalExceptionHandler 가 400 응답으로 바꿈
            throw new IllegalArgumentException("할당할 내용이 없습니다");
        }

        // 3. 줄마다 검사하면서 품목별·재고별 합계 모으기
        // Map<키, 값> : 이름표(키)로 값을 찾아 쓰는 메모장 / HashMap : Map 의 실제 구현체
        // itemMap·stockMap : 3번에서 조회한 엔티티를 저장해 두고 4·5·6번에서 다시 조회하지 않고 꺼내 씀
        // itemSum·stockSum : 같은 품목 줄·같은 재고가 여러 줄로 나뉘어 와도 합계로 검사하기 위해
        Map<Integer, DocumentItemEntity> itemMap = new HashMap<>();   // documentItemId → 품목 줄
        Map<Integer, StockEntity> stockMap = new HashMap<>();         // stockId → 재고 행
        Map<Integer, Integer> itemSum = new HashMap<>();              // documentItemId → 보낸 수량 합
        Map<Integer, Integer> stockSum = new HashMap<>();             // stockId → 보낸 수량 합

        for (AllocationDto row : rows) {
            // 3-1. 값 비어 있음 / 수량 0 이하 → 400
            if (row.getDocumentItemId() == null || row.getStockId() == null) {
                throw new IllegalArgumentException("품목 줄과 재고를 모두 선택해야 합니다");
            }
            if (row.getQty() == null || row.getQty() <= 0) {
                throw new IllegalArgumentException("수량은 1 이상이어야 합니다");
            }

            // 3-2. 품목 줄 · 재고 조회 → 없으면 404
            // findById : PK 로 한 건 조회 → 결과가 Optional(있을 수도 없을 수도 있는 상자)로 옴
            // orElseThrow(() -> 예외) : 상자가 비어 있으면 그 예외를 던짐, 있으면 엔티티를 꺼내줌
            DocumentItemEntity item = documentItemRepository.findById(row.getDocumentItemId())
                    .orElseThrow(() -> new EntityNotFoundException("품목 줄이 없습니다: " + row.getDocumentItemId()));
            StockEntity stock = stockRepository.findById(row.getStockId())
                    .orElseThrow(() -> new EntityNotFoundException("재고가 없습니다: " + row.getStockId()));

            // 3-3. 이 문서의 품목 줄인지 → 400
            // Integer 끼리는 == 대신 equals 로 비교 (== 는 값이 아니라 객체 주소 비교라 127 넘으면 틀릴 수 있음)
            if (!item.getDocumentEntity().getDocumentId().equals(documentId)) {
                throw new IllegalArgumentException("이 문서의 품목 줄이 아닙니다: " + row.getDocumentItemId());
            }

            // 3-3-1. [ED-61] 고른 재고의 화주가 문서의 화주와 다르면 → 409
            if (!stock.getTenantEntity().getTenantId().equals(documentEntity.getTenantEntity().getTenantId())) {
                throw new IllegalStateException("다른 화주의 재고입니다 · 재고 " + stock.getStockId());
            }

            // 3-4. 출고 가능한 재고인지 (다른 상품 400 / 칸·소비기한 409)
            // 통과하면 아무것도 안 돌려주고(void) 다음 줄로, 실패하면 그 안에서 예외가 던져짐
            allocationPlanService.checkShippable(item, stock, shipDate);

            // 3-5. 합계 모으기
            // put(키, 값) : 키가 없으면 새로 적고, 있으면 새 값으로 덮어씀
            // 같은 품목 줄이 두 번 와도 같은 엔티티라 덮어써도 문제없음
            itemMap.put(item.getDocumentItemId(), item);
            stockMap.put(stock.getStockId(), stock);

            // 품목 줄별 보낸 수량 합계 : 이미 있으면 기존 값에 더하고, 처음이면 0 에서 시작
            // containsKey(키) : 키가 있으면 true / get(키) : 그 키의 값을 꺼냄
            // 예) 품목 줄 21 이 [30개, 20개] 두 줄로 오면
            //     첫 줄 : 키 없음 → itemBefore 0 → put(21, 30)
            //     둘째 줄 : 키 있음 → itemBefore 30 → put(21, 50)
            int itemBefore = 0;
            if (itemSum.containsKey(item.getDocumentItemId())) {
                itemBefore = itemSum.get(item.getDocumentItemId());
            }
            itemSum.put(item.getDocumentItemId(), itemBefore + row.getQty());

            // 재고 행별 보낸 수량 합계 : 위와 같은 방식
            int stockBefore = 0;
            if (stockSum.containsKey(stock.getStockId())) {
                stockBefore = stockSum.get(stock.getStockId());
            }
            stockSum.put(stock.getStockId(), stockBefore + row.getQty());
        }

        // 4. 품목 줄별 검사
        // keySet() : Map 에 적힌 키(이름표)들만 모아서 돌려줌 → 품목 줄 id 를 하나씩 꺼내 검사
        for (Integer itemId : itemSum.keySet()) {
            DocumentItemEntity item = itemMap.get(itemId);
            // 4-1. 이미 할당된 줄 → 409 (더블클릭 방어 : 두 번째 요청은 여기서 막힘)
            if (allocationPlanService.allocatedSum(itemId) > 0) {
                throw new IllegalStateException("이미 할당된 품목입니다: " + item.getProductEntity().getProductName());
            }
            // 4-2. 보낸 합계가 요청 수량과 정확히 같아야 함 → 400
            int sent = itemSum.get(itemId);            // int 로 꺼내서 비교 (Integer 끼리 != 비교 함정 방지)
            int expected = item.getExpectedQty();
            if (sent != expected) {
                throw new IllegalArgumentException(item.getProductEntity().getProductName()
                        + " 수량이 맞지 않습니다 · 요청 " + expected + " · 입력 " + sent);
            }
        }

        // 5. 재고 행별 검사 : 보낸 합계가 가용수량(실물 − 선점)을 넘으면 409
        for (Integer stockId : stockSum.keySet()) {
            StockEntity stock = stockMap.get(stockId);
            int available = stock.getQty() - stock.getAllocatedQty();
            int sent = stockSum.get(stockId);
            if (sent > available) {
                throw new IllegalStateException(stock.getLocationEntity().getLocationCode()
                        + " 가용 수량을 넘습니다 · 입력 " + sent + " · 가용 " + available);
            }
        }

        // 6. 저장 : 할당 내역(detail) + 재고 선점수량 증가
        // 검사(3~5)를 전부 통과한 뒤에만 저장 → 검사 중 실패하면 DB 에 아무것도 안 들어감
        for (AllocationDto row : rows) {
            DocumentItemEntity item = itemMap.get(row.getDocumentItemId());
            StockEntity stock = stockMap.get(row.getStockId());
            documentItemDetailRepository.save(row.toEntity(item, stock)); // DTO → 엔티티로 바꿔서 detail 1줄 저장
            stock.setAllocatedQty(stock.getAllocatedQty() + row.getQty()); // 선점수량 증가 (실물 qty 는 출고확정 때 줄어듦)
            stockRepository.save(stock);
        }

        // 7. 문서 상태 정리 (처음 할당하면 WAITING → ALLOCATED, 전부 할당해도 ALLOCATED 유지)
        updateStatusAfterAllocation(documentEntity);

        // 8. 결과로 피킹리스트 반환
        return getPickingList(documentId);
    }

    // 할당 후 문서 상태 정리
    // 처음 할당하면 접수(WAITING) → 할당(ALLOCATED) , 품목을 전부 할당해도 할당(ALLOCATED) 에 머문다
    // 피킹중(PICKING) 은 피킹 확인 API 에서 작업자가 처음 집는 순간 바뀐다
    // private : 이 클래스 안(createPickingList)에서만 부름
    private void updateStatusAfterAllocation(DocumentEntity documentEntity) {
        // enum 비교는 == 사용 (enum 값은 프로그램에 하나씩만 존재해서 주소 비교로 충분)
        if (documentEntity.getStatus() == DocumentStatus.WAITING) {   // 아직 접수 상태면
            // moveTo : 엔티티에 만든 상태 변경 메서드
            //   안에서 canGoTo 로 "지금 상태 → 바꿀 상태" 가 허용된 이동인지 검사
            //   허용 안 된 이동이면 예외(409), 허용되면 status 값을 바꿈
            documentEntity.moveTo(DocumentStatus.ALLOCATED);         // 할당으로 바꾼다
        }
        documentRepository.save(documentEntity);                     // 바뀐 상태를 DB 에 저장
    }

    // 2. ED-19 피킹리스트 조회

    // ED-19 피킹 리스트 조회 (로케이션 코드순 = 피킹 동선 순서)
    // createPickingList 8번에서도 부르고, 화면(피킹 페이지)에서도 따로 부름
    @Transactional(readOnly = true)
    public List<PickingListDto> getPickingList(Integer documentId) {
        // 없는 문서면 404 (빈 목록 [] 이 200 으로 나가지 않게)
        // findById : PK 로 한 건 조회 → Optional(있을 수도 없을 수도 있는 상자)로 옴
        // orElseThrow : 상자가 비어 있으면 그 예외를 던짐
        documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));
        // stream : 목록을 흐름으로 바꿔 단계별로 처리 (수업에서 배운 방식)
        //   filter(조건)   : 조건이 true 인 것만 남김 → 이 문서의 할당 줄만
        //   sorted((a, b)) : 정렬 → 로케이션 코드 글자 순 (compareTo : a 가 앞이면 음수, 같으면 0, 뒤면 양수)
        //   map(변환)      : 엔티티 하나를 DTO 하나로 바꿈
        //   toList()       : 결과를 다시 List 로 모음
        return documentItemDetailRepository.findAll().stream()
                .filter((detail) -> detail.getDocumentItemEntity().getDocumentEntity().getDocumentId()
                        .equals(documentId))
                .sorted((a, b) -> a.getLocationEntity().getLocationCode()
                        .compareTo(b.getLocationEntity().getLocationCode()))
                .map((detail) -> PickingListDto.from(detail))
                .toList();
    }

    // 3. ED-52 피킹 확인 (집음)

    // ED-52 피킹 확인 : 작업자가 피킹 줄 1개를 집었다고 표시한다
    // 할당됨(ALLOCATED) 줄만 집음(PICKED) 으로 바꿀 수 있다 , 문서의 모든 품목이 할당된 뒤에만 집을 수 있다
    // 문서에서 처음 집는 순간 문서도 할당(ALLOCATED) → 피킹중(PICKING)
    // 상태가 두 종류인 이유
    //   DocumentStatus : 문서 전체 상태 (WAITING → ALLOCATED → PICKING → SHIPPED)
    //   DetailStatus   : 할당 줄 1개 상태 (ALLOCATED → PICKED → SHIPPED)
    public PickingListDto pickDetail(Integer detailId) {

        // 1. 피킹 줄(할당 실적 1줄) 찾기 → 없으면 404
        DocumentItemDetailEntity detail = documentItemDetailRepository.findById(detailId)
                .orElseThrow(() -> new EntityNotFoundException("피킹 줄이 없습니다: " + detailId));

        // 2. 이 줄이 속한 문서 (줄 → 품목 줄 → 문서) 가 출고 문서인지 → 아니면 400
        DocumentEntity documentEntity = detail.getDocumentItemEntity().getDocumentEntity();
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서의 줄이 아닙니다: " + detailId);
        }

        // 2-1. [결정] 문서의 모든 품목이 할당됐는지 → 하나라도 덜 할당됐으면 409
        //      일부만 할당한 채 피킹을 시작하면 남은 품목은 할당·취소를 못 하고 출고도 안 되기 때문
        for (DocumentItemEntity item : allocationPlanService.itemsOf(documentEntity.getDocumentId())) {
            if (allocationPlanService.allocatedSum(item.getDocumentItemId()) < item.getExpectedQty()) {
                throw new IllegalStateException("할당이 끝나지 않은 품목이 있습니다: " + item.getProductEntity().getProductName());
            }
        }

        // 3. 이미 집었거나 출고된 줄이면 409 (같은 줄 두 번 집기 방지)
        if (detail.getStatus() == DetailStatus.PICKED) {
            throw new IllegalStateException("이미 집은 줄입니다: " + detailId);
        }
        if (detail.getStatus() == DetailStatus.SHIPPED) {
            throw new IllegalStateException("이미 출고된 줄입니다: " + detailId);
        }

        // 4. 줄 상태 할당됨(ALLOCATED) → 집음(PICKED)
        // moveTo : 줄 엔티티의 상태 변경 메서드 (문서의 moveTo 와 같은 방식)
        detail.moveTo(DetailStatus.PICKED);
        documentItemDetailRepository.save(detail);

        // 5. 문서에서 처음 집은 거면 문서 할당(ALLOCATED) → 피킹중(PICKING)
        // 이미 피킹중이면 그대로
        if (documentEntity.getStatus() == DocumentStatus.ALLOCATED) {
            documentEntity.moveTo(DocumentStatus.PICKING);
            documentRepository.save(documentEntity);
        }

        // 6. 바뀐 줄 리턴
        return PickingListDto.from(detail);
    }


}