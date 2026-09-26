package com.wms.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundItemDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.model.entity.DocumentEntity;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class OutboundService {
    // ED - 12
    @Autowired
    private DocumentRepository documentRepository;
    // ED - 17
    @Autowired
    private DocumentItemRepository documentItemRepository;

    // 출고 문서 전부 가져오기 ED - 12
    public List<OutboundListDto> findAll() {
        List<DocumentEntity> documentEntities = documentRepository.findAll();

        return documentEntities.stream()
                // OUTBOUND 문서 필터
                .filter((entity) -> entity.getType().equals("OUTBOUND"))
                // sorted 무엇을 기준으로 정렬할지
                // comparedTo : a가 b보다 이르면 음수(앞), 같으면 0, 늦으면 양수(뒤) 반환
                .sorted((a, b) -> a.getExpectedAt().compareTo(b.getExpectedAt()))
                .map((entity) -> OutboundListDto.from(entity))
                .toList();
    }

    // 출고 문서 상세 조회 ED - 17
    public OutboundDetailDto detailFindAll(Integer documentId) {
        // 문서 하나 가져오기
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElse(null);
        OutboundDetailDto detailDto = OutboundDetailDto.from(documentEntity);

        // 해당 문서에 해당하는 품목만 가져오기
        List<OutboundItemDto> items = documentItemRepository.findAll().stream()
                .filter((item) -> {
                    return item.getDocumentEntity().getDocumentId().equals(documentId);
                })
                .map((item) -> {
                    return OutboundItemDto.from(item);
                })
                .toList();
        detailDto.setItems(items);
        return detailDto;
    }

}
