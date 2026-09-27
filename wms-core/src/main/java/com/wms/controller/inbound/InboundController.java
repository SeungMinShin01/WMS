package com.wms.controller.inbound;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.inbound.InboundDetailDto;
import com.wms.model.dto.inbound.InboundListDto;
import com.wms.service.InboundService;

@RestController 
@RequestMapping("/wms/inbounds")
public class InboundController {
    @Autowired private InboundService inboundService;

    // ED-10 입고 문서 목록 조회
    @GetMapping("")
    public List<InboundListDto> findAll(){
        return inboundService.findAll();
    }
    
    // ED-13 입고 문서 상세 조회
    @GetMapping("/{documentId}")
    public InboundDetailDto detailFind(@PathVariable (name="documentId") Integer documentId){
        return inboundService.detailFind(documentId);
    } 

    
}