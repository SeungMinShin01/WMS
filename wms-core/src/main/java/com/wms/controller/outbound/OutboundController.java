package com.wms.controller.outbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.outbound.AllocationDto;
import com.wms.model.dto.outbound.AllocationPreviewDto;
import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.model.dto.outbound.PickingListDto;
import com.wms.model.dto.outbound.ConfirmShipmentDto;
import com.wms.service.OutboundService;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@CrossOrigin (origins = "http://localhost:5173" , allowCredentials = "true")
@RestController
public class OutboundController {
    @Autowired private OutboundService outboundService;

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

    // ED-18 할당 1건 (수동 · 예외용) → 201 + detailId
    @PostMapping ("/wms/allocations")
    public ResponseEntity<Integer> allocate(@RequestBody AllocationDto allocationDto) {
        Integer detailId = outboundService.allocate(allocationDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(detailId);
    }
    
    // ED-18 확장 : 자동 할당 미리보기 (저장 안 함, 추천 결과만) → 200
    @GetMapping("/wms/allocations/{documentId}/preview")
    public ResponseEntity<List<AllocationPreviewDto>> previewAllocate(@PathVariable(name = "documentId") Integer documentId) {
        return ResponseEntity.ok(outboundService.previewAllocate(documentId));
    }

    // ED-18 확장 : 자동 할당 확정 → 201 + 피킹리스트 / 실패 → GlobalExceptionHandler 가 400·404·409 + 메시지
    // 기존 GET /wms/allocations/{documentId}(피킹 조회)와 주소는 같지만 POST 라서 충돌하지 않음
    @PostMapping("/wms/allocations/{documentId}")
    public ResponseEntity<List<PickingListDto>> autoAllocate(@PathVariable(name = "documentId") Integer documentId) {
        List<PickingListDto> pickingList = outboundService.autoAllocate(documentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(pickingList);
    }

    // 피킹 시작 : ALLOCATED → PICKING → 200 + 바뀐 상태
    @PutMapping("/wms/outbounds/{documentId}/picking")
    public ResponseEntity<String> startPicking(@PathVariable(name = "documentId") Integer documentId) {
        return ResponseEntity.ok(outboundService.startPicking(documentId));
    }

    // ED-19 피킹 리스트 조회
    @GetMapping ("/wms/allocations/{documentId}")
    public ResponseEntity<List<PickingListDto>> getPickingList(@PathVariable (name = "documentId") Integer documentId){
        return ResponseEntity.ok(outboundService.getPickingList(documentId));
    }

    // ED-20 출고확정
    @PutMapping ("/wms/allocations")
    public ResponseEntity<Boolean> confirmShipment(@RequestBody ConfirmShipmentDto confirmShipmentDto) {
        return ResponseEntity.ok(outboundService.confirmShipment(confirmShipmentDto));
    }
    
}
