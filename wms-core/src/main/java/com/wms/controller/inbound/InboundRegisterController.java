package com.wms.controller.inbound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.inbound.InboundCreateDto;
import com.wms.model.dto.inbound.InboundItemCreateDto;
import com.wms.service.InboundRegisterService;

// ED-60 입고 문서 등록 (조회는 InboundController, 등록은 여기)
@RestController
@RequestMapping("/wms/inbounds")
public class InboundRegisterController {
    @Autowired private InboundRegisterService inboundRegisterService;

    // 입고 문서 헤더 등록 → 201 + documentId
    @PostMapping("")
    public ResponseEntity<Integer> createDocument(@RequestBody InboundCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inboundRegisterService.createDocument(dto));
    }

    // 입고 문서 품목 등록 → 201 + documentItemId
    @PostMapping("/{documentId}/items")
    public ResponseEntity<Integer> createItem(@PathVariable(name = "documentId") Integer documentId,
                                              @RequestBody InboundItemCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inboundRegisterService.createItem(documentId, dto));
    }
}