package com.wms.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.wms.model.dto.inbound.InboundCreateDto;
import com.wms.model.dto.inbound.InboundItemCreateDto;
import com.wms.model.dto.inbound.RegisterOptionDto;
import com.wms.model.entity.DocumentEntity;
import com.wms.model.entity.DocumentItemEntity;
import com.wms.model.entity.DocumentStatus;
import com.wms.model.entity.DocumentType;
import com.wms.model.entity.LotEntity;
import com.wms.model.entity.PartnerEntity;
import com.wms.model.entity.ProductEntity;
import com.wms.model.entity.TenantEntity;
import com.wms.model.repository.DocumentItemRepository;
import com.wms.model.repository.DocumentRepository;
import com.wms.model.repository.LotRepository;
import com.wms.model.repository.PartnerRepository;
import com.wms.model.repository.ProductRepository;
import com.wms.model.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

// ED-60 입고문서 등록
@Service 
@Transactional 
public class InboundRegisterService {
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentItemRepository documentItemRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LotRepository lotRepository;

    // 입고 문서 헤더 등록 -> 생성된 documentId 반환
    public Integer createDocument(InboundCreateDto dto){
        // 필수값 400
        if(dto.getTenantId() == null || dto.getPartnerId() == null || dto.getExpectedDate() == null){
        throw new IllegalArgumentException("화주, 거래처, 입고예정일은 필수입니다");
    }
    
    // 존재 확인 404
    TenantEntity tenant = tenantRepository.findById(dto.getTenantId())
        .orElseThrow(() -> new EntityNotFoundException("화주를 찾을 수 없습니다."));
    PartnerEntity partner = partnerRepository.findById(dto.getPartnerId())
        .orElseThrow(() -> new EntityNotFoundException("거래처를 찾을 수 없습니다."));
    
    // 거래처는 같은 화주 소속이어야함 400
    if(!partner.getTenantEntity().getTenantId().equals(tenant.getTenantId())){
        throw new IllegalArgumentException("해당 화주의 거래처가 아닙니다.");
    }
    // 입고는 공급사만 400 (partnerType은 String 필드)
    if(!"SUPPLIER".equals(partner.getPartnerType())){
        throw new IllegalArgumentException("입고 거래처는 공급사만 가능합니다.");
    }
    String documentNo = nextDocumentNo(tenant, "IN");
    DocumentEntity saved = documentRepository.save(dto.toEntity(tenant, partner, documentNo));
    return saved.getDocumentId();
    }

    // 문서번호 생성: 화주코드-IN-오늘날짜-순번 (ex: HTL-IN-20261003-001)
    private String nextDocumentNo(TenantEntity tenant, String typeCode){
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = tenant.getTenantCode() + "-" + typeCode + "-" + today + "-";

        Optional<DocumentEntity> last = documentRepository.findTopByDocumentNoStartingWithOrderByDocumentNoDesc(prefix);
        int next = 1;
        if(last.isPresent()){
            String lastNo = last.get().getDocumentNo(); // // HLT-IN-20261003-005
            next = Integer.parseInt(lastNo.substring(prefix.length()))+1;   // 005 -> 006
        }
        return prefix + String.format("%03d", next);    // 006
    }

    // 입고 문서에 품목 1줄 등록 -> 생성된 documentItemId 반환
    public Integer createItem(Integer documentId, InboundItemCreateDto dto){
        // 필수값 400, StringUtils.hasText는 null,빈 문자열, 공백만 있는 경우를 모두 걸러줌
        if (dto.getProductId() == null || dto.getManufactureDate() == null || dto.getExpiryDate() == null) {
            throw new IllegalArgumentException("품목, 제조일자, 소비기한은 필수입니다.");
        }
        if (dto.getManufactureDate().isAfter(dto.getExpiryDate())) {
            throw new IllegalArgumentException("제조일자가 소비기한보다 늦을 수 없습니다.");
        }
        if (dto.getExpectedQty() == null || dto.getExpectedQty() <= 0) {
            throw new IllegalArgumentException("예정 수량은 1 이상이어야 합니다.");
        }
        // 문서 확인
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("문서를 찾을 수 없습니다."));
        if (document.getType() != DocumentType.INBOUND) {
            throw new IllegalArgumentException("입고 문서가 아닙니다.");
        }
        if (document.getStatus() != DocumentStatus.WAITING) {
            throw new IllegalStateException("대기 상태의 문서에만 품목을 추가할 수 있습니다.");
        }
        // 품목 확인 + 문서와 같은 화주인지
        ProductEntity product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("품목을 찾을 수 없습니다."));
        if (!product.getTenantEntity().getTenantId().equals(document.getTenantEntity().getTenantId())) {
            throw new IllegalArgumentException("문서의 화주와 다른 화주의 품목입니다.");
        }

        // LOT 번호 = LOT-제조일자-공급사코드-순번
        // 같은품목 + 같은 제조일자 + 같은공급사 = 같은 LOT -> 재사용
        String prefix = "LOT-" + dto.getManufactureDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                        + "-" + document.getPartnerEntity().getPartnerCode() + "-";
        LotEntity lot;
        Optional<LotEntity> found = lotRepository.findFirstByProductEntityAndLotCodeStartingWith(product, prefix);
        if(found.isPresent()){
            lot = found.get();
            if(!dto.getExpiryDate().equals(lot.getExpiryDate())){
                throw new IllegalStateException("같은 LOT(" + lot.getLotCode() + ")인데 소비기한이 다릅니다. (기존: " + lot.getExpiryDate() + ")");
            }
            if(documentItemRepository.existsByDocumentEntityAndLotEntity(document, lot)){
                throw new IllegalStateException("이 문서에 이미 등록된 LOT입니다.");
            }
        }else{
            lot = lotRepository.save(LotEntity.builder()
                                .productEntity(product)
                                .lotCode(nextLotCode(prefix))
                                .expiryDate(dto.getExpiryDate())
                                .build());
        }
        DocumentItemEntity saved = documentItemRepository.save(dto.toEntity(document, product, lot));
        return saved.getDocumentItemId();
    }       
    
    // LOT 순번: 같은 제조일자·공급사 접두어 중 가장 큰 번호 +1 (문서번호 생성과 같은 방식)
    private String nextLotCode(String prefix){
        Optional<LotEntity> last = lotRepository.findTopByLotCodeStartingWithOrderByLotCodeDesc(prefix);
        int next = 1;
        if(last.isPresent()){
            next = Integer.parseInt(last.get().getLotCode().substring(prefix.length())) + 1; // 01 -> 02
        }
        return prefix + String.format("%02d", next);
    }

    // =============== 엑셀 일괄 등록 ===============
    // 한줄 = 품목 1줄. 화주+공급사+입고예정일이 같은 줄은 한 문서로 묶음
    // 열: A 화주코드 | B 공급사코드 | C 입고예정일 | D 품목코드 | E 제조일자 | F 소비기한 | G 예정수량
    // 한 줄이라도 틀리면 전체 취소 (클래스 @Transactional → 예외가 나면 전부 롤백)
    public String uploadExcel(MultipartFile file){
        if(file == null || file.isEmpty())
            throw new IllegalArgumentException("엑셀 파일을 선택하세요.");

        Map<String, Integer> documents = new LinkedHashMap<>(); // "화주|공급사|날짜" → documentId
        int itemCount = 0;

        try(InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)){
            Sheet sheet = workbook.getSheetAt(0);
            for(int i = 1; i <= sheet.getLastRowNum(); i++){    // 0행은 제목 줄
                Row row = sheet.getRow(i);
                if(row == null || text(row.getCell(0)).isEmpty()) continue; // 빈 줄 건너뛰기
                int rowNo = i + 1;  // 엑셀 화면에 보이는 행 번호

                try{
                    // 1. 코드 -> 엔티티 (코드는 화주별로 유일해서 화주를 먼저 찾음)
                    TenantEntity tenant = tenantRepository.findByTenantCode(text(row.getCell(0)))
                            .orElseThrow(()-> new EntityNotFoundException("화주코드가 없습니다."));
                    PartnerEntity partner = partnerRepository.findByTenantEntityAndPartnerCode(tenant, text(row.getCell(1)))
                            .orElseThrow(()-> new EntityNotFoundException("공급사코드가 없습니다."));
                    LocalDate expectedDate = date(row.getCell(2), "입고예정일");
                    ProductEntity product = productRepository.findByTenantEntityAndProductCode(tenant, text(row.getCell(3)))
                            .orElseThrow(()-> new EntityNotFoundException("품목코드가 없습니다."));

                    // 2. 같은 화주+공급사+날짜면 이미 만든 문서에 붙이고, 처음이면 문서 생성
                    String key = tenant.getTenantId() + "|" + partner.getPartnerId() + "|" + expectedDate;
                    Integer documentId = documents.get(key);
                    if(documentId == null){
                        documentId = createDocument(new InboundCreateDto(tenant.getTenantId(), partner.getPartnerId(), expectedDate));
                        documents.put(key, documentId);
                    }

                    // 3. 품목 1줄: 화면 등록과 같은 createItem 재사용
                    createItem(documentId, new InboundItemCreateDto(product.getProductId(),
                            date(row.getCell(4), "제조일자"), date(row.getCell(5), "소비기한"),
                            number(row.getCell(6), "예정수량")));
                    itemCount++;

                // 같은 종류의 예외로 다시 던지되 메세지 앞에 행 번호 붙임 
                }catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(rowNo + "행: " + e.getMessage());
                } catch (IllegalStateException e) {
                    throw new IllegalStateException(rowNo + "행: " + e.getMessage());
                } catch (EntityNotFoundException e) {
                    throw new EntityNotFoundException(rowNo + "행: " + e.getMessage());
                }
            }
        }catch(IOException e){
            throw new IllegalArgumentException("엑셀 파일을 읽을 수 없습니다.");
        }

        if(itemCount == 0)
            throw new IllegalArgumentException("등록할 줄이 없습니다.");
        return "입고문서 " + documents.size() + "건, 품목 " + itemCount + "줄 등록";
    }

    // 셀 값을 화면에 보이는 글자 그대로 꺼내는 도구
    private static final DataFormatter FORMATTER = new DataFormatter();

    // 셀 -> 문자열 (빈 셀은 "")
    private String text(Cell cell){
        return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
    }

    // 셀 -> 날짜(엑셀 날짜 형식, "2026-10-12" 글자 둘 다 허용)
    private LocalDate date(Cell cell, String name){
        if(cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
            return cell.getLocalDateTimeCellValue().toLocalDate();
        try{
            return LocalDate.parse(text(cell));
        }catch(DateTimeParseException e){
            throw new IllegalArgumentException(name + "은(는) yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    // 셀 -> 정수
    private Integer number(Cell cell, String name){
        if(cell != null && cell.getCellType() == CellType.NUMERIC)
            return (int) cell.getNumericCellValue();
        try{
            return Integer.parseInt(text(cell));
        }catch(NumberFormatException e){
            throw new IllegalArgumentException(name + "은(는) 숫자여야 합니다.");
        }
    }


    // 등록화면 선택지 1. 화주목록
    public List<RegisterOptionDto> tenantOptions(){
        List<RegisterOptionDto> list = new ArrayList<>();
        for(TenantEntity t : tenantRepository.findAll()){
            list.add(new RegisterOptionDto(t.getTenantId(), t.getTenantCode(), t.getTenantName()));
        }
        return list;
    }

    // 등록화면 선택지 2. 그 화주의 공급사만 
    public List<RegisterOptionDto> supplierOptions(Integer tenantId){
        List<RegisterOptionDto> list = new ArrayList<>();
        for(PartnerEntity p : partnerRepository.findAll()){
            if(p.getTenantEntity().getTenantId().equals(tenantId) && "SUPPLIER".equals(p.getPartnerType())){
                list.add(new RegisterOptionDto(p.getPartnerId(), p.getPartnerCode(), p.getPartnerName()));
            }
        }
        return list;
    }

    // 등록화면 선택지 3. 그 화주의 품목만
    public List<RegisterOptionDto> productOptions(Integer tenantId){
        List<RegisterOptionDto> list = new ArrayList<>();
        for(ProductEntity p : productRepository.findAll()){
            if(p.getTenantEntity().getTenantId().equals(tenantId)){
                list.add(new RegisterOptionDto(p.getProductId(), p.getProductCode(), p.getProductName()));
            }
        }
        return list;
    }

}
