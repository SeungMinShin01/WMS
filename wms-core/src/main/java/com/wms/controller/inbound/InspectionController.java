package com.wms.controller.inbound;

import com.wms.service.CarryAsyncService;
import java.util.List;
import java.util.concurrent.CompletionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.inbound.CarryingDto;
import com.wms.model.dto.inbound.InspectionDto;
import com.wms.model.dto.inbound.InspectionResultDto;
import com.wms.model.dto.inbound.LocationOptionDto;
import com.wms.model.dto.inbound.LocationRecommendDto;
import com.wms.service.InboundService;
import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/wms/inspections")
public class InspectionController {
    @Autowired
    private CarryAsyncService carryAsyncService;
    @Autowired
    private InboundService inboundService;

    // 검수 1건 등록 ED - 14
    @AuditLog(action = AuditAction.INSPECT, target = "documentItemId", fields = { "qty" })
    @PostMapping("")
    public ResponseEntity<Integer> inspectionSave(@RequestBody InspectionDto inspectionDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inboundService.inspectionSave(inspectionDto));
    }

    // 검수 결과 상세 조회 ED - 15
    @GetMapping("/{documentId}")
    public ResponseEntity<List<InspectionResultDto>> inspectionFindAll(
            @PathVariable(name = "documentId") Integer documentId) {
        return ResponseEntity.ok(inboundService.inspectionFindAll(documentId));
    }

    // 적재 1건 ED - 16
    @AuditLog(action = AuditAction.CARRY, target = "detailId", fields = { "locationId", "mixLot", "force" })
    @PutMapping("")
    public ResponseEntity<Boolean> carry(@RequestBody CarryingDto carryingDto) {
        // 대기열에 넣고 -> 워커가 처리할때까지 기다렸다 결과 응답(실험에서 성공/409를 세기 위해)
        return ResponseEntity.ok(carryAsyncService.carry(carryingDto).join());
    }

    // 적치 추천 (검수 기록 1건 기준) — mixLot=true면 혼용적재 모드
    @GetMapping("/recommend/{detailId}")
    public ResponseEntity<List<LocationRecommendDto>> recommend(@PathVariable(name = "detailId") Integer detailId,
            @RequestParam(name = "mixLot", defaultValue = "false") boolean mixLot) {
        return ResponseEntity.ok(inboundService.recommend(detailId, mixLot));
    }

    // 적치 로케이션 선택 목록
    @GetMapping("/locations")
    public ResponseEntity<List<LocationOptionDto>> locationFindAll() {
        return ResponseEntity.ok(inboundService.locationFindAll());
    }
}
