package com.wms.controller.partner;


import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.partner.PartnerDto;
import com.wms.service.PartnerService;



@RestController 
public class PartnerController {
    @Autowired private PartnerService partnerService;

    @PostMapping("/wms/partner")
    public boolean 거래처등록(
            @RequestBody PartnerDto partnerDto){
            System.out.println(partnerDto);
        return partnerService.거래처등록( partnerDto );

    }
    @GetMapping("/wms/partners")
    public List<PartnerDto> 거래처전체조회(){
        return partnerService.거래처전체조회();
    }

    @PutMapping("/wms/partner")
    public boolean 거래처수정( @RequestBody PartnerDto partnerDto ){
        return partnerService.거래처수정( partnerDto );
    } 
}

 	