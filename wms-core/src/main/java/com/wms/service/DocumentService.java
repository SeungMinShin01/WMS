package com.wms.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.DocumentDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.repository.DocumentRepository;

@Service
@Transactional (readOnly = true)
public class DocumentService {
    @Autowired DocumentRepository documentRepository;

    // ED-12 출고 문서 목록 조회
    public List<DocumentDto> getOutboundList() {
        List<DocumentEntity> documentEntities = documentRepository.findByTypeOrderByExpectedAtAsc("OUTBOUND");
        List<DocumentDto> documentDtos = documentEntities.stream().map((entity) -> {return DocumentDto.from(entity);}).toList();
        return documentDtos;
    }
}
