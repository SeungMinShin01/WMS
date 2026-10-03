package com.wms.controller.stock;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.stock.StockDto;
import com.wms.model.dto.stock.StockHistoryDto;
import com.wms.service.StockService;

@RestController
@RequestMapping("/wms/stocks")
public class StockController {
    @Autowired private StockService stockService;

    // 전체 재고 조회 ED - 21
    @GetMapping("")
    public ResponseEntity<List<StockDto>> stockFindAll() {
        return ResponseEntity.ok(stockService.stockFindAll());
    }

    // 입출고 이력
    @GetMapping("/history")
    public ResponseEntity<List<StockHistoryDto>> historyFindAll(){
        return ResponseEntity.ok(stockService.historyFindAll());
    }
}