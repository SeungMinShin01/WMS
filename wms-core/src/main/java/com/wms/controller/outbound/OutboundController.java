package com.wms.controller.outbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.DocumentDto;
import com.wms.service.DocumentService;

import lombok.RequiredArgsConstructor;

@RestController 
@RequiredArgsConstructor
public class OutboundController {

    private final DocumentService documentService;

    // ED-12 출고 문서 목록 조회
    @GetMapping ("/wms/outbounds")
    public List<DocumentDto> getOutboundList() {
        return documentService.getOutboundList();
    }

}
