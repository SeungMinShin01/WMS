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
import org.springframework.web.bind.annotation.RestController;

import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;
import com.wms.model.dto.outbound.PickingAssignDto;
import com.wms.model.dto.outbound.PickingCreateDto;
import com.wms.model.dto.outbound.PickingDetailDto;
import com.wms.model.dto.outbound.PickingDto;
import com.wms.model.dto.outbound.PickingItemDto;
import com.wms.model.dto.outbound.PickingScanDto;
import com.wms.security.AdminOnly;
import com.wms.security.LoginInterceptor;
import com.wms.security.LoginUser;
import com.wms.service.QrPickingService;

import jakarta.servlet.http.HttpServletRequest;

// QR 피킹 API (ED-75 ~ ED-77)
// 메서드 순서 : 피킹 리스트 생성(ED-75) → 작업자 배정(ED-75)
//              → 내 피킹 리스트(ED-76) → 전체 피킹 리스트(ED-76) → 피킹 리스트 상세(ED-76) → 스캔(ED-77)
// 서비스에서 던진 예외는 GlobalExceptionHandler 가 받아서 400 / 403 / 404 / 409 응답으로 바꿈
@RestController
public class QrPickingController {
    @Autowired
    private QrPickingService qrPickingService;

    // ED-75 피킹 리스트 생성 → 201 + 만든 리스트
    // 본문 : {"documentId": 67}
    // 관리자만 (@AdminOnly) : 작업자가 리스트를 마음대로 만들지 못하게
    // 새로 만드는 것이라 POST + 201(CREATED)
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

    // ED-76 내 피킹 리스트 (작업자 화면) → 200
    // 로그인한 사람에게 배정된 것 중 배정됨·피킹중인 것만
    // 주소에 작업자 번호를 받지 않고 로그인 정보에서 꺼냄 → 남의 번호를 넣어서 남의 리스트를 볼 수 없게
    @GetMapping("/wms/pickings/mine")
    public ResponseEntity<List<PickingDto>> getMyPickings(HttpServletRequest request) {
        return ResponseEntity.ok(qrPickingService.getMyPickings(loginUserId(request)));
    }

    // ED-76 전체 피킹 리스트 (관리자 화면) → 200
    @AdminOnly
    @GetMapping("/wms/pickings")
    public ResponseEntity<List<PickingDto>> getAllPickings() {
        return ResponseEntity.ok(qrPickingService.getAllPickings());
    }

    // ED-76 피킹 리스트 상세 → 200 / 남의 리스트 → 403
    // 예 : GET /wms/pickings/5
    // /wms/pickings/mine 과 주소 모양이 같지만, 스프링은 글자가 정확히 맞는 mine 을 먼저 고름
    @GetMapping("/wms/pickings/{pickingListId}")
    public ResponseEntity<PickingDetailDto> getPickingDetail(
            @PathVariable(name = "pickingListId") Integer pickingListId,
            HttpServletRequest request) {
        return ResponseEntity.ok(qrPickingService.getPickingDetail(pickingListId, loginUserId(request)));
    }

    // ED-77 스캔 (집음) → 200 + 바뀐 줄 1개
    // 예 : PUT /wms/pickings/5/scan  본문 : {"pickingListItemId": 12, "lotCode": "L-20261001-001", "qty": 3}
    // 집은 수량을 바꾸는 수정이라 PUT / 작업자가 쓰는 API 라서 @AdminOnly 없음 (본인 리스트 검사는 서비스에서)
    @AuditLog(action = AuditAction.PICKING_SCAN, target = "pickingListId", fields = { "pickingListItemId", "lotCode", "qty" })
    @PutMapping("/wms/pickings/{pickingListId}/scan")
    public ResponseEntity<PickingItemDto> scan(
            @PathVariable(name = "pickingListId") Integer pickingListId,
            @RequestBody PickingScanDto dto,
            HttpServletRequest request) {
        return ResponseEntity.ok(qrPickingService.scan(pickingListId, dto, loginUserId(request)));
    }

    // 로그인한 사람의 계정 번호 꺼내기
    // LoginInterceptor 가 토큰을 확인한 뒤 요청(request)에 LoginUser 를 넣어 둠 → 그걸 꺼내서 번호만 돌려줌
    // LoginUser 는 record 라서 getUserId() 가 아니라 userId() 로 꺼냄
    private Integer loginUserId(HttpServletRequest request) {
        LoginUser user = (LoginUser) request.getAttribute(LoginInterceptor.LOGIN_USER);
        return user.userId();
    }
}