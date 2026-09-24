-- =========================================================
-- V1__init_schema.sql — WMS 초기 스키마 (표 8개)
-- MySQL 8.0.16+ (CHECK 제약이 실제로 동작하는 버전)
-- Flyway가 서버 시작 때 1번만 실행한다. 이미 실행된 뒤에는 이 파일을 고치지 않는다.
--   → 테이블을 바꿀 땐 V2__...sql 새 파일을 만든다.
-- stock_history: 보류
-- 순서: 부모 표 → 자식 표 (FK가 가리키는 표가 먼저 있어야 한다)
-- =========================================================

-- 1. 품목
CREATE TABLE product (
  product_id    INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_code  VARCHAR(20)  NOT NULL UNIQUE,
  product_name  VARCHAR(50)  NOT NULL,
  spec          VARCHAR(30)  NULL,
  unit          VARCHAR(20)  NOT NULL,
  min_ship_days INT          NOT NULL DEFAULT 0 CHECK (min_ship_days >= 0),  -- 출고 허용 잔여일
  created_at    DATETIME     NOT NULL DEFAULT NOW(),
  updated_at    DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW()
);

-- 2. 거래처 (공급사 / 납품처)
CREATE TABLE partner (
  partner_id    INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  partner_code  VARCHAR(50)  NOT NULL UNIQUE,
  partner_name  VARCHAR(50)  NOT NULL,
  partner_type  VARCHAR(20)  NOT NULL CHECK (partner_type IN ('SUPPLIER', 'CUSTOMER')),
  contact       VARCHAR(50)  NULL,
  created_at    DATETIME     NOT NULL DEFAULT NOW(),
  updated_at    DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW()
);

-- 3. 칸 (로케이션)
CREATE TABLE location (
  location_id   INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  location_code VARCHAR(20)  NOT NULL UNIQUE,
  is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    DATETIME     NOT NULL DEFAULT NOW(),
  updated_at    DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW()
);

-- 4. LOT (입고 문서 등록 때 찾거나 만든다)
CREATE TABLE lot (
  lot_id       INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id   INT          NOT NULL,
  lot_code     VARCHAR(50)  NOT NULL,          -- 예: LOT-20260915-PT001-01
  expiry_date  DATE         NOT NULL,          -- 소비기한
  created_at   DATETIME     NOT NULL DEFAULT NOW(),
  updated_at   DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  FOREIGN KEY (product_id) REFERENCES product (product_id)
);

-- 5. 재고 (LOT + 칸당 1행, 가용수량 = qty - allocated_qty)
CREATE TABLE stock (
  stock_id       INT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  lot_id         INT       NOT NULL,
  location_id    INT       NOT NULL,
  qty            INT       NOT NULL DEFAULT 0 CHECK (qty >= 0),
  allocated_qty  INT       NOT NULL DEFAULT 0,
  created_at     DATETIME  NOT NULL DEFAULT NOW(),
  updated_at     DATETIME  NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  CHECK (allocated_qty >= 0 AND allocated_qty <= qty),
  FOREIGN KEY (lot_id)      REFERENCES lot (lot_id),
  FOREIGN KEY (location_id) REFERENCES location (location_id)
);

-- 6. 입출고 문서 (입고·출고를 type으로 구분)
CREATE TABLE document (
  document_id   INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  document_no   VARCHAR(30)  NOT NULL UNIQUE,  -- 예: IN-20261001-001
  type          VARCHAR(20)  NOT NULL CHECK (type IN ('INBOUND', 'OUTBOUND')),
  partner_id    INT          NOT NULL,
  expected_at   DATETIME     NOT NULL,
  completed_at  DATETIME     NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'WAITING'
                CHECK (status IN ('WAITING', 'INSPECTED', 'COMPLETED',
                                  'ALLOCATED', 'PICKING', 'SHIPPED', 'CANCELED')),
  created_at    DATETIME     NOT NULL DEFAULT NOW(),
  updated_at    DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  FOREIGN KEY (partner_id) REFERENCES partner (partner_id)
);

-- 7. 문서 품목 = 요청 (입고는 lot_id 채움, 출고는 NULL)
CREATE TABLE document_item (
  document_item_id  INT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  document_id       INT       NOT NULL,
  product_id        INT       NOT NULL,
  lot_id            INT       NULL,
  expected_qty      INT       NOT NULL CHECK (expected_qty > 0),
  created_at        DATETIME  NOT NULL DEFAULT NOW(),
  updated_at        DATETIME  NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  FOREIGN KEY (document_id) REFERENCES document (document_id),
  FOREIGN KEY (product_id)  REFERENCES product (product_id),
  FOREIGN KEY (lot_id)      REFERENCES lot (lot_id)
);

-- 8. 문서 품목 상세 = 처리 결과 (검수·적재·할당·피킹)
CREATE TABLE document_item_detail (
  detail_id         INT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  document_item_id  INT       NOT NULL,
  lot_id            INT       NOT NULL,
  location_id       INT       NULL,            -- 입고 적재 전 NULL
  stock_id          INT       NULL,            -- 입고 적재 전 NULL
  qty               INT       NOT NULL CHECK (qty > 0),
  created_at        DATETIME  NOT NULL DEFAULT NOW(),
  updated_at        DATETIME  NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  FOREIGN KEY (document_item_id) REFERENCES document_item (document_item_id),
  FOREIGN KEY (lot_id)           REFERENCES lot (lot_id),
  FOREIGN KEY (location_id)      REFERENCES location (location_id),
  FOREIGN KEY (stock_id)         REFERENCES stock (stock_id)
);
