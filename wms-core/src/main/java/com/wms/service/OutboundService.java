package com.wms.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class OutboundService {
    private DocumentRepository documentRepository;
    
    // ED-12 출고 문서 목록 조회
    public List<OutboundListDto> getOutboundList() {
        List<DocumentEntity> documentEntities = documentRepository.findByTypeOrderByExpectedAtAsc("OUTBOUND");
        List<OutboundListDto> documentDtos = documentEntities.stream().map((entity) -> {return OutboundListDto.from(entity);}).toList();
        return documentDtos;
    }
}
