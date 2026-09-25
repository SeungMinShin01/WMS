package com.wms.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.wms.model.dto.InboundListDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.repository.DocumentRepository;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class InboundService {
    @Autowired
    private DocumentRepository documentRepository;

    public List<InboundListDto> findAll() {
        // 문서를 전부 다 가져오기 입출고
        List<DocumentEntity> documentEntities = documentRepository.findAll();

        // 입고 필터링
        List<DocumentEntity> inbounds = new ArrayList<>();
        documentEntities.forEach((documentEntity) -> {
            if (documentEntity.getType().equals("INBOUND")) {
                inbounds.add(documentEntity);
            }
        });

        // 예정일이 빠른 순 정렬
        for (int i = 0; i < inbounds.size(); i++) {
            for (int j = i + 1; j < inbounds.size(); j++) {
                if (inbounds.get(j).getExpectedAt().isBefore(inbounds.get(i).getExpectedAt())) {
                    // .isBefore : 날짜끼리 비교 a.isBefore(b) : a가 b보다 이전(<)이냐
                    // LocalDateTime은 숫자가 아니라서 < 로 비교할 수 없기 때문
                    DocumentEntity temp = inbounds.get(i); // 이전 자바에서 했던 Temp를 이용한 순서바꾸기
                    inbounds.set(i, inbounds.get(j));
                    inbounds.set(j, temp);
                }
            }
        }
        // Dto로 변경
        List<InboundListDto> inboundListDtos = new ArrayList<>();
        inbounds.forEach((documentEntity) -> {
            inboundListDtos.add(InboundListDto.from(documentEntity));
        });
        return inboundListDtos;

    }
}
