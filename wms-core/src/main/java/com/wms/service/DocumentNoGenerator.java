package com.wms.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.TenantEntity;
import com.wms.model.repository.DocumentRepository;

// 입고·출고 공용 문서번호 생성 (InboundRegisterService 에서 옮김)
// 화주코드-구분-오늘날짜-순번 (ex: HTL-IN-20261003-001, HTL-OUT-20261012-001)
@Component
public class DocumentNoGenerator {
    @Autowired
    private DocumentRepository documentRepository;

    public String next(TenantEntity tenant, String typeCode) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = tenant.getTenantCode() + "-" + typeCode + "-" + today + "-";

        Optional<DocumentEntity> last = documentRepository.findTopByDocumentNoStartingWithOrderByDocumentNoDesc(prefix);
        int next = 1;
        if (last.isPresent()) {
            String lastNo = last.get().getDocumentNo(); // HTL-IN-20261003-005
            next = Integer.parseInt(lastNo.substring(prefix.length())) + 1; // 005 -> 006
        }
        return prefix + String.format("%03d", next);
    }
}