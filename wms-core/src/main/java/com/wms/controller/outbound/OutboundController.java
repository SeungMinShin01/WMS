package com.wms.controller.outbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.service.OutboundService;

@RestController
@RequestMapping("/wms/outbounds")
public class OutboundController {
    @Autowired
    private OutboundService outboundService;

    // 출고 문서 목록
    @GetMapping("")
    public List<OutboundListDto> findAll() {
        return outboundService.findAll();
    }
}
