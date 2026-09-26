package com.wms.controller.inbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.inbound.StockDto;
import com.wms.service.InboundService;

@RestController
@RequestMapping("/wms/stocks")
public class StockController {
    @Autowired
    private InboundService inboundService;

    // 전체 재고 조회 ED - 21
    @GetMapping("")
    public List<StockDto> stockFindAll() {
        return inboundService.stockFindAll();
    }
}