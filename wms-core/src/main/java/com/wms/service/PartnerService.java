package com.wms.service;

import java.lang.StackWalker.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import com.wms.model.dto.partner.PartnerDto;
import com.wms.model.dto.product.ProductDto;
import com.wms.model.entity.PartnerEntity;
import com.wms.model.repository.PartnerRepository;


@Service
public class PartnerService {
    @Autowired private PartnerRepository partnerRepository;

    public boolean 거래처등록( PartnerDto partnerDto ){
        PartnerEntity entity = partnerDto.toEntity(); // 1. DTO -> ENTITY
        PartnerEntity savedEntity = partnerRepository.save( entity ); // 2. entity save
        if( savedEntity.getPartnerId() >= 1 ){
            return true;
        } else {
            return false;
        }
    }

    // http://localhost:8080/wms/partner?partner_id
    public PartnerDto 거래처개별조회( int partnerid ){
        Optional<PartnerEntity> optional = partnerRepository.findById( partnerid ); // 1. findById 엔티티 개별조회
        if( optional.isPresent() ) { // 2. 조회 결과 존재하면
            PartnerEntity entity = optional.get(); // 3. 엔티티 꺼내기
            return PartnerDto.from(entity);
    }
    return  null;
    }
    

        public List<PartnerDto> 거래처전체조회(){
            List<PartnerEntity> entities = partnerRepository.findAll(); // 1. findAll 엔티티 전체조회
            List<PartnerDto> list = new ArrayList<>(); // 2. 엔티티 -> dto 변환
            entities.forEach( (entity) -> {
                PartnerDto dto = PartnerDto.from( entity );
                list.add( dto );
            });
            return list;
        }
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
        return  false;    
    }
}