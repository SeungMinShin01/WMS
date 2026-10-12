package com.wms.controller.auth;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.auth.WorkerCreateDto;
import com.wms.model.dto.auth.WorkerDto;
import com.wms.service.WorkerService;
import com.wms.security.AdminOnly;
import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;

// 팀장변경 /관리자 전용: 클래스 전체에 @AdminOnly
@AdminOnly
@RequestMapping("/wms/workers")
@RestController
public class WorkerController {
    @Autowired
    private WorkerService workerService;

    @GetMapping
    public ResponseEntity<List<WorkerDto>> findAll() {
        return ResponseEntity.ok(workerService.findAll());
    }

    @AuditLog(action = AuditAction.WORKER_CREATE, target = "loginId", fields = { "userName" })
    @PostMapping
    public ResponseEntity<Integer> create(@RequestBody WorkerCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workerService.create(dto));
    }
}