package com.wms.controller.audit;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;
import com.wms.audit.AuditLogDto;
import com.wms.audit.AuditLogReader;
import com.wms.security.AdminOnly;

// 감사 로그 조회·다운로드 — 관리자 전용
@AdminOnly
@RestController
@RequestMapping("/wms/audit-logs")
public class AuditLogController {
    @Autowired
    private AuditLogReader auditLogReader;

    // 하루치 (date 없으면 오늘), 최신순
    @GetMapping
    public ResponseEntity<List<AuditLogDto>> list(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(auditLogReader.read(date == null ? LocalDate.now() : date));
    }

    // CSV 다운로드 — 누가 받아갔는지도 감사 로그에 남김
    @AuditLog(action = AuditAction.AUDIT_DOWNLOAD, fields = { "date" })
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-" + day + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(auditLogReader.download(day));
    }
}