package com.wms.controller.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;
import com.wms.security.AdminOnly;
import com.wms.service.DemoResetService;

// 시연 데이터 초기화 — 관리자 전용, 감사 로그에 남김
@AdminOnly
@RestController
public class DemoResetController {
    @Autowired
    private DemoResetService demoResetService;

    @AuditLog(action = AuditAction.DEMO_RESET)
    @PostMapping("/wms/admin/demo-reset")
    public ResponseEntity<String> reset() {
        return ResponseEntity.ok(demoResetService.reset());
    }
}