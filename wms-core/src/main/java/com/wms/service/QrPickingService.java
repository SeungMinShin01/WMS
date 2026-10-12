package com.wms.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.outbound.PickingAssignDto;
import com.wms.model.dto.outbound.PickingCreateDto;
import com.wms.model.dto.outbound.PickingDetailDto;
import com.wms.model.dto.outbound.PickingDto;
import com.wms.model.dto.outbound.PickingItemDto;
import com.wms.model.dto.outbound.PickingScanDto;
import com.wms.model.entity.DetailStatus;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.DocumentStatus;
import com.wms.model.entity.DocumentType;
import com.wms.model.entity.PickingListEntity;
import com.wms.model.entity.PickingListItemEntity;
import com.wms.model.entity.PickingStatus;
import com.wms.model.entity.UserEntity;
import com.wms.model.entity.UserRole;
import com.wms.model.repository.DocumentItemDetailRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.repository.PickingListItemRepository;
import com.wms.model.repository.PickingListRepository;
import com.wms.model.repository.UserRepository;
import com.wms.security.ForbiddenException;

import jakarta.persistence.EntityNotFoundException;

// QR 피킹 (ED-75 ~ ED-77)
// 메서드 순서 (위 → 아래 = 실행 흐름)
//   1. ED-75 피킹 리스트 생성 : createPicking  (관리자)
//      └ 번호 만들기 : nextPickingNo
//   2. ED-75 작업자 배정     : assignWorker   (관리자)
//   3. ED-76 내 피킹 리스트  : getMyPickings  (작업자)
//   4. ED-76 전체 피킹 리스트 : getAllPickings (관리자)
//   5. ED-76 피킹 리스트 상세 : getPickingDetail (작업자 본인 것 · 관리자)
//   6. ED-77 스캔(집음)      : scan (작업자 본인 것 · 관리자)
//      └ 본인 리스트인지 검사 : checkWorker (5·6 에서 사용)
@Service
@Transactional
public class QrPickingService {

    @Autowired private AllocationPlanService allocationPlanService;   // 문서 품목 줄·할당 합계 재사용
    @Autowired private PickingListService pickingListService;         // ED-52 pickDetail 재사용 (할당 줄 PICKED)
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemDetailRepository documentItemDetailRepository;
    @Autowired private PickingListRepository pickingListRepository;
    @Autowired private PickingListItemRepository pickingListItemRepository;
    @Autowired private UserRepository userRepository;

    // 1. ED-75 피킹 리스트 생성
    // 할당이 끝난 출고 문서 1개 → 피킹 리스트 1개 + 할당 줄마다 품목 줄 1개
    // 만든 직후 상태는 CREATED (작업자 배정 전)
    public PickingDto createPicking(PickingCreateDto dto) {

        // 1-1. 문서 번호가 비어 있으면 → 400
        if (dto.getDocumentId() == null) {
            throw new IllegalArgumentException("출고 문서를 선택해야 합니다");
        }
        Integer documentId = dto.getDocumentId();

        // 1-2. 문서 조회 → 없으면 404
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("출고 문서가 없습니다: " + documentId));

        // 1-3. 출고 문서가 아니면 → 400
        if (documentEntity.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다: " + documentId);
        }

        // 1-4. 할당 완료(ALLOCATED) 상태가 아니면 → 409
        //      대기(WAITING) : 아직 할당 전 / 피킹중(PICKING) : 이미 ED-52 방식으로 집기 시작함
        //      출고완료·취소 : 끝난 문서
        if (documentEntity.getStatus() != DocumentStatus.ALLOCATED) {
            throw new IllegalStateException("할당이 완료된 문서만 피킹 리스트를 만들 수 있습니다 · 현재 상태 "
                    + documentEntity.getStatus());
        }

        // 1-5. 모든 품목이 요청 수량만큼 할당됐는지 → 하나라도 덜 됐으면 409
        //      일부만 할당한 문서는 상태가 ALLOCATED 여도 나머지 품목이 리스트에 빠지기 때문
        //      (PickingListService.pickDetail 2-1 과 같은 검사)
        for (DocumentItemEntity item : allocationPlanService.itemsOf(documentId)) {
            if (allocationPlanService.allocatedSum(item.getDocumentItemId()) < item.getExpectedQty()) {
                throw new IllegalStateException("할당이 끝나지 않은 품목이 있습니다: "
                        + item.getProductEntity().getProductName());
            }
        }

        // 1-6. 이 문서로 만든 피킹 리스트가 이미 있으면 → 409 (버튼 두 번 누름 방어)
        if (pickingListRepository.existsByDocumentId(documentId)) {
            throw new IllegalStateException("이미 피킹 리스트가 있는 문서입니다: " + documentEntity.getDocumentNo());
        }

        // 1-7. 이 문서의 할당 줄 조회 (품목·상품·LOT·칸·재고를 한 번에 가져옴 , ED-73 join fetch)
        List<DocumentItemDetailEntity> details = documentItemDetailRepository.findByDocumentId(documentId);

        // 1-8. 할당 줄 검사
        //      줄이 하나도 없으면 → 409
        //      ED-52 로 이미 집은(PICKED) 줄이 있으면 → 409 (같은 물건을 두 번 집게 되므로)
        if (details.isEmpty()) {
            throw new IllegalStateException("할당된 줄이 없습니다: " + documentEntity.getDocumentNo());
        }
        for (DocumentItemDetailEntity detail : details) {
            if (detail.getStatus() != DetailStatus.ALLOCATED) {
                throw new IllegalStateException("이미 처리된 줄이 있습니다 · 줄 " + detail.getDetailId()
                        + " 상태 " + detail.getStatus());
            }
        }

        // 1-9. 집는 순서 = 칸 코드 글자 순 (ED-19 피킹리스트 조회와 같은 동선)
        //      sort((a, b)) : a 가 앞이면 음수, 같으면 0, 뒤면 양수를 돌려주는 규칙으로 목록 자체를 정렬
        details.sort((a, b) -> a.getLocationEntity().getLocationCode()
                .compareTo(b.getLocationEntity().getLocationCode()));

        // 1-10. 피킹 리스트 1건 저장 (작업자 없음, 상태 CREATED)
        PickingListEntity pickingList = PickingListEntity.builder()
                .pickingNo(nextPickingNo())
                .documentEntity(documentEntity)
                .status(PickingStatus.CREATED)
                .build();
        try {
            // saveAndFlush : INSERT 를 지금 바로 실행해서 UNIQUE 위반을 여기서 잡음 (WorkerService 와 같은 방식)
            pickingList = pickingListRepository.saveAndFlush(pickingList);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 문서로 두 번 요청 → 둘 다 1-6 을 통과해도 DB 의 document_id UNIQUE 가 막음
            // 같은 순간 다른 문서로 만들어 피킹번호가 겹쳐도 picking_no UNIQUE 가 막음
            throw new IllegalStateException("이미 피킹 리스트가 있거나 번호가 겹쳤습니다. 다시 시도하세요.");
        }

        // 1-11. 할당 줄마다 품목 줄 1개 저장
        //       seq : 1-9 에서 정렬한 순서대로 1, 2, 3 ...
        //       pickedQty : @Builder 는 필드 기본값을 무시하므로 0 을 직접 넣음
        for (int i = 0; i < details.size(); i++) {
            PickingListItemEntity item = PickingListItemEntity.builder()
                    .pickingListEntity(pickingList)
                    .detailEntity(details.get(i))
                    .seq(i + 1)
                    .pickedQty(0)
                    .build();
            pickingListItemRepository.save(item);
        }

        // 1-12. 만든 피킹 리스트 1건 리턴 (품목 줄 수 = 할당 줄 수)
        return PickingDto.from(pickingList, details.size());
    }

    // 1-10 에서 호출 : 피킹번호 만들기
    // PK-오늘날짜-순번 (ex: PK-20261012-001)
    // DocumentNoGenerator 는 document 테이블을 보기 때문에 피킹번호는 picking_list 테이블에서 따로 셈
    private String nextPickingNo() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "PK-" + today + "-";

        Optional<PickingListEntity> last = pickingListRepository.findTopByPickingNoStartingWithOrderByPickingNoDesc(prefix);
        int next = 1;
        if (last.isPresent()) {
            String lastNo = last.get().getPickingNo();                              // PK-20261012-005
            next = Integer.parseInt(lastNo.substring(prefix.length())) + 1;         // 005 -> 6
        }
        return prefix + String.format("%03d", next);                                // 6 -> 006
    }

    // 2. ED-75 작업자 배정
    // CREATED(처음 배정) · ASSIGNED(다른 작업자로 다시 배정) 일 때만 가능
    // 작업자가 스캔을 시작한(PICKING) 뒤에는 바꿀 수 없음
    public PickingDto assignWorker(Integer pickingListId, PickingAssignDto dto) {

        // 2-1. 작업자 번호가 비어 있으면 → 400
        if (dto.getWorkerId() == null) {
            throw new IllegalArgumentException("배정할 작업자를 선택해야 합니다");
        }

        // 2-2. 피킹 리스트 조회 → 없으면 404
        pickingListRepository.findById(pickingListId)
                .orElseThrow(() -> new EntityNotFoundException("피킹 리스트가 없습니다: " + pickingListId));

        // 2-3. 작업자 계정 조회 → 없으면 404
        UserEntity worker = userRepository.findById(dto.getWorkerId())
                .orElseThrow(() -> new EntityNotFoundException("계정이 없습니다: " + dto.getWorkerId()));

        // 2-4. 작업자(WORKER) 계정이 아니면 → 400 (관리자에게는 배정하지 않음)
        if (worker.getRole() != UserRole.WORKER) {
            throw new IllegalArgumentException("작업자 계정에만 배정할 수 있습니다: " + worker.getUserName());
        }

        // 2-5. 조건부 UPDATE : 상태가 CREATED 나 ASSIGNED 일 때만 작업자·상태·배정시각을 바꿈
        //      2-2 에서 조회한 뒤 그 사이 작업자가 첫 스캔을 했다면 상태가 PICKING 이라 0 → 409
        List<PickingStatus> fromList = new ArrayList<>();
        fromList.add(PickingStatus.CREATED);
        fromList.add(PickingStatus.ASSIGNED);
        int changed = pickingListRepository.assign(pickingListId, worker, fromList, PickingStatus.ASSIGNED);
        if (changed == 0) {
            throw new IllegalStateException("이미 피킹을 시작했거나 끝난 리스트라 배정할 수 없습니다: " + pickingListId);
        }

        // 2-6. 바뀐 리스트 리턴
        //      2-5 UPDATE 가 메모리를 비워서(clearAutomatically) 2-2 에서 조회한 엔티티는 옛 값
        //      → 번호로 다시 조회해서 바뀐 작업자·상태로 응답
        PickingListEntity result = pickingListRepository.findById(pickingListId)
                .orElseThrow(() -> new EntityNotFoundException("피킹 리스트가 없습니다: " + pickingListId));
        int itemCount = pickingListItemRepository.countByPickingListId(pickingListId);
        return PickingDto.from(result, itemCount);
    }

    // 3. ED-76 내 피킹 리스트 (작업자 화면 첫 화면)
    // 로그인한 작업자에게 배정된 것 중 아직 할 일이 남은 것만 : 배정됨(ASSIGNED) · 피킹중(PICKING)
    // 다 끝난(DONE) · 취소된(CANCELED) 리스트는 작업자 화면에 띄우지 않음
    @Transactional(readOnly = true)
    public List<PickingDto> getMyPickings(Integer loginUserId) {
        List<PickingStatus> statusList = new ArrayList<>();
        statusList.add(PickingStatus.ASSIGNED);
        statusList.add(PickingStatus.PICKING);

        List<PickingListEntity> lists = pickingListRepository.findByWorkerId(loginUserId, statusList);

        // 리스트마다 품목 줄 수를 세서 DTO 로 바꿈
        List<PickingDto> result = new ArrayList<>();
        for (PickingListEntity pickingList : lists) {
            int itemCount = pickingListItemRepository.countByPickingListId(pickingList.getPickingListId());
            result.add(PickingDto.from(pickingList, itemCount));
        }
        return result;
    }

    // 4. ED-76 전체 피킹 리스트 (관리자 화면)
    // 상태 상관없이 전부, 최근 것부터 → 배정 전(CREATED) 리스트를 찾아서 배정 버튼을 누르는 화면
    @Transactional(readOnly = true)
    public List<PickingDto> getAllPickings() {
        List<PickingListEntity> lists = pickingListRepository.findAllWithDocument();

        List<PickingDto> result = new ArrayList<>();
        for (PickingListEntity pickingList : lists) {
            int itemCount = pickingListItemRepository.countByPickingListId(pickingList.getPickingListId());
            result.add(PickingDto.from(pickingList, itemCount));
        }
        return result;
    }

    // 5. ED-76 피킹 리스트 상세 (머리 정보 + 품목 줄 목록)
    // 작업자는 본인에게 배정된 리스트만, 관리자는 전부 볼 수 있음
    @Transactional(readOnly = true)
    public PickingDetailDto getPickingDetail(Integer pickingListId, Integer loginUserId) {

        // 5-1. 피킹 리스트 조회 → 없으면 404
        PickingListEntity pickingList = pickingListRepository.findById(pickingListId)
                .orElseThrow(() -> new EntityNotFoundException("피킹 리스트가 없습니다: " + pickingListId));

        // 5-2. 본인 리스트인지 검사 → 아니면 403
        checkWorker(pickingList, loginUserId);

        // 5-3. 품목 줄 목록 (집는 순서대로, 칸·상품·LOT 를 한 번에 가져옴)
        List<PickingListItemEntity> items = pickingListItemRepository.findByPickingListId(pickingListId);

        // 5-4. 품목 줄 엔티티 → DTO (getPickingList 와 같은 stream 방식)
        List<PickingItemDto> itemDtos = items.stream()
                .map((item) -> PickingItemDto.from(item))
                .toList();

        // 5-5. 머리 정보 + 줄 목록을 묶어서 리턴
        return PickingDetailDto.builder()
                .picking(PickingDto.from(pickingList, items.size()))
                .items(itemDtos)
                .build();
    }

    // 6. ED-77 스캔 (집음)
    // 작업자가 칸에 가서 물건의 LOT QR 을 찍고 수량을 보냄 → 그 줄의 집은 수량(pickedQty)을 올림
    // 한 줄이 다 차면 할당 줄도 집음(PICKED), 모든 줄이 다 차면 리스트 완료(DONE)
    public PickingItemDto scan(Integer pickingListId, PickingScanDto dto, Integer loginUserId) {

        // 6-1. 값 검사 → 400
        if (dto.getPickingListItemId() == null || dto.getLotCode() == null) {
            throw new IllegalArgumentException("품목 줄과 LOT 를 모두 스캔해야 합니다");
        }
        if (dto.getQty() == null || dto.getQty() <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다");
        }
        Integer itemId = dto.getPickingListItemId();

        // 6-2. 피킹 리스트 조회 → 없으면 404 / 본인 리스트가 아니면 403
        PickingListEntity pickingList = pickingListRepository.findById(pickingListId)
                .orElseThrow(() -> new EntityNotFoundException("피킹 리스트가 없습니다: " + pickingListId));
        checkWorker(pickingList, loginUserId);

        // 6-3. 배정됨(ASSIGNED) · 피킹중(PICKING) 일 때만 스캔 가능 → 아니면 409
        //      CREATED : 아직 배정 전 / DONE : 이미 다 집음 / CANCELED : 출고 취소됨
        if (pickingList.getStatus() != PickingStatus.ASSIGNED && pickingList.getStatus() != PickingStatus.PICKING) {
            throw new IllegalStateException("스캔할 수 없는 피킹 리스트입니다 · 현재 상태 " + pickingList.getStatus());
        }

        // 6-4. 품목 줄 조회 → 없으면 404 / 이 리스트의 줄이 아니면 400
        PickingListItemEntity item = pickingListItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("품목 줄이 없습니다: " + itemId));
        if (!item.getPickingListEntity().getPickingListId().equals(pickingListId)) {
            throw new IllegalArgumentException("이 피킹 리스트의 줄이 아닙니다: " + itemId);
        }

        // 6-5. 찍은 LOT 가 집어야 할 LOT 와 같은지 → 다르면 400 (다른 물건을 집은 것)
        //      아래 UPDATE 들이 메모리를 비우므로(clearAutomatically) 필요한 값은 지금 꺼내 둠
        DocumentItemDetailEntity detail = item.getDetailEntity();
        Integer detailId = detail.getDetailId();
        int maxQty = detail.getQty();                                       // 이 줄에서 집어야 할 수량
        Integer documentId = pickingList.getDocumentEntity().getDocumentId();
        String lotCode = detail.getLotEntity().getLotCode();
        if (!lotCode.equals(dto.getLotCode())) {
            throw new IllegalArgumentException("다른 LOT 입니다 · 집어야 할 LOT " + lotCode + " · 찍은 LOT " + dto.getLotCode());
        }

        // 6-6. 첫 스캔이면 리스트 배정됨(ASSIGNED) → 피킹중(PICKING) + 시작 시각
        //      문서도 할당(ALLOCATED) → 피킹중(PICKING) : 이때부터 주문 취소가 막힘
        //      둘 다 결과가 0 이면 이미 바뀐 것 → 에러 아님, 그대로 진행
        if (pickingList.getStatus() == PickingStatus.ASSIGNED) {
            pickingListRepository.start(pickingListId, PickingStatus.ASSIGNED, PickingStatus.PICKING);
            documentRepository.changeStatus(documentId, DocumentStatus.ALLOCATED, DocumentStatus.PICKING);
        }

        // 6-7. 집은 수량 더하기 (조건부 UPDATE)
        //      더한 값이 할당 수량을 넘으면 0 → 409
        //      같은 줄을 두 사람이 동시에 찍어도 DB 가 한 줄씩 처리해서 할당 수량을 넘지 않음
        int added = pickingListItemRepository.addPickedQty(itemId, dto.getQty(), maxQty);
        if (added == 0) {
            throw new IllegalStateException("집어야 할 수량을 넘습니다 · 할당 수량 " + maxQty);
        }

        // 6-8. 다시 조회해서 이 줄이 다 찼는지 확인
        //      다 찼으면 → 다 집은 시각 기록 + 할당 줄 할당됨(ALLOCATED) → 집음(PICKED) (ED-52 pickDetail 재사용)
        PickingListItemEntity updated = pickingListItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("품목 줄이 없습니다: " + itemId));
        int picked = updated.getPickedQty();
        if (picked == maxQty) {
            pickingListItemRepository.markPicked(itemId);
            pickingListService.pickDetail(detailId);
        }

        // 6-9. 아직 덜 집은 줄이 하나도 없으면 → 리스트 피킹중(PICKING) → 완료(DONE) + 완료 시각
        //      이후 관리자가 기존 출고확정(ED-20)을 누르면 출고 끝
        if (pickingListItemRepository.countNotDone(pickingListId) == 0) {
            pickingListRepository.complete(pickingListId, PickingStatus.PICKING, PickingStatus.DONE);
        }

        // 6-10. 바뀐 줄 리턴 (6-8 이후 UPDATE 가 또 메모리를 비웠으므로 다시 조회)
        PickingListItemEntity result = pickingListItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("품목 줄이 없습니다: " + itemId));
        return PickingItemDto.from(result);
    }

    // 5-2 · 6-2 에서 호출 : 로그인한 사람이 이 리스트를 볼 수 있는지
    // 관리자(ADMIN) → 통과 / 작업자 → 배정된 작업자 본인일 때만 통과, 아니면 403
    private void checkWorker(PickingListEntity pickingList, Integer loginUserId) {
        // 로그인한 계정의 역할을 DB 에서 확인
        UserEntity loginUser = userRepository.findById(loginUserId)
                .orElseThrow(() -> new EntityNotFoundException("계정이 없습니다: " + loginUserId));
        if (loginUser.getRole() == UserRole.ADMIN) {
            return;   // 관리자는 검사 없이 통과
        }

        // 아직 배정 전(작업자 null)이거나, 배정된 작업자 번호가 내 번호와 다르면 → 403
        // Integer 끼리는 == 대신 equals 로 비교
        if (pickingList.getWorkerEntity() == null
                || !pickingList.getWorkerEntity().getUserId().equals(loginUserId)) {
            throw new ForbiddenException("본인에게 배정된 피킹 리스트가 아닙니다: " + pickingList.getPickingNo());
        }
    }
}