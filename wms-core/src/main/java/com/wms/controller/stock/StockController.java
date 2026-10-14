package com.wms.controller.stock;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;
import com.wms.model.dto.stock.StockDto;
import com.wms.model.dto.stock.StockHistoryDto;
import com.wms.security.AdminOnly;
import com.wms.security.LoginInterceptor;
import com.wms.security.LoginUser;
import com.wms.security.UnauthorizedException;
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

    // 재고 폐기 (관리자 전용) — 재고 줄은 지우지 않고 실물 0 이 되므로 DELETE 가 아니라 PUT
    @AdminOnly
    @AuditLog(action = AuditAction.STOCK_DISPOSE, target = "stockId")
    @PutMapping("/{stockId}/dispose")
    public ResponseEntity<Integer> dispose(@PathVariable(name = "stockId") Integer stockId,
            @RequestAttribute(name = LoginInterceptor.LOGIN_USER, required = false) LoginUser me) {
        if (me == null)
            throw new UnauthorizedException("로그인이 필요합니다.");
        return ResponseEntity.ok(stockService.dispose(stockId, me.userId()));
    }
}