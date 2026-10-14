package com.wms.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wms.model.entity.PartnerEntity;
import com.wms.model.entity.ProductEntity;
import com.wms.model.entity.TenantEntity;
import com.wms.model.repository.PartnerRepository;
import com.wms.model.repository.ProductRepository;
import com.wms.model.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;

// 입고·출고 엑셀 일괄 등록 공용 도구 (DocumentNoGenerator 처럼 두 서비스가 같이 씀)
// 여기엔 "셀 읽기 + 이름으로 찾기"만 둔다
// 어떤 열을 읽어서 어떤 문서를 만들지(업무 규칙)는 각 서비스에 그대로 둔다
@Component 
public class ExcelHelper {
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private ProductRepository productRepository;

    // 셀 값을 화면에 보이는 글자 그대로 꺼내는 도구
    private static final DataFormatter FORMATTER = new DataFormatter();

    // 셀 -> 문자열 (빈 셀은 "")
    public String text(Cell cell) {
        return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
    }

    // 셀 -> 날짜 (엑셀 날짜 형식, "2026-10-12" 글자 둘 다 허용)
    public LocalDate date(Cell cell, String name) {
        // 숫자 셀이면 엑셀 날짜 일련번호로 봄 (서식이 빠져 46307처럼 보여도 날짜로 읽음)
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        try {
            return LocalDate.parse(text(cell));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + "은(는) yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    // 셀 -> 정수
    public Integer number(Cell cell, String name) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        }
        try {
            return Integer.parseInt(text(cell));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + "은(는) 숫자여야 합니다.");
        }
    }

    // ===== 이름으로 찾기 =====
    // 이름은 DB에서 유일 보장이 없음 -> 0개면 404, 2개 이상이면 409, 1개일 때만 사용

    // 화주 찾기
    public TenantEntity findTenant(String value) {
        if (value.isEmpty()) throw new IllegalArgumentException("화주가 비었습니다.");
        List<TenantEntity> found = tenantRepository.findByTenantName(value);
        if (found.isEmpty()) throw new EntityNotFoundException("화주 '" + value + "'을(를) 찾을 수 없습니다.");
        if (found.size() > 1) throw new IllegalStateException("화주 '" + value + "'이(가) 여러 개입니다. 코드로 입력하세요.");
        return found.get(0);
    }

    // 거래처 찾기 (그 화주 안에서, 구분이 맞는 것만)
    // partnerType : 입고 "SUPPLIER" / 출고 "CUSTOMER"
    // label       : 에러 메세지에 쓸 이름 "공급사" / "납품처"
    public PartnerEntity findPartner(TenantEntity tenant, String value, String partnerType, String label) {
        if (value.isEmpty()) throw new IllegalArgumentException(label + "가 비었습니다.");
        List<PartnerEntity> found = new ArrayList<>();
        for (PartnerEntity p : partnerRepository.findByTenantEntityAndPartnerName(tenant, value)) {
            if (partnerType.equals(p.getPartnerType())) {
                found.add(p);
            }
        }
        if (found.isEmpty()) throw new EntityNotFoundException(label + " '" + value + "'을(를) 찾을 수 없습니다.");
        if (found.size() > 1) throw new IllegalStateException(label + " '" + value + "'이(가) 여러 개입니다. 기준정보를 확인하세요.");
        return found.get(0);
    }

    // 품목 찾기 (그 화주 안에서만)
    public ProductEntity findProduct(TenantEntity tenant, String value) {
        if (value.isEmpty()) throw new IllegalArgumentException("품목이 비었습니다.");
        List<ProductEntity> found = productRepository.findByTenantEntityAndProductName(tenant, value);
        if (found.isEmpty()) throw new EntityNotFoundException("품목 '" + value + "'을(를) 찾을 수 없습니다.");
        if (found.size() > 1) throw new IllegalStateException("품목 '" + value + "'이(가) 여러 개입니다. 기준정보를 확인하세요.");
        return found.get(0);
    }
}
