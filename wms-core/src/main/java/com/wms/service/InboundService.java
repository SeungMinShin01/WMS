package com.wms.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.inbound.CarryingDto;
import com.wms.model.dto.inbound.InboundDetailDto;
import com.wms.model.dto.inbound.InboundItemDto;
import com.wms.model.dto.inbound.InboundListDto;
import com.wms.model.dto.inbound.InspectionDto;
import com.wms.model.dto.inbound.InspectionResultDto;
import com.wms.model.dto.inbound.LocationRecommendDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.LocationEntity;
import com.wms.model.entity.StockEntity;
import com.wms.model.repository.DocumentItemDetailRepository;
import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.repository.LocationRepository;


@Service 
@Transactional 
public class InboundService {
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemRepository documentItemRepository;
    @Autowired private DocumentItemDetailRepository documentItemDetailRepository;
    @Autowired private StockService stockService;
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
        List<DocumentItemEntity> allItems = documentItemRepository.findAll();
        List<InboundListDto> inboundListDtos = new ArrayList<>();
        inbounds.forEach((documentEntity)->{
            int itemCount = 0;
            int totalQty = 0;
            for(DocumentItemEntity item : allItems){
                if(item.getDocumentEntity().getDocumentId().equals(documentEntity.getDocumentId())){
                    itemCount++;
                    totalQty += item.getExpectedQty();
                }
            }
            inboundListDtos.add(InboundListDto.from(documentEntity, itemCount, totalQty));
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
        // 수량 검사, 0-음수-빈값 보냄x
        if (inspectionDto.getQty() == null || inspectionDto.getQty() <= 0) return null;
        DocumentItemEntity documentItemEntity = documentItemRepository.findById(inspectionDto.getDocumentItemId()).orElse(null);
        // NullPointException error 해결 , 조회 직후 null 이면 멈추기
        if (documentItemEntity == null) return null;
        // 품목이 속한 문서가 입고인지 확인
        if (!documentItemEntity.getDocumentEntity().getType().equals("INBOUND")) return null;
        // 문서 상태가 waiting일때만 허용
        if (!documentItemEntity.getDocumentEntity().getStatus().equals("WAITING")) return null;
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

    // ED-16 적재 1건
    public boolean carry(CarryingDto carryingDto) {
        // 검수 결과 조회
        DocumentItemDetailEntity detailEntity = documentItemDetailRepository.findById(carryingDto.getDetailId()).orElse(null);
        if(detailEntity == null) return false;

        // 취소된 입고의 물건 들여보내지 않기 
        String status = detailEntity.getDocumentItemEntity().getDocumentEntity().getStatus();
        if (status.equals("CANCELED")) return false;

        // 이미 적재됐으면 중복 적재 방지
        if(detailEntity.getLocationEntity() != null) return false;

        // 적재할 칸 조회
        LocationEntity locationEntity = locationRepository.findById(carryingDto.getLocationId()).orElse(null);
        if(locationEntity == null || !locationEntity.getIsActive()) return false;

        // 재고 증가는 StockService에 맡긴다
        StockEntity stockEntity = stockService.increase(detailEntity.getLotEntity(), locationEntity, detailEntity.getQty());

        // 검수 결과에 적재 위치와 재고 연결
        detailEntity.setLocationEntity(locationEntity);
        detailEntity.setStockEntity(stockEntity);
        return true;
    }

    // 적치 추천 (검수 기록 1건 기준)
    public List<LocationRecommendDto> recommend(Integer detailId){
        DocumentItemDetailEntity detail = documentItemDetailRepository.findById(detailId).orElse(null);
        if(detail==null) return new ArrayList<>();
        return stockService.recommend(detail.getLotEntity());
    }
    
}
