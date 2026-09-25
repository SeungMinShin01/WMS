package com.wms.controller.inbound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.inbound.InspectionDto;
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
}
