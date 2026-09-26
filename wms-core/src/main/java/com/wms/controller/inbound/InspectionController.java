package com.wms.controller.inbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.inbound.InspectionDto;
import com.wms.model.dto.inbound.InspectionResultDto;
import com.wms.model.dto.inbound.PutawayDto;
import com.wms.service.InboundService;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/wms/inspections")
public class InspectionController {
    @Autowired
    private InboundService inboundService;

    // 검수 1건 등록 ED - 14
    @PostMapping("")
    public Integer inspectionSave(@RequestBody InspectionDto inspectionDto) {
        return inboundService.inspectionSave(inspectionDto);
    }

    // 검수 결과 조회 ED - 15
    @GetMapping("/{documentId}")
    public List<InspectionResultDto> inspectionFindAll(
            @PathVariable(name = "documentId") Integer documentId) {
        return inboundService.inspectionFindAll(documentId);
    }

    // 적재 1건 ED - 16
    @PutMapping("")
    public boolean putaway(@RequestBody PutawayDto putawayDto) {
        return inboundService.putaway(putawayDto);
    }

}
