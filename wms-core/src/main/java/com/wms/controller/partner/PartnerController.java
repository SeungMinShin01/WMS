package com.wms.controller.partner;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;   // [추가]
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.partner.PartnerDto;
import com.wms.service.PartnerService;

@RestController
public class PartnerController {
    @Autowired
    private PartnerService partnerService;

    // 거래처 등록 — 새 거래처 번호를 돌려준다
    // [변경] boolean → ResponseEntity<Integer>, System.out.println 삭제
    @PostMapping("/wms/partner")
    public ResponseEntity<Integer> 거래처등록(@RequestBody PartnerDto partnerDto) {
        return ResponseEntity.ok(partnerService.거래처등록(partnerDto));
    }

    // [변경] List<PartnerDto> → ResponseEntity<List<PartnerDto>>
    @GetMapping("/wms/partners")
    public ResponseEntity<List<PartnerDto>> 거래처전체조회() {
        return ResponseEntity.ok(partnerService.거래처전체조회());
    }

    // [변경] PartnerDto → ResponseEntity<PartnerDto>, 겹쳐 있던 중괄호 {{ }} 정리
    @GetMapping("/wms/partner/detail")
    public ResponseEntity<PartnerDto> 거래처개별조회(
            @RequestParam(name = "partnerid") int partnerid) {
        return ResponseEntity.ok(partnerService.거래처개별조회(partnerid));
    }

    // [변경] boolean → ResponseEntity<Boolean>
    @PutMapping("/wms/partner")
    public ResponseEntity<Boolean> 거래처수정(@RequestBody PartnerDto partnerDto) {
        return ResponseEntity.ok(partnerService.거래처수정(partnerDto));
    }
}