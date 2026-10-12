package com.wms.controller.outbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;
import com.wms.model.dto.inbound.RegisterOptionDto;
import com.wms.model.dto.outbound.OutboundCreateDto;
import com.wms.model.dto.outbound.OutboundItemCreateDto;
import com.wms.service.OutboundRegisterService;

// 출고 문서 등록 (조회·할당·피킹은 OutboundController, 등록은 여기) — 입고 InboundRegisterController 와 같은 구조
@RestController
@RequestMapping("/wms/outbounds")
public class OutboundRegisterController {
    @Autowired
    private OutboundRegisterService outboundRegisterService;

    // 출고 문서 헤더 등록 → 201 + documentId
    @AuditLog(action = AuditAction.OUTBOUND_CREATE, fields = { "tenantId", "partnerId", "expectedDate" })
    @PostMapping("")
    public ResponseEntity<Integer> createDocument(@RequestBody OutboundCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(outboundRegisterService.createDocument(dto));
    }

    // 출고 문서 품목 등록 → 201 + documentItemId
    @AuditLog(action = AuditAction.OUTBOUND_ITEM_ADD, target = "documentId", fields = { "productId", "expectedQty" })
    @PostMapping("/{documentId}/items")
    public ResponseEntity<Integer> createItem(@PathVariable(name = "documentId") Integer documentId,
            @RequestBody OutboundItemCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(outboundRegisterService.createItem(documentId, dto));
    }

    // 등록 화면 선택지 1. 화주 목록
    @GetMapping("/tenants")
    public ResponseEntity<List<RegisterOptionDto>> tenantOptions() {
        return ResponseEntity.ok(outboundRegisterService.tenantOptions());
    }

    // 등록 화면 선택지 2. 화주의 납품처
    @GetMapping("/tenants/{tenantId}/customers")
    public ResponseEntity<List<RegisterOptionDto>> customerOptions(@PathVariable(name = "tenantId") Integer tenantId) {
        return ResponseEntity.ok(outboundRegisterService.customerOptions(tenantId));
    }

    // 등록 화면 선택지 3. 화주의 품목
    @GetMapping("/tenants/{tenantId}/products")
    public ResponseEntity<List<RegisterOptionDto>> productOptions(@PathVariable(name = "tenantId") Integer tenantId) {
        return ResponseEntity.ok(outboundRegisterService.productOptions(tenantId));
    }
}