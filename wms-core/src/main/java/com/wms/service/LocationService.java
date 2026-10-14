package com.wms.service;

import com.wms.model.repository.StockRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;   // [변경] jakarta → spring (readOnly 사용)
import org.springframework.util.StringUtils;

import com.wms.model.dto.location.LocationDto;
import com.wms.model.entity.LocationEntity;
import com.wms.model.entity.StockEntity;
import com.wms.model.repository.LocationRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional(readOnly = true)   // [추가] 조회는 읽기 전용, 쓰기 메서드는 따로 @Transactional
public class LocationService {
    @Autowired private StockRepository stockRepository;
    @Autowired
    LocationRepository locationRepository;

    LocationService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    // 필수값 검사 (등록, 수정 공통) → 400, 비운 항목을 모아서 알린다
    private void 입력검사(LocationDto dto) {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(dto.getLocationCode())) {
            errors.add("로케이션코드는 필수입니다");
        }
        if (dto.getIsActive() == null) {
            errors.add("사용여부는 필수입니다");
        }
        if (dto.getCapacity() == null) {
            errors.add("적재 가능 수량은 필수입니다");
        } else if (dto.getCapacity() < 0) {
            errors.add("적재 가능 수량은 0 이상입니다");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }

    // 로케이션코드 중복이면 거부 → 409 (excludeId: 수정할 때 자기 자신 제외)
    private void 중복검사(String code, Integer excludeId) {
        for (LocationEntity l : locationRepository.findAll()) {
            boolean sameCode = l.getLocationCode().equals(code);
            boolean self = excludeId != null && l.getLocationId().equals(excludeId);
            if (sameCode && !self) {
                throw new IllegalStateException("이미 등록된 로케이션코드입니다: " + code);
            }
        }
    }

    // [변경] boolean → Integer (새 로케이션 번호를 돌려준다)
    @Transactional
    public Integer 위치등록(LocationDto locationDto) {
        입력검사(locationDto);                                   // 400
        중복검사(locationDto.getLocationCode(), null);           // 409
        LocationEntity saved = locationRepository.save(locationDto.toEntity());
        return saved.getLocationId();
    }

    // [그대로] 클래스의 readOnly 트랜잭션이 적용된다
    public List<LocationDto> 위치전체조회() {
        // 1. 로케이션 가져오기
        List<LocationEntity> entities = locationRepository.findAll(); // findAll 전체조회
        
        // 2. 재고를 한번에 가져오기
        List<StockEntity> allStocks = stockRepository.findAllForRecommend();

        // 3. 재고를 칸 번호별로 나눠 담기
        Map<Integer, List<StockEntity>> stocksByLocation = new HashMap<>();
        for(StockEntity stock : allStocks){
            Integer locationId = stock.getLocationEntity().getLocationId(); // 재고가 있는 칸 번호

            // 번호가 처음 나왔으면 빈 리스트 먼저 만들기
            if(!stocksByLocation.containsKey(locationId)){
                stocksByLocation.put(locationId, new ArrayList<>());
            }
            // 그 칸 리스트에 재고 추가
            stocksByLocation.get(locationId).add(stock);
        } 
        // 칸 하나씩 DTO로 바꾸기, 실제 적재수량-혼용 채우기
        List<LocationDto> list = new ArrayList<>();
        for(LocationEntity entity : entities){
            LocationDto dto = LocationDto.from(entity);

            // 칸의 재고 목록 꺼내기 
            List<StockEntity> stocks = new ArrayList<>();
            if(stocksByLocation.containsKey(entity.getLocationId())){
                stocks = stocksByLocation.get(entity.getLocationId());
            }

            // 실제 적재수량 - 칸 재고 수량 전부 더한 값
            int storedQty = 0;
            for(StockEntity stock : stocks){
                storedQty = storedQty + stock.getQty();
            }
            dto.setStoredQty(storedQty);

            // 혼용 = 칸에 LOT가 2개 이상이면 TRUE
            if(stocks.size() >= 2){
                dto.setMixed(true);
            }else{
                dto.setMixed(false);
            }
            list.add(dto);
        }
        return list;
    }


    // [변경] 없으면 null(200)이 아니라 404
    public LocationDto 위치개별조회(int locationid) {
        LocationEntity entity = locationRepository.findById(locationid)
                .orElseThrow(() -> new EntityNotFoundException("로케이션이 없습니다: " + locationid));
        return LocationDto.from(entity);
    }

    // [변경] 없으면 true가 아니라 404, 필수값 400, 코드 중복 409(자기 자신 제외)
    @Transactional
    public boolean 위치수정(LocationDto locationDto) {
        if (locationDto.getLocationId() == null) {
            throw new IllegalArgumentException("로케이션 번호가 없습니다");
        }
        LocationEntity entity = locationRepository.findById(locationDto.getLocationId())
                .orElseThrow(() -> new EntityNotFoundException("로케이션이 없습니다: " + locationDto.getLocationId()));

        입력검사(locationDto);                                                       // 400
        중복검사(locationDto.getLocationCode(), entity.getLocationId());              // 409

        entity.setLocationCode(locationDto.getLocationCode());
        entity.setCapacity(locationDto.getCapacity());
        entity.setIsActive(locationDto.getIsActive());
        return true;
    }
}