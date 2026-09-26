package com.wms.controller.outbound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.outbound.AllocationDto;
import com.wms.service.OutboundService;

@RestController
@RequestMapping("/wms/allocations")
public class AllocationController {
    @Autowired
    private OutboundService outboundService;

    // 할당 1건 Ed - 18
    @PostMapping("")
    public Integer allocationSave(@RequestBody AllocationDto allocationDto) {
        return outboundService.allocationSave(allocationDto);
    }

}
