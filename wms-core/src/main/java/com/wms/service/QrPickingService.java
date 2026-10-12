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
import com.wms.model.dto.outbound.PickingDto;
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

import jakarta.persistence.EntityNotFoundException;

// QR 피킹 (ED-75 ~ ED-77)
// 메서드 순서 (위 → 아래 = 실행 흐름)
//   1. ED-75 피킹 리스트 생성 : createPicking  (관리자)
//      └ 번호 만들기 : nextPickingNo
//   2. ED-75 작업자 배정     : assignWorker   (관리자)
@Service
@Transactional
public class QrPickingService {

    @Autowired private AllocationPlanService allocationPlanService;   // 문서 품목 줄·할당 합계 재사용
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
        int itemCount = pickingListItemRepository.findByPickingListId(pickingListId).size();
        return PickingDto.from(result, itemCount);
    }
}