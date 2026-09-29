-- V2: 로케이션 적재량 + 출고 문서 배송지
-- 규칙: V1은 수정하지 않는다. 변경은 새 버전 파일로만.

-- 1. 로케이션별 최대 적재량 (BOX). NULL = 제한 없음  
ALTER TABLE location
  ADD COLUMN capacity INT NULL AFTER location_code,
  ADD CONSTRAINT chk_location_capacity CHECK (capacity IS NULL OR capacity > 0);

-- 2. 거래처 주소. 거래처 = 배송지 (지점이 다르면 거래처를 따로 등록)
--    연락처(contact)는 V1에 이미 있음
ALTER TABLE partner
  ADD COLUMN address VARCHAR(200) NULL AFTER contact;

-- 3. 비고 3층: 문서 전체 / 화주 요청 줄 / 작업자 처리 줄
ALTER TABLE document             ADD COLUMN remark VARCHAR(200) NULL AFTER status;
ALTER TABLE document_item        ADD COLUMN remark VARCHAR(200) NULL AFTER expected_qty;
ALTER TABLE document_item_detail ADD COLUMN remark VARCHAR(200) NULL AFTER qty;