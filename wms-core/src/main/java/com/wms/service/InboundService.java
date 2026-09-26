package com.wms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wms.model.dto.inbound.InboundDetailDto;
import com.wms.model.dto.inbound.InboundItemDto;
import com.wms.model.dto.inbound.InboundListDto;
import com.wms.model.dto.inbound.InspectionDto;
import com.wms.model.dto.inbound.InspectionResultDto;
import com.wms.model.dto.inbound.PutawayDto;
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
    // ED - 10
    @Autowired
    private DocumentRepository documentRepository;
    // ED - 13
    @Autowired
    private DocumentItemRepository documentItemRepository;
    // ED - 14
    @Autowired
    private DocumentItemDetailRepository detailRepository;
    // ED - 16
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private StockRepository stockRepository;

    public List<InboundListDto> findAll() {
        // 문서를 전부 다 가져오기 입출고
        List<DocumentEntity> documentEntities = documentRepository.findAll();

        // 입고 필터링
        List<DocumentEntity> inbounds = new ArrayList<>();
        documentEntities.forEach((documentEntity) -> {
            if (documentEntity.getType().equals("INBOUND")) {
                inbounds.add(documentEntity);
            }
        });

        // 예정일이 빠른 순 정렬
        for (int i = 0; i < inbounds.size(); i++) {
            for (int j = i + 1; j < inbounds.size(); j++) {
                if (inbounds.get(j).getExpectedAt().isBefore(inbounds.get(i).getExpectedAt())) {
                    // .isBefore : 날짜끼리 비교 a.isBefore(b) : a가 b보다 이전(<)이냐
                    // LocalDateTime은 숫자가 아니라서 < 로 비교할 수 없기 때문
                    DocumentEntity temp = inbounds.get(i); // 이전 자바에서 했던 Temp를 이용한 순서바꾸기
                    inbounds.set(i, inbounds.get(j));
                    inbounds.set(j, temp);
                }
            }
        }
        // Dto로 변경
        List<InboundListDto> inboundListDtos = new ArrayList<>();
        inbounds.forEach((documentEntity) -> {
            inboundListDtos.add(InboundListDto.from(documentEntity));
        });
        return inboundListDtos;

    }

    // 입고 문서 상세 조회 ED - 13
    public InboundDetailDto detailFindAll(Integer docuemntId) {
        // 문서 하나 가져오기
        DocumentEntity documentEntity = documentRepository.findById(docuemntId).orElse(null);
        InboundDetailDto inboundDetailDto = InboundDetailDto.from(documentEntity);

        List<DocumentItemEntity> documentItemEntities = documentItemRepository.findAll();
        documentItemEntities.forEach((documentItemEntity) -> {
            // 해당 문서에 해당하는 품목만 가져오기
            if (documentItemEntity.getDocumentEntity().getDocumentId().equals(docuemntId)) {
                InboundItemDto inboundItemDto = InboundItemDto.from(documentItemEntity);
                inboundDetailDto.getItems().add(inboundItemDto);
            }
        });
        return inboundDetailDto;
    }

    // 검수 1건 등록 ED - 14
    public Integer inspectionSave(InspectionDto inspectionDto) {
        DocumentItemEntity documentItemEntity = documentItemRepository.findById(inspectionDto.getDocumentItemId())
                .orElse(null);
        DocumentItemDetailEntity detailEntity = inspectionDto.toEntity(documentItemEntity);
        DocumentItemDetailEntity savedEntity = detailRepository.save(detailEntity);

        return savedEntity.getDetailId();
    }

    // 검수 결과 조회 ED - 15
    public List<InspectionResultDto> inspectionFindAll(Integer documentId) {
        List<DocumentItemDetailEntity> detailEntities = detailRepository.findAll();

        List<InspectionResultDto> inspectionResultDtos = new ArrayList<>();
        detailEntities.forEach((detailEntity) -> {
            // 검수결과(detail) -> 문서 품목 (item) -> 문서 (document)
            if (detailEntity.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)) {
                inspectionResultDtos.add(InspectionResultDto.from(detailEntity));
            }
        });
        return inspectionResultDtos;
    }

    // 적재 1건 ED - 16
    public boolean putaway(PutawayDto putawayDto) {
        // 검수 결과 조회
        Optional<DocumentItemDetailEntity> optional = detailRepository.findById(putawayDto.getDetailId());
        if (optional.isPresent()) {
            DocumentItemDetailEntity detailEntity = optional.get();

            // 적재할 location 정보 조회
            Optional<LocationEntity> optional2 = locationRepository.findById(putawayDto.getLocationId());
            if (optional2.isPresent()) {
                LocationEntity locationEntity = optional2.get();

                // 같은 칸에 해당 재고가 있는지 (같은 LOT도 포함) 확인
                StockEntity stockEntity = null;
                for (StockEntity stock : stockRepository.findAll()) {
                    if (stock.getLotEntity().getLotId().equals(detailEntity.getLotEntity().getLotId())
                            && stock.getLocationEntity().getLocationId().equals(locationEntity.getLocationId())) {
                        // 재고의 LOT == 품목 상세 LOT && 재고의 위치 == 지정한 위치
                        stockEntity = stock; // 일치하면 stock을 담아둠 (해당 칸에 동일한 LOT가 있을경우)
                    }
                }
                if (stockEntity == null) {
                    // 해당칸에 동일한 상품이 없을 경우, 새로 만듦
                    stockEntity = StockEntity.builder()
                            .lotEntity(detailEntity.getLotEntity())
                            .locationEntity(locationEntity)
                            .qty(detailEntity.getQty())
                            .build();
                } else {
                    // 이미 있으면, 이전에 담아둔 엔티티에 수량만 더함
                    stockEntity.setQty(stockEntity.getQty() + detailEntity.getQty());
                }
                StockEntity savedEntity = stockRepository.save(stockEntity);

                // 5. 검수 결과에 칸, 재고 연결
                detailEntity.setLocationEntity(locationEntity);
                detailEntity.setStockEntity(savedEntity);
                detailRepository.save(detailEntity);
                return true;
            }
        }
        return false;
    }
}
