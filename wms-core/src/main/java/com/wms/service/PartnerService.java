package com.wms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;   // 필수값 검사용

import com.wms.model.dto.partner.PartnerDto;
import com.wms.model.entity.PartnerEntity;
import com.wms.model.entity.TenantEntity;                 // [추가]
import com.wms.model.repository.PartnerRepository;
import com.wms.model.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;       // [추가]

@Service
@Transactional(readOnly = true)
public class PartnerService {
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private TenantRepository tenantRepository;   // [추가]

    // [변경] boolean → Integer (새 거래처 번호를 돌려준다), 화주 검사 추가
    @Transactional   // [추가] 클래스가 readOnly라서 쓰기 메서드는 따로 붙인다
    public Integer 거래처등록( PartnerDto partnerDto ){
        // [추가] 1. 필수값 검사 (400)
        if (partnerDto.getTenantId() == null) {
            throw new IllegalArgumentException("화주를 선택하세요");
        }
        if (!StringUtils.hasText(partnerDto.getPartnerCode())) {
            throw new IllegalArgumentException("거래처코드는 필수입니다");
        }
        if (!StringUtils.hasText(partnerDto.getPartnerName())) {
            throw new IllegalArgumentException("거래처명은 필수입니다");
        }

        // [추가] 2. 화주 조회 (없으면 404)
        TenantEntity tenant = tenantRepository.findById(partnerDto.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("화주가 없습니다: " + partnerDto.getTenantId()));

        // [추가] 3. 같은 화주 안에서 거래처코드 중복이면 거부 (409), 다른 화주는 같은 코드 허용
        for (PartnerEntity p : partnerRepository.findAll()) {
            boolean sameTenant = p.getTenantEntity().getTenantId().equals(tenant.getTenantId());
            boolean sameCode = p.getPartnerCode().equals(partnerDto.getPartnerCode());
            if (sameTenant && sameCode) {
                throw new IllegalStateException("이미 등록된 거래처코드입니다: " + partnerDto.getPartnerCode());
            }
        }

        // [변경] toEntity() → toEntity(tenant), true/false 대신 새 번호
        PartnerEntity saved = partnerRepository.save( partnerDto.toEntity(tenant) );
        return saved.getPartnerId();
    }

    // http://localhost:8080/wms/partner/detail?partnerid=1
    // [그대로] 클래스의 readOnly 트랜잭션이 적용된다
    public PartnerDto 거래처개별조회( int partnerid ){
        Optional<PartnerEntity> optional = partnerRepository.findById( partnerid ); // 1. findById 엔티티 개별조회
        if( optional.isPresent() ) { // 2. 조회 결과 존재하면
            PartnerEntity entity = optional.get(); // 3. 엔티티 꺼내기
            return PartnerDto.from(entity);
        }
        return null;
    }

    // [그대로]
    public List<PartnerDto> 거래처전체조회(){
        List<PartnerEntity> entities = partnerRepository.findAll(); // 1. findAll 엔티티 전체조회
        List<PartnerDto> list = new ArrayList<>(); // 2. 엔티티 -> dto 변환
        entities.forEach( (entity) -> {
            PartnerDto dto = PartnerDto.from( entity );
            list.add( dto );
        });
        return list;
    }

    // [그대로] 화주 변경 막기, 코드 중복 검사는 나중에 (ED-62)
    @Transactional
    public boolean 거래처수정( PartnerDto partnerDto ){
        // 1. 수정할 pk 이용하여 엔티티 찾기
        Optional<PartnerEntity> optional = partnerRepository.findById( partnerDto.getPartnerId() );
        if( optional.isPresent() ){ // 존재하면 엔티티 수정한다.
            PartnerEntity entity = optional.get();
            entity.setPartnerId( partnerDto.getPartnerId() );
            entity.setPartnerCode( partnerDto.getPartnerCode() );
            entity.setPartnerName( partnerDto.getPartnerName() );
            entity.setPartnerType( partnerDto.getPartnerType() );
            entity.setContact( partnerDto.getContact() );
            entity.setAddress( partnerDto.getAddress() );
            return true;
        }
        return false;
    }
}