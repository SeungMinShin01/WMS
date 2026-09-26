package com.wms.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wms.model.dto.inbound.InboundDetailDto;
import com.wms.model.dto.inbound.InboundItemDto;
import com.wms.model.dto.inbound.InboundListDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;

import jakarta.transaction.Transactional;

@Service 
@Transactional 
public class InboundService {
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemRepository documentItemRepository;
    @Autowired private DocumentItemDetailEntity documentItemDetailEntity;

    // ED-10 입고 문서 목록 조회
    public List<InboundListDto> findAll(){
        // 문서 모두 조회
        List<DocumentEntity> documentEntities = documentRepository.findAll();

        // inbound만 필터링
        List<DocumentEntity> inbounds = new ArrayList<>();
        documentEntities.forEach((documentEntity)->{
            if(documentEntity.getType().equals("INBOUND")){
                inbounds.add(documentEntity);
            }
        });

        // 3. 예정일 빠른 순 정렬
        // compareTo: a가b보다 빠르면/이르면 음수(앞), 같으면 0, 늦으면 양수(뒤)
        // LocalDateTime은 숫자가 아니라서 < 부등호로 비교x
        inbounds.sort((a,b)->a.getExpectedAt().compareTo(b.getExpectedAt()));

        // 4. Dto 변경
        List<InboundListDto> inboundListDtos = new ArrayList<>();
        inbounds.forEach((documentEntity)->{
            inboundListDtos.add(InboundListDto.from(documentEntity));
        });
        return inboundListDtos;
    }

    // ED-13 입고 문서 상세 조회
    public InboundDetailDto detailFind(Integer documentId){
        // 문서 하나 조회
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElse(null);
        InboundDetailDto inboundDetailDto = InboundDetailDto.from(documentEntity);

        // 문서에 포함된 품목 가져오기
        List<DocumentItemEntity> documentItemEntities = documentItemRepository.findAll();
        documentItemEntities.forEach((documentItemEntity)->{
            if(documentItemEntity.getDocumentEntity().getDocumentId().equals(documentId)){
                InboundItemDto inboundItemDto = InboundItemDto.from(documentItemEntity);
                inboundDetailDto.getItems().add(inboundItemDto);
            }
        });
        return inboundDetailDto;
    }

    
}
