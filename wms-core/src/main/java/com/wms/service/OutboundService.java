package com.wms.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wms.model.repository.DocumentItemDetailRepository;
import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.repository.StockRepository;
import com.wms.model.dto.outbound.AllocationDto;
import com.wms.model.dto.outbound.OutboundDetailDto;
import com.wms.model.dto.outbound.OutboundItemDto;
import com.wms.model.dto.outbound.OutboundListDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemDetailEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.StockEntity;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class OutboundService {
    // ED - 12
    @Autowired
    private DocumentRepository documentRepository;
    // ED - 17
    @Autowired
    private DocumentItemRepository documentItemRepository;

    // ED - 18
    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DocumentItemDetailRepository detailRepository;

    // 출고 문서 전부 가져오기 ED - 12
    public List<OutboundListDto> findAll() {
        List<DocumentEntity> documentEntities = documentRepository.findAll();

        return documentEntities.stream()
                // OUTBOUND 문서 필터
                .filter((entity) -> entity.getType().equals("OUTBOUND"))
                // sorted 무엇을 기준으로 정렬할지
                // comparedTo : a가 b보다 이르면 음수(앞), 같으면 0, 늦으면 양수(뒤) 반환
                .sorted((a, b) -> a.getExpectedAt().compareTo(b.getExpectedAt()))
                .map((entity) -> OutboundListDto.from(entity))
                .toList();
    }

    // 출고 문서 상세 조회 ED - 17
    public OutboundDetailDto detailFindAll(Integer documentId) {
        // 문서 하나 가져오기
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElse(null);
        OutboundDetailDto detailDto = OutboundDetailDto.from(documentEntity);

        // 해당 문서에 해당하는 품목만 가져오기
        List<OutboundItemDto> items = documentItemRepository.findAll().stream()
                .filter((item) -> {
                    return item.getDocumentEntity().getDocumentId().equals(documentId);
                })
                .map((item) -> {
                    return OutboundItemDto.from(item);
                })
                .toList();
        detailDto.setItems(items);
        return detailDto;
    }

    // 할당 1건 ED - 18
    public Integer allocationSave(AllocationDto allocationDto) {
        // 예시 요청 : { "documentItemId": 7, "stockId": 1, "qty": 20 }
        // 문서 품목 ID (1) , 재고 ID (2) , 수량 (3)
        // 문서에서 일치하는 품목 찾기 (1)
        DocumentItemEntity documentItemEntity = documentItemRepository
                .findById(allocationDto.getDocumentItemId())
                .orElse(null);
        // 작업자가 선택한 재고 찾기 (2)
        StockEntity stockEntity = stockRepository
                .findById(allocationDto.getStockId()).orElse(null);
        // 해당 재고에 선점(예약) 걸기 (실제 재고는 변화없음) (3)
        stockEntity.setAllocatedQty(stockEntity.getAllocatedQty() + allocationDto.getQty());
        stockRepository.save(stockEntity);

        // 할당 결과 저장
        DocumentItemDetailEntity detailEntity = allocationDto.toEntity(documentItemEntity, stockEntity);
        DocumentItemDetailEntity savedEntity = detailRepository.save(detailEntity);

        return savedEntity.getDetailId();

    }
}
