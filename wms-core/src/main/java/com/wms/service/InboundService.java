package com.wms.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import com.wms.model.dto.inbound.CarryingDto;
import com.wms.model.dto.inbound.InboundDetailDto;
import com.wms.model.dto.inbound.InboundItemDto;
import com.wms.model.dto.inbound.InboundListDto;
import com.wms.model.dto.inbound.InspectionDto;
import com.wms.model.dto.inbound.InspectionResultDto;
import com.wms.model.dto.inbound.StockDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.LocationEntity;
import com.wms.model.entity.StockEntity;
import com.wms.model.repository.DocumentItemDetailRepository;
import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.repository.LocationRepository;
import com.wms.model.repository.StockRepository;

import jakarta.transaction.Transactional;

@Service 
@Transactional 
public class InboundService {
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemRepository documentItemRepository;
    @Autowired private DocumentItemDetailRepository documentItemDetailRepository;
    @Autowired private StockRepository stockRepository;
    // ED-16
    @Autowired private LocationRepository locationRepository;

    // ED-10 입고 문서 목록 조회
    public List<InboundListDto> findAll(){
        // 문서 모두 조회
        List<DocumentEntity> documentEntities = documentRepository.findAll();

        // inbound만 필터링
        List<DocumentEntity> inbounds = new ArrayList<>();
        documentEntities.forEach((documentEntity)->{
            if(documentEntity.getType().equals("INBOUND")){
                inbounds.add(documentEntity);
            }
        });

        // 3. 예정일 빠른 순 정렬
        // compareTo: a가b보다 빠르면/이르면 음수(앞), 같으면 0, 늦으면 양수(뒤)
        // LocalDateTime은 숫자가 아니라서 < 부등호로 비교x
        inbounds.sort((a,b)->a.getExpectedAt().compareTo(b.getExpectedAt()));

        // 4. Dto 변경
        List<InboundListDto> inboundListDtos = new ArrayList<>();
        inbounds.forEach((documentEntity)->{
            inboundListDtos.add(InboundListDto.from(documentEntity));
        });
        return inboundListDtos;
    }

    // ED-13 입고 문서 상세 조회
    public InboundDetailDto detailFind(Integer documentId){
        // 문서 하나 조회
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElse(null);
        InboundDetailDto inboundDetailDto = InboundDetailDto.from(documentEntity);

        // 문서에 포함된 품목 가져오기
        List<DocumentItemEntity> documentItemEntities = documentItemRepository.findAll();
        documentItemEntities.forEach((documentItemEntity)->{
            if(documentItemEntity.getDocumentEntity().getDocumentId().equals(documentId)){
                InboundItemDto inboundItemDto = InboundItemDto.from(documentItemEntity);
                inboundDetailDto.getItems().add(inboundItemDto);
            }
        });
        return inboundDetailDto;
    }

    // ED-14 검수 결과 1건 등록
    public Integer inspectionSave(InspectionDto inspectionDto) {
        DocumentItemEntity documentItemEntity = documentItemRepository.findById(inspectionDto.getDocumentItemId()).orElse(null);
        DocumentItemDetailEntity detailEntity = inspectionDto.toEntity(documentItemEntity);
        DocumentItemDetailEntity savedEntity = documentItemDetailRepository.save(detailEntity);
        return savedEntity.getDetailId();
    }

    // ED-15 검수 결과 조회
    public List<InspectionResultDto> inspectionFindAll(Integer documentId) {
        List<DocumentItemDetailEntity> detailEntities = documentItemDetailRepository.findAll();
        List<InspectionResultDto> inspectionResultDtos = new ArrayList<>();
        detailEntities.forEach((detailEntity)->{
            if(detailEntity.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)){
                inspectionResultDtos.add(InspectionResultDto.from(detailEntity));
            }
        });
        return inspectionResultDtos;
    }

    // ED-21 재고 조회
     public List<StockDto> stockFindAll() {
        List<StockEntity> stockEntities = stockRepository.findAll();
        // FEFO정렬: 유통기한 오름차순 -> 유통기한없는 lot는 맨 뒤로 -> 같으면 stockId순으로 
        stockEntities.sort(
            Comparator.comparing((StockEntity s)->s.getLotEntity().getExpiryDate(),
            Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StockEntity::getStockId)
        );
        List<StockDto> stockDtos = new ArrayList<>();
        stockEntities.forEach(stockEntity -> stockDtos.add(StockDto.from(stockEntity)));
        return stockDtos;
    }

    // ED-16 적재 1건
    public boolean carry(CarryingDto carryingDto) {
        // 검수 결과 조회
        DocumentItemDetailEntity detailEntity = documentItemDetailRepository.findById(carryingDto.getDetailId()).orElse(null);
        if(detailEntity == null) return false;

        // 이미 적재됐으면 중복 적재 방지
        if(detailEntity.getLocationEntity() != null) return false;

        // 적재할 칸 조회
        LocationEntity locationEntity = locationRepository.findById(carryingDto.getLocationId()).orElse(null);
        if(locationEntity == null || !locationEntity.getIsActive()) return false;

        // 같은 lot + 같은 칸의 재고 찾기
        StockEntity stockEntity = null;
        List<StockEntity> stockEntities = stockRepository.findAll();
        for(StockEntity s : stockEntities){
            if(s.getLotEntity().getLotId().equals(detailEntity.getLotEntity().getLotId())
                    && s.getLocationEntity().getLocationId().equals(locationEntity.getLocationId())){
                        stockEntity = s;
                        break;
                }
        }
        if(stockEntity != null){
            // 있으면 수량 +
            stockEntity.setQty(stockEntity.getQty() + detailEntity.getQty());
        }else {
            // 없으면 새재고 생성
            stockEntity = StockEntity.builder()
            .lotEntity(detailEntity.getLotEntity())
            .locationEntity(locationEntity)
            .qty(detailEntity.getQty())
            .build();
            stockEntity = stockRepository.save(stockEntity);
        }

        // 검수 결과에 적재 위치와 재고 연결
        detailEntity.setLocationEntity(locationEntity);
        detailEntity.setStockEntity(stockEntity);
        return true;
    }

    
}
