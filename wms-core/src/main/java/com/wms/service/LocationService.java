package com.wms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wms.model.dto.location.LocationDto;
import com.wms.model.entity.LocationEntity;
import com.wms.model.repository.LocationRepository;

import jakarta.transaction.Transactional;

@Service
public class LocationService {
    @Autowired
    LocationRepository locationRepository;

    public boolean 위치등록(LocationDto locationDto) {
        LocationEntity entity = locationDto.toEntity();
        LocationEntity savedEntity = locationRepository.save(entity);
        if (savedEntity.getLocationId() >= 1) {
            return true;
        } else {
            return false;
        }
    }

    public List<LocationDto> 위치전체조회() {
        List<LocationEntity> entities = locationRepository.findAll(); // findAll 전체조회
        List<LocationDto> list = new ArrayList<>(); // 2. 엔티티 -> dto 변환
        entities.forEach((entity) -> {
            LocationDto dto = LocationDto.from(entity);
            list.add(dto);
        });
        return list;

    }
    public LocationDto 위치개별조회(int locationid) {
        Optional<LocationEntity> optional = locationRepository.findById(locationid);
        if(optional.isPresent()) { // 2. 조회 결과 존재하면
            LocationEntity entity = optional.get(); // 3. 엔티티 꺼내기
            return LocationDto.from(entity);
        }
        return  null;
    }

    @Transactional
    public boolean 위치수정( LocationDto locationDto ) {
        Optional<LocationEntity> optional
            = locationRepository.findById( locationDto.getLocationId() );
        if( optional.isPresent() ){ // 2. 존재하면 엔티티 수정한다.
            LocationEntity entity = optional.get();
            entity.setLocationCode( locationDto.getLocationCode() );
            entity.setCapacity( locationDto.getCapacity() );
            entity.setIsActive( locationDto.getIsActive() );
            return true;
        }
return true;
    }
}
