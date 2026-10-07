package com.wms.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;
import com.wms.model.dto.inbound.InboundCreateDto;
import com.wms.model.dto.inbound.InboundItemCreateDto;
import com.wms.model.dto.inbound.RegisterOptionDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.DocumentStatus;
import com.wms.model.entity.DocumentType;
import com.wms.model.entity.LotEntity;
import com.wms.model.entity.PartnerEntity;
import com.wms.model.entity.ProductEntity;
import com.wms.model.entity.TenantEntity;
import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.repository.LotRepository;
import com.wms.model.repository.PartnerRepository;
import com.wms.model.repository.ProductRepository;
import com.wms.model.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

// ED-60 입고문서 등록
@Service 
@Transactional 
public class InboundRegisterService {
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemRepository documentItemRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LotRepository lotRepository;

    // 입고 문서 헤더 등록 -> 생성된 documentId 반환
    public Integer createDocument(InboundCreateDto dto){
        // 필수값 400
        if(dto.getTenantId() == null || dto.getPartnerId() == null || dto.getExpectedDate() == null){
        throw new IllegalArgumentException("화주, 거래처, 입고예정일은 필수입니다");
    }
    
    // 존재 확인 404
    TenantEntity tenant = tenantRepository.findById(dto.getTenantId())
        .orElseThrow(() -> new EntityNotFoundException("화주를 찾을 수 없습니다."));
    PartnerEntity partner = partnerRepository.findById(dto.getPartnerId())
        .orElseThrow(() -> new EntityNotFoundException("거래처를 찾을 수 없습니다."));
    
    // 거래처는 같은 화주 소속이어야함 400
    if(!partner.getTenantEntity().getTenantId().equals(tenant.getTenantId())){
        throw new IllegalArgumentException("해당 화주의 거래처가 아닙니다.");
    }
    // 입고는 공급사만 400 (partnerType은 String 필드)
    if(!"SUPPLIER".equals(partner.getPartnerType())){
        throw new IllegalArgumentException("입고 거래처는 공급사만 가능합니다.");
    }
    String documentNo = nextDocumentNo(tenant, "IN");
    DocumentEntity saved = documentRepository.save(dto.toEntity(tenant, partner, documentNo));
    return saved.getDocumentId();
    }

    // 문서번호 생성: 화주코드-IN-오늘날짜-순번 (ex: HTL-IN-20261003-001)
    private String nextDocumentNo(TenantEntity tenant, String typeCode){
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = tenant.getTenantCode() + "-" + typeCode + "-" + today + "-";

        Optional<DocumentEntity> last = documentRepository.findTopByDocumentNoStartingWithOrderByDocumentNoDesc(prefix);
        int next = 1;
        if(last.isPresent()){
            String lastNo = last.get().getDocumentNo(); // // HLT-IN-20261003-005
            next = Integer.parseInt(lastNo.substring(prefix.length()))+1;   // 005 -> 006
        }
        return prefix + String.format("%03d", next);    // 006
    }

    // 입고 문서에 품목 1줄 등록 -> 생성된 documentItemId 반환
    public Integer createItem(Integer documentId, InboundItemCreateDto dto){
        // 필수값 400, StringUtils.hasText는 null,빈 문자열, 공백만 있는 경우를 모두 걸러줌
        if (dto.getProductId() == null || !StringUtils.hasText(dto.getLotCode()) || dto.getExpiryDate() == null) {
            throw new IllegalArgumentException("품목, LOT 번호, 소비기한은 필수입니다.");
        }
        if (dto.getExpectedQty() == null || dto.getExpectedQty() <= 0) {
            throw new IllegalArgumentException("예정 수량은 1 이상이어야 합니다.");
        }
        // 문서 확인
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("문서를 찾을 수 없습니다."));
        if (document.getType() != DocumentType.INBOUND) {
            throw new IllegalArgumentException("입고 문서가 아닙니다.");
        }
        if (document.getStatus() != DocumentStatus.WAITING) {
            throw new IllegalStateException("대기 상태의 문서에만 품목을 추가할 수 있습니다.");
        }
        // 품목 확인 + 문서와 같은 화주인지
        ProductEntity product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("품목을 찾을 수 없습니다."));
        if (!product.getTenantEntity().getTenantId().equals(document.getTenantEntity().getTenantId())) {
            throw new IllegalArgumentException("문서의 화주와 다른 화주의 품목입니다.");
        }

        // LOT: 같은 품목 + 같은 LOT 번호가 있으면 재사용, 없으면 새로 등록
        // .trim 앞뒤 공백 지우기
        String lotCode = dto.getLotCode().trim();
        LotEntity lot;
        Optional<LotEntity> found = lotRepository.findByProductEntityAndLotCode(product, lotCode);
        if (found.isPresent()) {
            lot = found.get();
            if (!dto.getExpiryDate().equals(lot.getExpiryDate())) {
                throw new IllegalStateException("같은 LOT 번호인데 소비기한이 다릅니다. (기존: " + lot.getExpiryDate() + ")");
            }
            if (documentItemRepository.existsByDocumentEntityAndLotEntity(document, lot)) {
                throw new IllegalStateException("이 문서에 이미 등록된 LOT입니다.");
            }
        } else {
            lot = lotRepository.save(LotEntity.builder()
                    .productEntity(product)
                    .lotCode(lotCode)
                    .expiryDate(dto.getExpiryDate())
                    .build());
        }
        DocumentItemEntity saved = documentItemRepository.save(dto.toEntity(document, product, lot));
        return saved.getDocumentItemId();
    }       
    
    // 등록화면 선택지 1. 화주목록
    public List<RegisterOptionDto> tenantOptions(){
        List<RegisterOptionDto> list = new ArrayList<>();
        for(TenantEntity t : tenantRepository.findAll()){
            list.add(new RegisterOptionDto(t.getTenantId(), t.getTenantCode(), t.getTenantName()));
        }
        return list;
    }

    // 등록화면 선택지 2. 그 화주의 공급사만 
    public List<RegisterOptionDto> supplierOptions(Integer tenantId){
        List<RegisterOptionDto> list = new ArrayList<>();
        for(PartnerEntity p : partnerRepository.findAll()){
            if(p.getTenantEntity().getTenantId().equals(tenantId) && "SUPPLIER".equals(p.getPartnerType())){
                list.add(new RegisterOptionDto(p.getPartnerId(), p.getPartnerCode(), p.getPartnerName()));
            }
        }
        return list;
    }

    // 등록화면 선택지 3. 그 화주의 품목만
    public List<RegisterOptionDto> productOptions(Integer tenantId){
        List<RegisterOptionDto> list = new ArrayList<>();
        for(ProductEntity p : productRepository.findAll()){
            if(p.getTenantEntity().getTenantId().equals(tenantId)){
                list.add(new RegisterOptionDto(p.getProductId(), p.getProductCode(), p.getProductName()));
            }
        }
        return list;
    }

}
