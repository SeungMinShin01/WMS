-- V10: 품목·거래처 사용 여부 (위치 location.is_active 는 V1 에 이미 있음)
-- TRUE = 사용 중, FALSE = 삭제(미사용). 실제 DELETE 대신 사용 여부로 막는다
-- 지우지 않는 이유: 예전 입출고 문서·LOT·재고가 이 행을 FK 로 가리킴 → DELETE 하면 FK 오류
-- 기존 행은 DEFAULT TRUE 로 전부 "사용 중"으로 채워짐
ALTER TABLE product ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER min_ship_days;
ALTER TABLE partner ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER address;