package com.wms.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.inbound.RegisterOptionDto;
import com.wms.model.dto.outbound.OutboundCreateDto;
import com.wms.model.dto.outbound.OutboundItemCreateDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.DocumentStatus;
import com.wms.model.entity.DocumentType;
import com.wms.model.entity.PartnerEntity;
import com.wms.model.entity.ProductEntity;
import com.wms.model.entity.TenantEntity;
import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.repository.PartnerRepository;
import com.wms.model.repository.ProductRepository;
import com.wms.model.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;

// 출고 문서 등록 (입고 InboundRegisterService 와 같은 흐름)
// 재고 검사는 하지 않음 — 주문 접수와 재고 확보(할당)를 나눔. 부족은 할당 단계에서 처리
@Service
@Transactional
public class OutboundRegisterService {
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentItemRepository documentItemRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private PartnerRepository partnerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private DocumentNoGenerator documentNoGenerator;

    // 출고 문서 헤더 등록 -> 생성된 documentId
    public Integer createDocument(OutboundCreateDto dto) {
        // 필수값 400
        if (dto.getTenantId() == null || dto.getPartnerId() == null || dto.getExpectedDate() == null) {
            throw new IllegalArgumentException("화주, 납품처, 출고요청일은 필수입니다.");
        }
        // 존재 확인 404
        TenantEntity tenant = tenantRepository.findById(dto.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("화주를 찾을 수 없습니다."));
        PartnerEntity partner = partnerRepository.findById(dto.getPartnerId())
                .orElseThrow(() -> new EntityNotFoundException("거래처를 찾을 수 없습니다."));
        // 같은 화주의 거래처 400
        if (!partner.getTenantEntity().getTenantId().equals(tenant.getTenantId())) {
            throw new IllegalArgumentException("해당 화주의 거래처가 아닙니다.");
        }
        // 출고는 납품처만 400
        if (!"CUSTOMER".equals(partner.getPartnerType())) {
            throw new IllegalArgumentException("출고 거래처는 납품처만 가능합니다.");
        }
        String documentNo = documentNoGenerator.next(tenant, "OUT");
        return documentRepository.save(dto.toEntity(tenant, partner, documentNo)).getDocumentId();
    }

    // 출고 문서에 품목 1줄 등록 -> 생성된 documentItemId
    public Integer createItem(Integer documentId, OutboundItemCreateDto dto) {
        // 필수값 400
        if (dto.getProductId() == null) {
            throw new IllegalArgumentException("품목은 필수입니다.");
        }
        if (dto.getExpectedQty() == null || dto.getExpectedQty() <= 0) {
            throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다.");
        }
        // 문서 확인
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("문서를 찾을 수 없습니다."));
        if (document.getType() != DocumentType.OUTBOUND) {
            throw new IllegalArgumentException("출고 문서가 아닙니다.");
        }
        if (document.getStatus() != DocumentStatus.WAITING) {
            throw new IllegalStateException("대기 상태의 문서에만 품목을 추가할 수 있습니다.");
        }
        // 품목 확인 + 같은 화주
        ProductEntity product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("품목을 찾을 수 없습니다."));
        if (!product.getTenantEntity().getTenantId().equals(document.getTenantEntity().getTenantId())) {
            throw new IllegalArgumentException("문서의 화주와 다른 화주의 품목입니다.");
        }
        // 같은 문서에 같은 품목 중복 409 (수량은 한 줄로 합쳐서 입력)
        if (documentItemRepository.existsByDocumentEntityAndProductEntity(document, product)) {
            throw new IllegalStateException("이 문서에 이미 등록된 품목입니다.");
        }
        DocumentItemEntity saved = documentItemRepository.save(dto.toEntity(document, product));
        return saved.getDocumentItemId();
    }

    // 등록 화면 선택지 1. 화주 목록
    public List<RegisterOptionDto> tenantOptions() {
        List<RegisterOptionDto> list = new ArrayList<>();
        for (TenantEntity t : tenantRepository.findAll()) {
            list.add(new RegisterOptionDto(t.getTenantId(), t.getTenantCode(), t.getTenantName()));
        }
        return list;
    }

    // 등록 화면 선택지 2. 그 화주의 납품처
    public List<RegisterOptionDto> customerOptions(Integer tenantId) {
        List<RegisterOptionDto> list = new ArrayList<>();
        for (PartnerEntity p : partnerRepository.findByTenantEntity_TenantIdAndPartnerType(tenantId, "CUSTOMER")) {
            list.add(new RegisterOptionDto(p.getPartnerId(), p.getPartnerCode(), p.getPartnerName()));
        }
        return list;
    }

    // 등록 화면 선택지 3. 그 화주의 품목
    public List<RegisterOptionDto> productOptions(Integer tenantId) {
        List<RegisterOptionDto> list = new ArrayList<>();
        for (ProductEntity p : productRepository.findByTenantEntity_TenantId(tenantId)) {
            list.add(new RegisterOptionDto(p.getProductId(), p.getProductCode(), p.getProductName()));
        }
        return list;
    }
}