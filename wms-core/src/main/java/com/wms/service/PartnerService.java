package com.wms.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.wms.model.dto.partner.PartnerDto;
import com.wms.model.entity.PartnerEntity;
import com.wms.model.entity.TenantEntity;
import com.wms.model.repository.PartnerRepository;
import com.wms.model.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional(readOnly = true)
public class PartnerService {
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private TenantRepository tenantRepository;

    // 필수값 + 구분 검사 (등록, 수정 공통) → 400, 비운 항목을 모아서 알린다
    private void 입력검사(PartnerDto dto, boolean 화주필수) {
        List<String> errors = new ArrayList<>();
        if (화주필수 && dto.getTenantId() == null) {
            errors.add("화주를 선택하세요");
        }
        if (!StringUtils.hasText(dto.getPartnerCode())) {
            errors.add("거래처코드는 필수입니다");
        }
        if (!StringUtils.hasText(dto.getPartnerName())) {
            errors.add("거래처명은 필수입니다");
        }
        if (!StringUtils.hasText(dto.getPartnerType())) {
            errors.add("구분은 필수입니다");
        } else if (!"SUPPLIER".equals(dto.getPartnerType()) && !"CUSTOMER".equals(dto.getPartnerType())) {
            errors.add("구분은 SUPPLIER 또는 CUSTOMER만 가능합니다");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }

    // 같은 화주 안에서 코드 중복이면 거부 → 409 (excludeId: 수정할 때 자기 자신 제외)
    private void 중복검사(Integer tenantId, String code, Integer excludeId) {
        for (PartnerEntity p : partnerRepository.findAll()) {
            boolean sameTenant = p.getTenantEntity().getTenantId().equals(tenantId);
            boolean sameCode = p.getPartnerCode().equals(code);
            boolean self = excludeId != null && p.getPartnerId().equals(excludeId);
            if (sameTenant && sameCode && !self) {
                throw new IllegalStateException("이미 등록된 거래처코드입니다: " + code);
            }
        }
    }

    @Transactional
    public Integer 거래처등록(PartnerDto partnerDto) {
        // 1. 필수값, 구분 검사 → 400
        입력검사(partnerDto, true);

        // 2. 화주 찾기 → 없으면 404
        TenantEntity tenant = tenantRepository.findById(partnerDto.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("화주가 없습니다: " + partnerDto.getTenantId()));

        // 3. 같은 화주 안 코드 중복 → 409 (다른 화주의 같은 코드는 허용)
        중복검사(tenant.getTenantId(), partnerDto.getPartnerCode(), null);

        // 4. 저장 → 새 거래처 번호를 돌려준다
        PartnerEntity saved = partnerRepository.save(partnerDto.toEntity(tenant));
        return saved.getPartnerId();
    }

    // [변경] tenantId가 있으면 그 화주 거래처만, 없으면 전체
    public List<PartnerDto> 거래처전체조회(Integer tenantId) {
        List<PartnerDto> list = new ArrayList<>();
        for (PartnerEntity entity : partnerRepository.findAll()) {
            if (tenantId == null || entity.getTenantEntity().getTenantId().equals(tenantId)) {
                list.add(PartnerDto.from(entity));
            }
        }
        return list;
    }

    // [변경] 없으면 null(200)이 아니라 404
    public PartnerDto 거래처개별조회(int partnerid) {
        PartnerEntity entity = partnerRepository.findById(partnerid)
                .orElseThrow(() -> new EntityNotFoundException("거래처가 없습니다: " + partnerid));
        return PartnerDto.from(entity);
    }

    // [변경] 404 / 화주 변경 400 / 필수값 400 / 같은 화주 코드 중복 409
    @Transactional
    public boolean 거래처수정(PartnerDto partnerDto) {
        // 1. 없으면 404
        if (partnerDto.getPartnerId() == null) {
            throw new IllegalArgumentException("거래처 번호가 없습니다");
        }
        PartnerEntity entity = partnerRepository.findById(partnerDto.getPartnerId())
                .orElseThrow(() -> new EntityNotFoundException("거래처가 없습니다: " + partnerDto.getPartnerId()));

        // 2. 화주는 바꿀 수 없다 → 400
        Integer currentTenantId = entity.getTenantEntity().getTenantId();
        if (partnerDto.getTenantId() != null && !partnerDto.getTenantId().equals(currentTenantId)) {
            throw new IllegalArgumentException("화주는 바꿀 수 없습니다");
        }

        // 3. 필수값, 구분 검사 → 400 (화주는 기존 것을 쓰므로 필수 아님)
        입력검사(partnerDto, false);

        // 4. 같은 화주 + 같은 코드인데 자기 자신이 아닌 것이 있으면 → 409
        중복검사(currentTenantId, partnerDto.getPartnerCode(), entity.getPartnerId());

        // 5. 값 바꾸기 (화주는 안 바꿈)
        entity.setPartnerCode(partnerDto.getPartnerCode());
        entity.setPartnerName(partnerDto.getPartnerName());
        entity.setPartnerType(partnerDto.getPartnerType());
        entity.setContact(partnerDto.getContact());
        entity.setAddress(partnerDto.getAddress());
        return true;
    }
}