-- V4: 화주(tenant) 도입
-- 화주는 지금 3곳 고정(시드). 화주 추가 기능은 나중에 API로 (코드에 화주를 하드코딩하지 않는다)
-- tenant_id 를 넣는 표: product, partner, document, stock
--   lot·document_item·document_item_detail 은 부모에서 알 수 있어 넣지 않는다
--   location 은 창고 소유(화주 공용)라 넣지 않는다
-- stock.tenant_id 는 lot → product 로 알 수 있지만 일부러 중복 컬럼을 둔다
--   (칸 단위 화주 검사, 화주별 재고 조회, 할당 후보 쿼리)
-- 기존 행이 있는 DB(배포 DB)에서도 돌도록: NULL 로 추가 → 채우기 → NOT NULL
-- tenant_id 에 기본값을 두지 않는다: 화주를 빠뜨리면 조용히 1번 화주로 들어가는 사고를 막기 위해

-- 1. 화주
CREATE TABLE tenant (
  tenant_id    INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_code  VARCHAR(10)  NOT NULL UNIQUE,   -- 문서번호·코드 접두어 (HLT / BEV / FOD)
  tenant_name  VARCHAR(50)  NOT NULL,          -- 회사명
  created_at   DATETIME     NOT NULL DEFAULT NOW(),
  updated_at   DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW()
);

INSERT INTO tenant (tenant_id, tenant_code, tenant_name) VALUES
(1, 'HLT', '(주)새봄헬스'),
(2, 'BEV', '(주)맑은샘음료'),
(3, 'FOD', '(주)들녘푸드');

-- 2. 품목: 화주 소유. 품목코드는 화주 안에서만 겹치지 않으면 된다
--    V1 의 product_code UNIQUE(창고 전체에서 하나)를 지우고 (tenant_id, product_code) 로 바꾼다
ALTER TABLE product ADD COLUMN tenant_id INT NULL AFTER product_id;
UPDATE product SET tenant_id = 1;
ALTER TABLE product
  MODIFY tenant_id INT NOT NULL,
  DROP INDEX product_code,
  ADD CONSTRAINT uk_product_tenant_code UNIQUE (tenant_id, product_code),
  ADD CONSTRAINT fk_product_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id);

-- 3. 거래처: 화주의 공급사·배송지. 품목과 같은 이유로 UNIQUE 범위를 화주 안으로
ALTER TABLE partner ADD COLUMN tenant_id INT NULL AFTER partner_id;
UPDATE partner SET tenant_id = 1;
ALTER TABLE partner
  MODIFY tenant_id INT NOT NULL,
  DROP INDEX partner_code,
  ADD CONSTRAINT uk_partner_tenant_code UNIQUE (tenant_id, partner_code),
  ADD CONSTRAINT fk_partner_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id);

-- 4. 문서: 요청한 화주 + 들어온 경로(WMS 직접 / PORTAL)
--    document_no 는 전역 UNIQUE 유지 (Portal 은 BEV-IN-…, WMS 직접은 IN-… 라 겹치지 않는다)
ALTER TABLE document
  ADD COLUMN tenant_id INT NULL AFTER document_id,
  ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'WMS' AFTER type,
  ADD CONSTRAINT chk_document_source CHECK (source IN ('WMS', 'PORTAL'));
UPDATE document d JOIN partner p ON p.partner_id = d.partner_id
   SET d.tenant_id = p.tenant_id;
ALTER TABLE document
  MODIFY tenant_id INT NOT NULL,
  ADD CONSTRAINT fk_document_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  ADD INDEX idx_document_tenant_status (tenant_id, status);

-- 5. 재고: 품목의 화주를 복사해 둔다 (stock → lot → product.tenant_id 와 항상 같아야 한다)
ALTER TABLE stock ADD COLUMN tenant_id INT NULL AFTER stock_id;
UPDATE stock s
  JOIN lot l     ON l.lot_id     = s.lot_id
  JOIN product p ON p.product_id = l.product_id
   SET s.tenant_id = p.tenant_id;
ALTER TABLE stock
  MODIFY tenant_id INT NOT NULL,
  ADD CONSTRAINT fk_stock_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  ADD INDEX idx_stock_location_tenant (location_id, tenant_id);  -- 칸에 다른 화주 재고가 있나