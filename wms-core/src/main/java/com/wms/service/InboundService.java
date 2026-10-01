package com.wms.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.wms.model.dto.inbound.LocationOptionDto;
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
import com.wms.model.entity.DocumentStatus;
import com.wms.model.entity.DocumentType;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional
public class InboundService {
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentItemRepository documentItemRepository;
    @Autowired
    private DocumentItemDetailRepository documentItemDetailRepository;
    @Autowired
    private StockService stockService;
    // ED-16
    @Autowired
    private LocationRepository locationRepository;

    // ED-10 입고 문서 목록 조회
    public List<InboundListDto> findAll() {
        // 문서 모두 조회
        List<DocumentEntity> documentEntities = documentRepository.findAll();

        // inbound만 필터링
        List<DocumentEntity> inbounds = new ArrayList<>();
        documentEntities.forEach((documentEntity) -> {
            if (documentEntity.getType() == DocumentType.INBOUND) {
                inbounds.add(documentEntity);
            }
        });

        // 3. 예정일 빠른 순 정렬
        // compareTo: a가b보다 빠르면/이르면 음수(앞), 같으면 0, 늦으면 양수(뒤)
        // LocalDateTime은 숫자가 아니라서 < 부등호로 비교x
        inbounds.sort((a, b) -> a.getExpectedAt().compareTo(b.getExpectedAt()));

        // 4. Dto 변경
        List<DocumentItemEntity> allItems = documentItemRepository.findAll();
        List<InboundListDto> inboundListDtos = new ArrayList<>();
        inbounds.forEach((documentEntity) -> {
            int itemCount = 0;
            int totalQty = 0;
            for (DocumentItemEntity item : allItems) {
                if (item.getDocumentEntity().getDocumentId().equals(documentEntity.getDocumentId())) {
                    itemCount++;
                    totalQty += item.getExpectedQty();
                }
            }
            inboundListDtos.add(InboundListDto.from(documentEntity, itemCount, totalQty));
        });
        return inboundListDtos;
    }

    // ED-13 입고 문서 상세 조회
    public InboundDetailDto detailFind(Integer documentId) {
        // 문서 하나 조회
        DocumentEntity documentEntity = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("입고 문서가 없습니다."));
        InboundDetailDto inboundDetailDto = InboundDetailDto.from(documentEntity);

        // 문서에 포함된 품목 가져오기
        List<DocumentItemEntity> documentItemEntities = documentItemRepository.findAll();
        documentItemEntities.forEach((documentItemEntity) -> {
            if (documentItemEntity.getDocumentEntity().getDocumentId().equals(documentId)) {
                InboundItemDto inboundItemDto = InboundItemDto.from(documentItemEntity);
                inboundDetailDto.getItems().add(inboundItemDto);
            }
        });
        return inboundDetailDto;
    }

    // ED-14 검수 결과 1건 등록
    public Integer inspectionSave(InspectionDto inspectionDto) {
        // 수량 검사, 0-음수-빈값 보냄x
        if (inspectionDto.getQty() == null || inspectionDto.getQty() <= 0)
            throw new IllegalArgumentException("검수 수량은 1 이상이어야 합니다.");
        DocumentItemEntity documentItemEntity = documentItemRepository.findById(inspectionDto.getDocumentItemId())
                .orElseThrow(() -> new EntityNotFoundException("문서 품목이 없습니다."));

        // 품목이 속한 문서가 입고인지 확인
        if (documentItemEntity.getDocumentEntity().getType() != DocumentType.INBOUND)
            throw new IllegalArgumentException("입고 문서의 품목이 아닙니다.");

        DocumentEntity documentEntity = documentItemEntity.getDocumentEntity();
        // 검수는 대기(waiting) 문서만 - 전 품목 검수 끝나면 검수 완료(inspected)로 넘어감
        if (documentEntity.getStatus() != DocumentStatus.WAITING)
            throw new IllegalStateException("대기 상태 문서만 검수할 수 있습니다. (현재: " + documentEntity.getStatus() + ")");
        DocumentItemDetailEntity detailEntity = inspectionDto.toEntity(documentItemEntity);
        DocumentItemDetailEntity savedEntity = documentItemDetailRepository.save(detailEntity);

        // 문서의 모든 품목이 검수됐으면 대기 -> 검수 완료
        if (allItemsInspected(documentEntity.getDocumentId()))
            documentEntity.moveTo(DocumentStatus.INSPECTED);
        return savedEntity.getDetailId();
    }

    // ED-15 검수 결과 조회
    public List<InspectionResultDto> inspectionFindAll(Integer documentId) {
        List<DocumentItemDetailEntity> detailEntities = documentItemDetailRepository.findAll();
        List<InspectionResultDto> inspectionResultDtos = new ArrayList<>();
        detailEntities.forEach((detailEntity) -> {
            if (detailEntity.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)) {
                inspectionResultDtos.add(InspectionResultDto.from(detailEntity));
            }
        });
        return inspectionResultDtos;
    }

    // ED-16 적재 1건
    public boolean carry(CarryingDto carryingDto) {
        // 검수 결과 조회
        DocumentItemDetailEntity detailEntity = documentItemDetailRepository.findById(carryingDto.getDetailId())
                .orElseThrow(() -> new EntityNotFoundException("검수 기록이 없습니다."));

        DocumentEntity documentEntity = detailEntity.getDocumentItemEntity().getDocumentEntity();
        // 적재는 검수 완료(INSPECTED) 문서만 — 대기·취소·완료 문서는 거부
        if (documentEntity.getStatus() != DocumentStatus.INSPECTED)
            throw new IllegalStateException("전 품목 검수가 끝난 문서만 적재할 수 있습니다 (현재: " + documentEntity.getStatus() + ")");

        // 이미 적재됐으면 중복 적재 방지
        if (detailEntity.getLocationEntity() != null)
            throw new IllegalStateException(
                    "이미 적재된 검수 기록입니다. (위치: " + detailEntity.getLocationEntity().getLocationCode() + ")");

        // 적재할 칸 조회
        LocationEntity locationEntity = locationRepository.findById(carryingDto.getLocationId())
                .orElseThrow(() -> new EntityNotFoundException("로케이션이 없습니다."));
        if (!locationEntity.getIsActive())
            throw new IllegalArgumentException("미사용 로케이션입니다. (" + locationEntity.getLocationCode() + ")");

        // 재고 증가는 StockService에 맡긴다
        StockEntity stockEntity = stockService.increase(detailEntity.getLotEntity(), locationEntity,
                detailEntity.getQty());

        // 검수 결과에 적재 위치와 재고 연결
        detailEntity.setLocationEntity(locationEntity);
        detailEntity.setStockEntity(stockEntity);
        if (allDetailsCarried(documentEntity.getDocumentId())) {
            documentEntity.moveTo(DocumentStatus.COMPLETED);
            documentEntity.setCompletedAt(LocalDateTime.now());
        }
        return true;
    }

    // 문서의 모든 품목 줄에 검수 기록이 있는가
    private boolean allItemsInspected(Integer documentId) {
        List<DocumentItemDetailEntity> details = documentItemDetailRepository.findAll();
        for (DocumentItemEntity item : documentItemRepository.findAll()) {
            if (!item.getDocumentEntity().getDocumentId().equals(documentId))
                continue;
            boolean inspected = false;
            for (DocumentItemDetailEntity d : details) {
                if (d.getDocumentItemEntity().getDocumentItemId().equals(item.getDocumentItemId())) {
                    inspected = true;
                    break;
                }
            }
            if (!inspected)
                return false;
        }
        return true;
    }

    // 문서의 모든 검수 기록이 적재됐는가
    private boolean allDetailsCarried(Integer documentId) {
        for (DocumentItemDetailEntity d : documentItemDetailRepository.findAll()) {
            if (d.getDocumentItemEntity().getDocumentEntity().getDocumentId().equals(documentId)
                    && d.getLocationEntity() == null)
                return false;
        }
        return true;
    }

    // 적치 추천 (검수 기록 1건 기준) — 없는 검수 기록이면 404
    public List<LocationRecommendDto> recommend(Integer detailId, boolean mixLot) {
        DocumentItemDetailEntity detail = documentItemDetailRepository.findById(detailId)
                .orElseThrow(() -> new EntityNotFoundException("검수 기록이 없습니다."));
        // LOT , 수량꺼내서 재고 쪽으로 넘기기
        return stockService.recommend(detail.getLotEntity(), detail.getQty(), mixLot);
    }

    // 적치 로케이션 선택 목록 - 사용 중인 칸만, 코드 순
    public List<LocationOptionDto> locationFindAll() {
        List<LocationOptionDto> list = new ArrayList<>();
        locationRepository.findAll().forEach((loc) -> {
            if (loc.getIsActive())
                list.add(LocationOptionDto.from(loc));
        });
        list.sort(Comparator.comparing(LocationOptionDto::getLocationCode));
        return list;
    }
}