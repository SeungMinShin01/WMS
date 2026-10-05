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
        // documentEntity = 통과된 문서 , items = 통과된 문서의 문서품목들
        // 로케이션 코드 순으로 정렬 (피킹 동선 순서) : a 와 b 의 로케이션 코드를 글자 순으로 비교
        result.sort((a, b) -> a.getLocationCode().compareTo(b.getLocationCode()));
        return result;
    }

    // 2. 검증 메서드 (PickingListService 도 같이 씀)

    // 문서 검사 : 없는 문서 404 / 출고 문서 아님 400 / 대기·할당 상태 아님 409
    // WAITING(아직 하나도 할당 안 함), ALLOCATED(일부만 할당함) 에서만 할당 가능
    // 이미 PICKING 으로 넘어간 문서는 다시 못 함 → "더블클릭 방어" 역할도 함
    public DocumentEntity checkAllocatable(Integer documentId) {
        DocumentEntity documentEntity = documentRepository.findById(documentId) // documentId 조회 
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId)); // 없을시 orElseThrow
        if (documentEntity.getType() != DocumentType.OUTBOUND) { // enum(클래스값.값이름) 문서 상태로 사용가능
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId); // 
        }
        DocumentStatus status = documentEntity.getStatus();
        if (status != DocumentStatus.WAITING && status != DocumentStatus.ALLOCATED) { // 접수랑 일부할당만 통과
            throw new IllegalStateException("대기·할당 상태에서만 할당할 수 있습니다. 현재 상태: " + status); 
        }   
        return documentEntity;
    }

    // 이 문서의 품목 줄 전체 (document_item 중 document_id 가 같은 것)
    // 문서 품목 테이블 전체를 돌면서 문서에 해당하는 품목인지 확인
    public List<DocumentItemEntity> itemsOf(Integer documentId) {
        List<DocumentItemEntity> items = new ArrayList<>();
        for (DocumentItemEntity item : documentItemRepository.findAll()) {
            if (item.getDocumentEntity().getDocumentId().equals(documentId)) {
                items.add(item);
            }
        }
        return items; // 문서 번호에 해당하는 문서 품목만 담아서 리턴  
    }
    // 이미 할당된 품목 중복 방지 검사 (예: 내 화면은 할당 전인데 다른 사람이 먼저 할당한 경우, 새로고침 안 한 화면에서 다시 누른 경우)
    // 품목 줄 1개에 지금까지 할당된 수량 합계를 계산 (document_item_detail 의 qty 합)
    // 추천 받기 / 피킹리스트 생성 : 0 보다 크면 이미 할당된 품목이라 409 (중복 할당 방지)
    // 문서 상태 정리 : 요청 수량보다 작으면 아직 덜 할당된 품목
    // 출고 문서 상세 조회 : 화면의 "할당 수량" 으로 보여줌
    public int allocatedSum(Integer documentItemId) { // 사용자가 체크한 품목의 품목 id를 파라미터로 받아옴
        int sum = 0; // 합계를 담을 변수. 0에서 시작
        for (DocumentItemDetailEntity d : documentItemDetailRepository.findAll()) { // 할당 줄 전체를 하나씩 꺼내서
            if (d.getDocumentItemEntity().getDocumentItemId().equals(documentItemId)) { // 체크한 품목의 품목 id
                sum += d.getQty(); // 할당된 수량을 더함 (출고쪽이니까 할당 , 입고쪽이면 검수)
                // 추천 받기 / 피킹리스트 생성 : 0보다 큰경우(비정상) 중복할당
                
            }
        }
        return sum; // 합계 리턴
    }
    // outboundservice ED-17 출고 문서 상세 조회에서 호출
    // 이 품목 줄의 "출고 가능 재고" 합계 (주문 품목 화면에 보여줄 값)
    // 추천 계산(buildPlan)과 같은 조건 : 같은 상품 · 운영 중인 칸 · 소비기한 있음 · 잔여일 충분 · 가용 > 0
    public int shippableQty(DocumentItemEntity item, LocalDate shipDate, List<StockEntity> allStocks) { 
        // 품목한개 , 출고 예정일, 재고 전체를 파라미터로 받아옴
        int sum = 0;
        for (StockEntity s : allStocks) {
            // 밑에 isShippable로 가서 재고가 이 품목 줄에 출고 가능한지 검사
            if (!isShippable(item, s, shipDate)) continue; 

            // [ED-61] 문서 화주와 다른 화주의 재고는 출고 가능 재고에서 뺀다 (추천 후보와 같은 기준)
            if (!s.getTenantEntity().getTenantId().equals(item.getDocumentEntity().getTenantEntity().getTenantId())) continue;
            
            int available = s.getQty() - s.getAllocatedQty(); // 출고 가능시 가용재고 개수 저장 가용 = 실물 − 선점
            if (available > 0) sum += available;
        }
        return sum;
    }
    
    // 재고 1행이 이 품목 줄에 출고 가능한지 true/false 로만 판단 (예외 안 던짐)
    private boolean isShippable(DocumentItemEntity item, StockEntity s, LocalDate shipDate) {
        // // item : 해당 문서의 문서 품목중하나 , s : 모든 재고 중 for문을 통해 랜덤으로 꺼내온 재고 , shipDate : 문서의 출고 예정일 

        // 재고가 속한 로트를 꺼내고 그 로트의 품목을 꺼내고 그 품목의 id랑 문서품목의 품목id를 비교해서 
        if (!s.getLotEntity().getProductEntity().getProductId().equals(item.getProductEntity().getProductId())) return false; 
        // 같지 않으면 재고 상품과 주문 상품이 다르므로 false

        // 재고가 속한 칸이 사용중이지 않으면 
        if (!s.getLocationEntity().getIsActive()) return false;   
        // 안 쓰는 칸이므로 false 

        // 재고의 소비기한
        LocalDate expiry = s.getLotEntity().getExpiryDate();

        if (expiry == null) return false; // 소비기한이 없으면 false      
        int minShipDays = item.getProductEntity().getMinShipDays(); // 품목의 출고 허용 잔여일
        // plusDays(n) = 지정한 일수(days)를 더해 새로운 날짜 객체를 반환
        // ex) shipDate = 2026-10-05, minShipDays = 30 / shipDate.plusDays(30) → 2026-11-04
        LocalDate limitDate = shipDate.plusDays(minShipDays);   // 출고예정일 + 출고 허용 잔여일 / 소비기한이 이 날짜와 같거나 이후여야 함
        // isBefore = 비교하는 대상보다 앞선 시간(과거)이면 true, 아니면 false를 반환
        // ex) 2026-10-25 .isBefore(2026-11-04) 이면 true 
        // 출고하려면 expiry(소비기한일)가 limitDate(출고예정일 + 출고 허용 잔여일)와 같거나 이후여야 함
        if (expiry.isBefore(limitDate)) return false; // 소비기한이 기준일보다 빠름 (잔여일 부족)
        return true; 
    }

    // pickingListService에서 호출
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
        int minShipDays = item.getProductEntity().getMinShipDays();
        LocalDate limitDate = shipDate.plusDays(minShipDays);   // 출고예정일 + 최소 잔여일
        if (expiry.isBefore(limitDate)) {                       // 소비기한이 그 날짜보다 빠르면 잔여일 부족
            throw new IllegalStateException("소비기한 잔여일이 부족합니다 · 재고 " + stock.getStockId()
                    + " · 소비기한 " + expiry + " · 필요 " + limitDate + " 이후 (최소 " + minShipDays + "일)");
        }
    }

    // 3. 내부 계산

    // 사용자가 체크한 품목 줄만 골라냄
    // 선택 없음 → 전체 / 이 문서에 없는 id 400 / 이미 할당된 줄 409
    private List<DocumentItemEntity> selectItems(Integer documentId, List<Integer> documentItemIds) {
        // List<Integer> documentItemIds = 사용자가 체크한 품목들의 번호
        List<DocumentItemEntity> all = itemsOf(documentId); // all = 문서번호에 해당하는 문서 품목만 담아져있음
        if (all.isEmpty()) { // isEmpty() 목록에 들어 있는게 하나도 없으면 true 아닐시 false
            throw new IllegalStateException("품목이 없는 문서입니다: " + documentId); // 문서 품목이 없을시(할당할게없음) 예외 발생
        }

        List<DocumentItemEntity> selected = new ArrayList<>(); // 골라낸 품목 줄을 담을 빈 목록
        if (documentItemIds == null || documentItemIds.isEmpty()) { // 체크한 품목 목록이 없거나(null) 왔는데 비어있으면(isEmpty)
            selected.addAll(all); // addAll = "선택"이 없으면 all안의 문서 품목 전체를 selected에 넣음          
        } else { // 체크한 품목이 하나 이상 왔을 때 [21, 22]
            List<Integer> doneIds = new ArrayList<>(); // 이미 처리한 id를 담아둘 리스트        
            for (Integer id : documentItemIds) { // 체크한 품목번호를 하나씩 꺼내서 id에 대입
                // contains = 특정 값이 포함되어 있는지 검사함. 값이 있으면 true 없을시 fasle
                if (doneIds.contains(id)) continue; // doneIds안에 품목번호가 있는지 검사함 있을시 다음 id로 넘어감
                doneIds.add(id); // 없을시 리스트에 추가함 
                // [21, 21] 같은 품목이 여러번 담기는걸 방지

                DocumentItemEntity found = null; // 번호에 맞는 품목을 찾으면 담을 변수. 처음에는 null
                for (DocumentItemEntity item : all) { // itme에 문서 품목 행 전체가 들어감
                    if (item.getDocumentItemId().equals(id)) { // 품목id를 비교
                        found = item; // 같을시 found에 넣음
                        break; // for문 종료
                    }
                }
                if (found == null) { // 위 for문에서 found에 품목이 담기지 못했을 경우 null
                    throw new IllegalArgumentException("이 문서의 품목 줄이 아닙니다: " + id);
                }
                selected.add(found); // 품목을 담아서 리스트에 더함
            }
        }

        for (DocumentItemEntity item : selected) {
            if (allocatedSum(item.getDocumentItemId()) > 0) { // 0보다 큰 경우 할당이 확정된 품목
                throw new IllegalStateException("이미 할당된 품목입니다: " + item.getProductEntity().getProductName());
            }
        }
        return selected; // 품목들 반환
    }

    // 재고 행의 "계획 반영 가용수량" = 실물 − 선점 − 이번 계획에서 이미 쓴 수량
    private int availableOf(StockEntity s, Map<Integer, Integer> planned) {
        int used = 0;  // 이번 계획에서 이 재고를 이미 쓴 수량 (없으면 0)
        if (planned.containsKey(s.getStockId())) { // containsKey = Map 안에 키가 들어 있는지 검사 있으면 true 아닐시 false
            // planned.containsKey(10)
            // planned 에 재고번호가 있다  → 이번 계산에서 앞 품목이 이미 쓴 적 있다 → 그만큼 빼야 한다
            // planned 에 재고번호가 없다  → 이번 계산에서 아직 안 썼다           → 뺄 게 없다 (0)
            used = planned.get(s.getStockId()); 
            // 재고 id가 10이면 키값이 10이 되고 10의 값을 get 해서 user에 10의 값이 들어감 
        }
        return s.getQty() - s.getAllocatedQty() - used; // 실물 - 선점 = 원래 가용수량 그대로
    }



    // 추천 계산 (DB 저장 X) : 품목 줄마다 재고 행과 수량을 정해 목록으로 돌려줌
    // [LOT 정하기] 1 소비기한 빠른 순 → 2-1 LOT 가용 합계 많은 순 → 2-2 LOT 첫 적재일 빠른 순 → 2-3 lotId
    // [칸 정하기]  3-1 칸 가용수량 많은 순 → 3-2 칸 적재일 빠른 순 → 3-3 stockId
    private List<AllocationPreviewDto> buildPlan(DocumentEntity documentEntity, List<DocumentItemEntity> items) {
        // documentEntity = 통과된 문서 , items = 통과된 문서의 문서품목들
        LocalDate shipDate = documentEntity.getExpectedAt().toLocalDate();   // 잔여일 계산 기준일 (출고 예정일)

        List<StockEntity> allStocks = stockRepository.findAll();   // 재고 전체 (품목마다 재사용)
        // Map = 이름표(key) → 값(value)
        Map<Integer, Integer> planned = new HashMap<>();           // stockId → 이번 계획에서 이미 쓴 수량
        List<AllocationPreviewDto> plan = new ArrayList<>();       // 결과 (추천 1줄 = DTO 1개)

        for (DocumentItemEntity item : items) {
            int need = item.getExpectedQty();  // getExpectedQty = 예정(채워야할)수량                         

            // (1) 자격 조건으로 후보 거르기 (같은 상품 · 운영 칸 · 소비기한 · 잔여일 + 꺼낼 수량 있음)
            List<StockEntity> candidates = new ArrayList<>();
            for (StockEntity s : allStocks) {
                // 재고 1행이 이 품목 줄에 출고 가능한지 검사
                if (!isShippable(item, s, shipDate)) continue; // 출고불가일시(fasle를 받아오면) continue;

                // [ED-61] 안전장치 : 재고의 화주가 문서의 화주와 다르면 후보에서 제외
                // (같은 품목이면 화주도 같아야 정상이지만, 데이터가 잘못 들어간 경우를 막는다)
                if (!s.getTenantEntity().getTenantId().equals(documentEntity.getTenantEntity().getTenantId())) continue;

                if (availableOf(s, planned) <= 0) continue; // 0보다 작거나 같으면 꺼낼 수량이 없어서 후보제외
                candidates.add(s); // 품목 1개마다 전체 재고를 돌면서 이품목에 꺼낼 수 있는 재고만 더함
            }

            // (2) 후보 가용 합계가 필요량보다 적으면 409
            int totalAvailable = 0; // 후보 가용 합계
            for (StockEntity s : candidates) { //  candidates = 이품목에 꺼낼 수 있는 재고
                // availableOf(s, planned) = 이 재고에서 더 꺼낼 수 있는 수량 (실물 - 선점 - planned 에서 이미 쓴 양)
                totalAvailable += availableOf(s, planned);
                // for문이 끝나면 totalAvailable = 후보 재고들의 꺼낼 수 있는 수량을 전부 더한 값
            }
            if (totalAvailable < need) { // need : 이 품목에서 채워야 할 수량
                // 후보 재고를 다 합쳐도 필요한 수량보다 적으면 예외 던지기
                throw new IllegalStateException(item.getProductEntity().getProductName() 
                        + " 가용 부족 · 필요 " + need + " · 가용 " + totalAvailable);
            }

            // (3) LOT 단위 값 (lotTotal : LOT별 가용 합계 / lotFirstIn : LOT별 첫 적재일)
            // 한 LOT 가 창고 여러 칸에 나뉘어 있을 수 있다 (재고 행이 여러 개)
            // 같은 LOT 에 속한 재고들의 가용 수량을 더함
            Map<Integer, Integer> lotTotal = new HashMap<>();
            // LOT번호 → 그 LOT 에서 가장 이른 적재 시각 을 적어두는 메모장
            // 같은 LOT 의 재고가 여러 개일 수 있으니, 그중 가장 먼저 적재된 시각만 남김
            Map<Integer, LocalDateTime> lotFirstIn = new HashMap<>();
            for (StockEntity s : candidates) { //  candidates = 이품목에 꺼낼 수 있는 재고
                Integer lotId = s.getLotEntity().getLotId(); // 품목의 로트id를 꺼냄
                int before = 0;  // 지금까지 모인 이 LOT 의 가용 합계 (처음이면 0)
                if (lotTotal.containsKey(lotId)) {
                    before = lotTotal.get(lotId);
                }
                lotTotal.put(lotId, before + availableOf(s, planned));
                // lotTotal.put(재고의 LOT번호, 실제 가용수량);
                // 첫 재고에서는 그 재고의 가용수량이 적히고, 같은 LOT의 재고가 또 나오면 더해져서 커진다
                LocalDateTime in = s.getCreatedAt(); // 재고가 적재 완료된 날짜
                // 이 LOT 가 처음 나왔거나, 이번 재고가 지금까지 적힌 시각보다 더 일찍 적재됐으면 true
                if (!lotFirstIn.containsKey(lotId) || in.isBefore(lotFirstIn.get(lotId))) {
                    // 이번 재고의 적재 시각
                    // 칸이 없으면 새로 만들어 적고 이미 있으면 더 이른 시각으로 덮어씀
                    // 결과적으로 칸에는 항상 그 LOT 에서 가장 이른 적재 시각만 남는다
                    lotFirstIn.put(lotId, in); 
                    // Map.put(키, 값) : Map 에 "키 → 값" 한 줄을 적는다 (등록)
                    //   - 그 키가 Map 에 없으면 → 새 칸을 만들어서 값을 적는다
                    //   - 그 키가 이미 있으면   → 그 칸의 값을 새 값으로 덮어쓴다
                }
            }

            // (4) 정렬 (LOT 기준 전부 → 칸 기준 전부)
            //     a 가 앞이면 음수, b 가 앞이면 양수, 같으면 0 을 돌려줌 → 0 이면 다음 기준으로 비교
            //     "많은 순" 은 a 와 b 를 바꿔서 비교 (큰 값이 앞으로)

            //  A.compareTo(B) : A 와 B 를 비교해서 숫자 하나를 돌려준다
            //   A 가 B 보다 작거나 이르면 → 음수
            //   A 와 B 가 같으면          → 0
            //   A 가 B 보다 크거나 늦으면 → 양수
                candidates.sort((a, b) -> {
                // 두 재고가 각각 속한 LOT 번호를 미리 꺼내 둔다
                Integer lotA = a.getLotEntity().getLotId();
                Integer lotB = b.getLotEntity().getLotId();
                
                
                // 1 소비기한 빠른 순
                // 0 이 아니면 소비기한으로 순서가 정해졌다는 뜻 → 그 숫자를 돌려주고 비교를 끝낸다
                // 0 이면 소비기한이 같은 것 → 아래 기준으로 넘어간다
                int result = a.getLotEntity().getExpiryDate().compareTo(b.getLotEntity().getExpiryDate());
                if (result != 0) return result;

                // 2-1 LOT 가용 합계 많은 순 (b 와 a 를 바꿔서 비교)
                // (3)번에서 만든 lotTotal 메모장에서 각 LOT 의 가용 합계를 꺼낸다
                result = lotTotal.get(lotB).compareTo(lotTotal.get(lotA));
                if (result != 0) return result;
                // 같은 소비기한이면 한 LOT 에 재고가 많이 몰려 있는 쪽부터 사용

                // 2-2 LOT 첫 적재일 빠른 순
                // (3)번에서 만든 lotFirstIn 메모장에서 각 LOT 의 가장 이른 적재 시각을 꺼내 비교
                // 더 일찍 적재된 LOT 가 앞에 온다
                result = lotFirstIn.get(lotA).compareTo(lotFirstIn.get(lotB));
                if (result != 0) return result;

                // 2-3 lotId 작은 순
                // 위 기준이 전부 같을 때 LOT 번호가 작은 쪽을 앞에 둔다
                result = lotA.compareTo(lotB);
                if (result != 0) return result;

                // 3-1 칸 가용수량 많은 순 (b 와 a 를 바꿔서 비교)
                // availableOf 는 int(기본형 숫자)를 돌려줘서 .compareTo 를 쓸 수 없으므로
                // 숫자 두 개를 비교하는 Integer.compare(x, y) 를 사용 (x 가 작으면 음수, 같으면 0, 크면 양수)
                result = Integer.compare(availableOf(b, planned), availableOf(a, planned));
                if (result != 0) return result;

                // 3-2 칸 적재일 빠른 순
                // 각 재고 행이 만들어진 시각을 비교해 더 이른 쪽이 앞에 온다
                result = a.getCreatedAt().compareTo(b.getCreatedAt());
                if (result != 0) return result;

                // 3-3 stockId 작은 순
                // 마지막까지 같으면 재고 번호가 작은 쪽을 앞에 둔다.
                return a.getStockId().compareTo(b.getStockId());
            });

            // (5) 정렬 순서대로 필요한 만큼 담기 (저장 X, planned 에만 기록)
            for (StockEntity s : candidates) {
                if (need == 0) break;   // need : 아직 더 채워야 할 수량
                int take = Math.min(need, availableOf(s, planned)); 
                // take : 이 재고에서 실제로 가져갈 수량
                // 아직 필요한 수량(need) 과 이 재고에서 꺼낼 수 있는 수량 중 작은 쪽만 가져간다
                // 재고가 넉넉하면 필요한 만큼(need)만, 재고가 모자라면 있는 만큼만 가져간다

                plan.add(AllocationPreviewDto.from(item, s, take)); 
                // from(item, s, take) : 품목(item) + 재고(s) + 수량(take) 정보를 담은 추천 1줄(DTO)을 생성

                // 이 재고를 이번 계획에서 쓴 수량 누적 → 다음 품목이 같은 재고를 쓸 때 가용에서 빠짐
                int used = 0;
                // 이 재고를 이번 계산에서 이미 쓴 수량
                if (planned.containsKey(s.getStockId())) {
                    used = planned.get(s.getStockId());
                }
                planned.put(s.getStockId(), used + take);
                need -= take;
                // 남은 필요량에서 가져간 만큼 뺀다 (need = need - take)
                // need 가 0 이 되면 다음 바퀴 시작에서 break 로 빠져나감                  
            }
        }
        return plan; // 모아 둔 추천 목록(plan) 리턴
    }
}