package com.wms.service;

import com.wms.model.repository.StockRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wms.model.dto.location.LocationDto;
import com.wms.model.entity.LocationEntity;
import com.wms.model.entity.StockEntity;
import com.wms.model.repository.LocationRepository;

import jakarta.transaction.Transactional;

@Service
public class LocationService {
    @Autowired private StockRepository stockRepository;
    @Autowired
    LocationRepository locationRepository;

    LocationService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

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
