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

// 관리자 전용: /wms/workers 는 LoginInterceptor가 ADMIN만 통과시킴
@RequestMapping("/wms/workers")
@RestController
public class WorkerController {
    @Autowired private WorkerService workerService;

    @GetMapping
    public ResponseEntity<List<WorkerDto>> findAll(){
        return ResponseEntity.ok(workerService.findAll());
    }

    @PostMapping
    public ResponseEntity<Integer> create(@RequestBody WorkerCreateDto dto){
        return ResponseEntity.status(HttpStatus.CREATED).body(workerService.create(dto));
    }
}