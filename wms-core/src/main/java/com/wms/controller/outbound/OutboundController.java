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
    public List<OutboundListDto> getOutboundList() {
        return outboundService.getOutboundList();
    }

    // ED-17 출고 문서 상세 조회
    @GetMapping ("/wms/outbounds/{documentId}")
    public OutboundDetailDto getOutboundDetail(@PathVariable (name = "documentId") Integer documentId){
        return outboundService.getOutboundDetail(documentId);
    }

    // ED-18 할당 1건 (피킹리스트 생성)
    @PostMapping ("/wms/allocations")
    public Integer allocate(@RequestBody AllocationDto allocationDto) {
    return outboundService.allocate(allocationDto);
    }
    
    // ED-18 확장 : 출고 문서 자동 할당 (할당 규칙 v3)
    // 성공 → 201 + 피킹리스트 / 실패 → GlobalExceptionHandler 가 400·404·409 + 메시지로 응답
    // 기존 GET /wms/allocations/{documentId}(피킹 조회)와 주소는 같지만 POST 라서 충돌하지 않음
    @PostMapping("/wms/allocations/{documentId}")
    public ResponseEntity<List<PickingListDto>> autoAllocate(@PathVariable(name = "documentId") Integer documentId) {
        // 주소의 {documentId} 값을 받아 서비스의 자동 할당 실행 → 결과 피킹리스트
        List<PickingListDto> pickingList = outboundService.autoAllocate(documentId);
        // 새 할당(detail)이 만들어졌으니 201 Created, 본문에는 피킹리스트
        return ResponseEntity.status(HttpStatus.CREATED).body(pickingList);
    }


    // ED-19 피킹 리스트 조회
    @GetMapping ("/wms/allocations/{documentId}")
    public List<PickingListDto> getPickingList(@PathVariable (name = "documentId") Integer documentId){
        return  outboundService.getPickingList(documentId);
    }

    // ED-20 출고확정
    @PutMapping ("/wms/allocations")
    public Boolean confirmShipment(@RequestBody ConfirmShipmentDto confirmShipmentDto) {
        return outboundService.confirmShipment(confirmShipmentDto);
    }
    
}
