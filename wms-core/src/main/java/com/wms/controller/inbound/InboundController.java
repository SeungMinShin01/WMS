package com.wms.controller.inbound;

import com.wms.service.CarryAsyncService;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;
import com.wms.model.dto.inbound.InboundDetailDto;
import com.wms.model.dto.inbound.InboundListDto;
import com.wms.service.InboundService;

@RestController
@RequestMapping("/wms/inbounds")
public class InboundController {
    private final CarryAsyncService carryAsyncService;
    @Autowired
    private InboundService inboundService;

    InboundController(CarryAsyncService carryAsyncService) {
        this.carryAsyncService = carryAsyncService;
    }

    // ED-10 입고 문서 목록 조회
    @GetMapping("")
    public ResponseEntity<List<InboundListDto>> findAll() {
        return ResponseEntity.ok(inboundService.findAll());
    }

    // ED-13 입고 문서 상세 조회
    @GetMapping("/{documentId}")
    public ResponseEntity<InboundDetailDto> detailFind(@PathVariable(name = "documentId") Integer documentId) {
        return ResponseEntity.ok(inboundService.detailFind(documentId));
    }

    // 입고 문서 취소 — 문서를 지우지 않고 상태만 CANCELED 로 바꾸므로 PUT
    // 적재 대기열에 넣고 끝날 때까지 기다림 (예외는 CompletionException → 공통 예외처리가 풀어줌)
    @AuditLog(action = AuditAction.INBOUND_CANCEL, target = "documentId")
    @PutMapping("/{documentId}/cancel")
    public ResponseEntity<String> cancel(@PathVariable(name = "documentId") Integer documentId) {
        return ResponseEntity.ok(carryAsyncService.cancel(documentId).join());
    }
}