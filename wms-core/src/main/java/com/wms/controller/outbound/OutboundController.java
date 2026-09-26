package com.wms.controller.outbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.service.OutboundService;

import lombok.RequiredArgsConstructor;

@RestController 
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundService outboundService;

    // ED-12 출고 문서 목록 조회
    @GetMapping ("/wms/outbounds")
    public List<OutboundListDto> getOutboundList() {
        return outboundService.getOutboundList();
    }

    // ED-17 출고 문서 상세 조회
    @GetMapping ("/wms/outbounds/{documentId}")
    public List<OutboundDetailDto> getOutboundDetailList(){
        return outboundService.getOutboundDetailList();
    }
}
