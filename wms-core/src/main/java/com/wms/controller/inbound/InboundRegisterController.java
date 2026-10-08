package com.wms.controller.inbound;

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

import com.wms.model.dto.inbound.InboundCreateDto;
import com.wms.model.dto.inbound.InboundItemCreateDto;
import com.wms.model.dto.inbound.RegisterOptionDto;
import com.wms.service.InboundRegisterService;
import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;

// ED-60 입고 문서 등록 (조회는 InboundController, 등록은 여기)
@RestController
@RequestMapping("/wms/inbounds")
public class InboundRegisterController {
    @Autowired
    private InboundRegisterService inboundRegisterService;

    // 입고 문서 헤더 등록 → 201 + documentId
    @AuditLog(action = AuditAction.INBOUND_CREATE, fields = { "tenantId", "partnerId", "expectedDate" })
    @PostMapping("")
    public ResponseEntity<Integer> createDocument(@RequestBody InboundCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inboundRegisterService.createDocument(dto));
    }

    // 입고 문서 품목 등록 → 201 + documentItemId
    @AuditLog(action = AuditAction.INBOUND_ITEM_ADD, target = "documentId", fields = { "productId", "expectedQty",
            "expiryDate" })
    @PostMapping("/{documentId}/items")
    public ResponseEntity<Integer> createItem(@PathVariable(name = "documentId") Integer documentId,
            @RequestBody InboundItemCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inboundRegisterService.createItem(documentId, dto));
    }

    // 등록 화면 선택지1. 화주목록
    @GetMapping("/tenants")
    public ResponseEntity<List<RegisterOptionDto>> tenantOptions() {
        return ResponseEntity.ok(inboundRegisterService.tenantOptions());
    }

    // 등록 화면 선택지2. 화주의 공급사
    @GetMapping("/tenants/{tenantId}/suppliers")
    public ResponseEntity<List<RegisterOptionDto>> supplierOptions(@PathVariable(name = "tenantId") Integer tenantId) {
        return ResponseEntity.ok(inboundRegisterService.supplierOptions(tenantId));
    }

    // 등록 화면 선택지3. 화주의 품목
    @GetMapping("/tenants/{tenantId}/products")
    public ResponseEntity<List<RegisterOptionDto>> productOptions(@PathVariable(name = "tenantId") Integer tenantId) {
        return ResponseEntity.ok(inboundRegisterService.productOptions(tenantId));
    }
}