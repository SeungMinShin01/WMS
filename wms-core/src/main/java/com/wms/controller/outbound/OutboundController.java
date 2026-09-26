package com.wms.controller.outbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.service.OutboundService;

@RestController
@RequestMapping("/wms/outbounds")
public class OutboundController {
    @Autowired
    private OutboundService outboundService;

    // 출고 문서 목록 ED - 12
    @GetMapping("")
    public List<OutboundListDto> findAll() {
        return outboundService.findAll();
    }

    // 출고 문서 상세 조회 ED - 17
    @GetMapping("/{documentId}")
    public OutboundDetailDto detailFindAll(
            @PathVariable(name = "documentId") Integer documentId) {
        return outboundService.detailFindAll(documentId);
    }
}
