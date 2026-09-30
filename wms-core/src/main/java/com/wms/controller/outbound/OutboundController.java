package com.wms.controller.outbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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

@CrossOrigin (origins = "http://localhost:5173" , allowCredentials = "true")
@RestController
public class OutboundController {
    @Autowired private OutboundService outboundService;               // 조회 · 출고확정
    @Autowired private AllocationPlanService allocationPlanService;   // 할당 미리보기
    @Autowired private PickingListService pickingListService;         // 피킹리스트 생성 · 조회

    // ED-12 출고 문서 목록 조회
    @GetMapping ("/wms/outbounds")
    public ResponseEntity<List<OutboundListDto>> getOutboundList() {
        return ResponseEntity.ok(outboundService.getOutboundList());
    }

    // ED-17 출고 문서 상세 조회
    @GetMapping ("/wms/outbounds/{documentId}")
    public ResponseEntity<OutboundDetailDto> getOutboundDetail(@PathVariable (name = "documentId") Integer documentId){
        return ResponseEntity.ok(outboundService.getOutboundDetail(documentId));
    }

    // ED-18 할당 미리보기 (추천만, 저장 안 함) → 200
    // 예 : GET /wms/allocations/10/preview?documentItemIds=21,22   (documentItemIds 생략하면 전체 품목)
    @GetMapping ("/wms/allocations/{documentId}/preview")
    public ResponseEntity<List<AllocationPreviewDto>> previewAllocate(
            @PathVariable (name = "documentId") Integer documentId,
            @RequestParam (name = "documentItemIds", required = false) List<Integer> documentItemIds) {
        return ResponseEntity.ok(allocationPlanService.previewAllocate(documentId, documentItemIds));
    }

    // ED-18 피킹리스트 생성 (할당 확정) → 201 + 피킹리스트
    // 본문 : [ {"documentItemId":21, "stockId":10, "qty":50}, ... ]  (미리보기 결과를 사용자가 수정한 값)
    @PostMapping ("/wms/allocations/{documentId}/pickinglist")
    public ResponseEntity<List<PickingListDto>> createPickingList(
            @PathVariable (name = "documentId") Integer documentId,
            @RequestBody List<AllocationDto> rows) {
        List<PickingListDto> pickingList = pickingListService.createPickingList(documentId, rows);
        return ResponseEntity.status(HttpStatus.CREATED).body(pickingList);
    }

    // ED-19 피킹 리스트 조회
    @GetMapping ("/wms/allocations/{documentId}")
    public ResponseEntity<List<PickingListDto>> getPickingList(@PathVariable (name = "documentId") Integer documentId){
        return ResponseEntity.ok(pickingListService.getPickingList(documentId));
    }

    // ED-20 출고확정 (문서 단위) → 200 + 바뀐 상태 "SHIPPED" / 이미 출고됨 → 409
    @PutMapping ("/wms/outbounds/{documentId}/ship")
    public ResponseEntity<String> confirmShipment(@PathVariable (name = "documentId") Integer documentId) {
        return ResponseEntity.ok(outboundService.confirmShipment(documentId));
    }
}
