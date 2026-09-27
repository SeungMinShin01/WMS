package com.wms.controller.outbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.outbound.AllocationDto;
import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.service.OutboundService;
import org.springframework.web.bind.annotation.RequestBody;


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
}
