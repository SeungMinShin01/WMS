package com.wms.controller.partner;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.partner.PartnerDto;
import com.wms.service.PartnerService;
import com.wms.security.AdminOnly;
import com.wms.audit.AuditAction;
import com.wms.audit.AuditLog;

@RestController
public class PartnerController {
    @Autowired
    private PartnerService partnerService;

    @AdminOnly
    @AuditLog(action = AuditAction.PARTNER_CREATE, target = "partnerCode", fields = { "partnerName", "partnerType" })
    @PostMapping("/wms/partner")
    public boolean 거래처등록(
            @RequestBody PartnerDto partnerDto) {
        System.out.println(partnerDto);
        return partnerService.거래처등록(partnerDto);

    }

    @GetMapping("/wms/partners")
    public List<PartnerDto> 거래처전체조회() {
        return partnerService.거래처전체조회();
    }

    @GetMapping("/wms/partner/detail")
    public PartnerDto 거래처개별조회(
            @RequestParam(name = "partnerid") int partnerid) {
        {
            return partnerService.거래처개별조회(partnerid);
        }
    }

    @AdminOnly
    @AuditLog(action = AuditAction.PARTNER_UPDATE, target = "partnerId", fields = { "partnerCode", "partnerName" })
    @PutMapping("/wms/partner")
    public boolean 거래처수정(@RequestBody PartnerDto partnerDto) {
        return partnerService.거래처수정(partnerDto);
    }
}
