package com.wms.controller.inbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wms.model.dto.InboundListDto;
import com.wms.service.InboundService;

@RestController
@RequestMapping("/wms/inbounds")
public class InboundController {
    @Autowired
    private InboundService inboundService;

    // 입고 문서 목록
    @GetMapping("")
    public List<InboundListDto> findAll() {
        return inboundService.findAll();
    }

}
