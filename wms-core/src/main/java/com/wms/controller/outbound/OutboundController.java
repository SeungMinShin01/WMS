package com.wms.controller.outbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.outbound.AllocationDto;
import com.wms.model.dto.outbound.AllocationPreviewDto;
import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.model.dto.outbound.PickingListDto;
import com.wms.service.AllocationPlanService;
import com.wms.service.OutboundService;
import com.wms.service.PickingListService;

// 출고 API
// 메서드 순서 : 목록(ED-12) → 상세(ED-17) → 할당 미리보기(ED-18) → 피킹리스트 생성(ED-18)
//              → 피킹리스트 조회(ED-19) → 피킹 확인(ED-52) → 출고확정(ED-20) → 주문 취소
// 서비스에서 던진 예외는 GlobalExceptionHandler 가 받아서 400 / 404 / 409 응답으로 바꿈
@RestController
public class OutboundController {
    @Autowired
    private OutboundService outboundService; // 목록·상세 조회 · 출고확정 · 주문 취소
    @Autowired
    private AllocationPlanService allocationPlanService; // 할당 미리보기
    @Autowired
    private PickingListService pickingListService; // 피킹리스트 생성 · 조회 · 피킹 확인

    // ED-12 출고 문서 목록 조회
    // ED-61 화주 필터 : GET /wms/outbounds?tenantId=2 (생략하면 전체)
    // required = false : 없어도 됨 → 안 보내면 tenantId 에 null 이 들어감
    // ResponseEntity.ok(값) : 상태코드 200(성공) + 본문에 값을 담아 응답
    @GetMapping("/wms/outbounds")
    public ResponseEntity<List<OutboundListDto>> getOutboundList(
            @RequestParam(name = "tenantId", required = false) Integer tenantId) {
        return ResponseEntity.ok(outboundService.getOutboundList(tenantId));
    }

    // ED-17 출고 문서 상세 조회
    // @PathVariable : 주소 경로 안의 {documentId} 자리 값을 받음
    //   예) /wms/outbounds/10 → documentId = 10
    @GetMapping("/wms/outbounds/{documentId}")
    public ResponseEntity<OutboundDetailDto> getOutboundDetail(@PathVariable(name = "documentId") Integer documentId) {
        return ResponseEntity.ok(outboundService.getOutboundDetail(documentId));
    }

    // ED-18 할당 미리보기 (추천만, 저장 안 함) → 200
    // 예 : GET /wms/allocations/10/preview?documentItemIds=21,22 (documentItemIds 생략시 전체 품목)
    // documentItemIds=21,22 처럼 쉼표로 보내면 스프링이 List<Integer> [21, 22] 로 바꿔서 넣어줌
    // 저장을 안 하는 조회라서 GET 사용
    @GetMapping("/wms/allocations/{documentId}/preview")
    public ResponseEntity<List<AllocationPreviewDto>> previewAllocate(
            @PathVariable(name = "documentId") Integer documentId,
            @RequestParam(name = "documentItemIds", required = false) List<Integer> documentItemIds) {
        return ResponseEntity.ok(allocationPlanService.previewAllocate(documentId, documentItemIds));
    }

    // ED-18 피킹리스트 생성 (할당 확정) → 201 + 피킹리스트
    // 본문 : [ {"documentItemId":21, "stockId":10, "qty":50}, ... ] (미리보기 결과를 사용자가 수정한 값)
    // ResponseEntity.status(HttpStatus.CREATED).body(값)
    //   : 상태코드 201(새로 만들어짐) + 본문에 값을 담아 응답
    //   ok() 는 200 만 되므로, 다른 상태코드를 쓸 때는 status() 로 정하고 body() 로 본문을 넣음
    @PostMapping("/wms/allocations/{documentId}/pickinglist")
    public ResponseEntity<List<PickingListDto>> createPickingList(
            @PathVariable(name = "documentId") Integer documentId,
            @RequestBody List<AllocationDto> rows) {
        List<PickingListDto> pickingList = pickingListService.createPickingList(documentId, rows);
        return ResponseEntity.status(HttpStatus.CREATED).body(pickingList);
    }

    // ED-19 피킹 리스트 조회
    @GetMapping("/wms/allocations/{documentId}")
    public ResponseEntity<List<PickingListDto>> getPickingList(@PathVariable(name = "documentId") Integer documentId) {
        return ResponseEntity.ok(pickingListService.getPickingList(documentId));
    }

    // ED-52 피킹 확인 (줄 1개 집음) → 200 + 그 줄
    // 예 : PUT /wms/pickings/128
    // @PutMapping : PUT 요청(이미 있는 것 수정)을 이 메서드와 연결 → 줄 상태만 바꾸므로 PUT
    @PutMapping("/wms/pickings/{detailId}")
    public ResponseEntity<PickingListDto> pickDetail(@PathVariable(name = "detailId") Integer detailId) {
        return ResponseEntity.ok(pickingListService.pickDetail(detailId));
    }

    // ED-20 출고확정 (문서 단위) → 200 + 바뀐 상태 "SHIPPED" / 이미 출고됨 → 409
    // 문서 상태와 재고 수량을 바꾸는 수정이라 PUT
    @PutMapping("/wms/outbounds/{documentId}/ship")
    public ResponseEntity<String> confirmShipment(@PathVariable(name = "documentId") Integer documentId) {
        return ResponseEntity.ok(outboundService.confirmShipment(documentId));
    }

    // 주문 취소 → 200 + "CANCELED" / 피킹중·출고완료·이미 취소 → 409
    // 문서를 지우지 않고 상태만 CANCELED 로 바꾸므로 DELETE 가 아니라 PUT
    @PutMapping("/wms/outbounds/{documentId}/cancel")
    public ResponseEntity<String> cancelOutbound(@PathVariable(name = "documentId") Integer documentId) {
        return ResponseEntity.ok(outboundService.cancelOutbound(documentId));
    }
}