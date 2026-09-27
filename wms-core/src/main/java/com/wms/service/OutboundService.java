package com.wms.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wms.model.dto.outbound.AllocationDto;
import com.wms.model.dto.outbound.ConfirmShipmentDto;
import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundItemDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.model.dto.outbound.PickingListDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.StockEntity;
import com.wms.model.repository.DocumentItemDetailRepository;
import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.repository.StockRepository;

@Service
@Transactional 
public class OutboundService {
    // ED-12
    @Autowired private DocumentRepository documentRepository;
    // ED-17, ED-18
    @Autowired private DocumentItemRepository documentItemRepository;
    // ED-18
    @Autowired private StockRepository stockRepository;
    // ED-18
    @Autowired private DocumentItemDetailRepository documentItemDetailRepository;


    
    // ED-12 출고 문서 목록 조회
    public List<OutboundListDto> getOutboundList() {
        // 문서타입이 OUTBOUND만 가져오기
        List<DocumentEntity> documentEntities = documentRepository.findByTypeOrderByExpectedAtAsc("OUTBOUND");
        List<OutboundListDto> documentDtos = documentEntities.stream().map((entity) -> {return OutboundListDto.from(entity);}).toList();
        return documentDtos;
    }

    // ED-17 출고 문서 상세 조회
    public OutboundDetailDto getOutboundDetail(Integer documentId) {
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElse(null);
        OutboundDetailDto outboundDetailDto = OutboundDetailDto.from(documentEntity);

        
        List<DocumentItemEntity> documentItemEntities = documentItemRepository.findAll();
        documentItemEntities.forEach((documentItemEntity) -> {
        
            if (documentItemEntity.getDocumentEntity().getDocumentId().equals(documentId)) {
                OutboundItemDto outboundItemDto = OutboundItemDto.from(documentItemEntity);
                outboundDetailDto.getItems().add(outboundItemDto);
            }
        });
        return outboundDetailDto;
    }

    // ED-18 할당 1건 (피킹리스트 생성)
    public Integer allocate(AllocationDto allocationDto) {
        DocumentItemEntity documentItemEntity = documentItemRepository.findById(allocationDto.getDocumentItemId()).orElse(null);
        StockEntity stockEntity = stockRepository.findById(allocationDto.getStockId()).orElse(null);

        DocumentItemDetailEntity detailEntity = allocationDto.toEntity(documentItemEntity, stockEntity);
        DocumentItemDetailEntity savedDetail = documentItemDetailRepository.save(detailEntity);

        stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() + allocationDto.getQty());
        stockRepository.save(stockEntity);

        return savedDetail.getDetailId();
    }

    // ED-19 피킹 리스트 조회
    public List<PickingListDto> getPickingList(Integer documentId){
        List<DocumentItemDetailEntity> detailEntities = documentItemDetailRepository.findAll();

        List<DocumentItemDetailEntity> filtered = new ArrayList<>();
        detailEntities.forEach( (detailEntity) -> {
            if (detailEntity.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)) {
                filtered.add(detailEntity);
            }
        });
    
        for (int i = 0; i < filtered.size(); i++) {
            for (int j = i + 1; j < filtered.size(); j++) {
                String codeI = filtered.get(i).getLocationEntity().getLocationCode();
                String codeJ = filtered.get(j).getLocationEntity().getLocationCode();
                if (codeJ.compareTo(codeI) < 0) {
                    DocumentItemDetailEntity temp = filtered.get(i);
                    filtered.set(i, filtered.get(j));
                    filtered.set(j, temp);
                }
            }
        }

        List<PickingListDto> pickingListDtos = new ArrayList<>();
        filtered.forEach((detailEntity) -> {
            pickingListDtos.add(PickingListDto.from(detailEntity));
        });
        return pickingListDtos;
    }
    
    // ED-20 출고확정 
    public Boolean confirmShipment(ConfirmShipmentDto confirmShipmentDto) {
        DocumentItemDetailEntity detailEntity = documentItemDetailRepository.findById(confirmShipmentDto.getDetailId()).orElse(null);
        
        if (detailEntity == null) {
            return false;
        }

        StockEntity stockEntity = detailEntity.getStockEntity();
        stockEntity.setQty(stockEntity.getQty() - detailEntity.getQty());
        stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() - detailEntity.getQty());
        stockRepository.save(stockEntity);

        return true;
    }
}
