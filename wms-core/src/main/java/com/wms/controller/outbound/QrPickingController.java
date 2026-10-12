package com.wms.controller.outbound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;
import com.wms.model.dto.outbound.PickingAssignDto;
import com.wms.model.dto.outbound.PickingCreateDto;
import com.wms.model.dto.outbound.PickingDto;
import com.wms.security.AdminOnly;
import com.wms.service.QrPickingService;

// QR 피킹 API (ED-75 ~ ED-77)
// 메서드 순서 : 피킹 리스트 생성(ED-75) → 작업자 배정(ED-75)
// 서비스에서 던진 예외는 GlobalExceptionHandler 가 받아서 400 / 404 / 409 응답으로 바꿈
@RestController
public class QrPickingController {
    @Autowired
    private QrPickingService qrPickingService;

    // ED-75 피킹 리스트 생성 → 201 + 만든 리스트
    // 본문 : {"documentId": 67}
    // 관리자만 (@AdminOnly) : 작업자가 리스트를 마음대로 만들지 못하게
    @AdminOnly
    @AuditLog(action = AuditAction.PICKING_CREATE, target = "documentId")
    @PostMapping("/wms/pickings")
    public ResponseEntity<PickingDto> createPicking(@RequestBody PickingCreateDto dto) {
        PickingDto picking = qrPickingService.createPicking(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(picking);
    }

    // ED-75 작업자 배정 → 200 + 배정된 리스트
    // 예 : PUT /wms/pickings/5/assign  본문 : {"workerId": 3}
    // 이미 있는 리스트의 작업자·상태를 바꾸는 수정이라 PUT
    // 같은 리스트를 다른 작업자로 다시 배정하는 것도 이 API (CREATED · ASSIGNED 일 때만)
    @AdminOnly
    @AuditLog(action = AuditAction.PICKING_ASSIGN, target = "pickingListId", fields = { "workerId" })
    @PutMapping("/wms/pickings/{pickingListId}/assign")
    public ResponseEntity<PickingDto> assignWorker(
            @PathVariable(name = "pickingListId") Integer pickingListId,
            @RequestBody PickingAssignDto dto) {
        return ResponseEntity.ok(qrPickingService.assignWorker(pickingListId, dto));
    }
}