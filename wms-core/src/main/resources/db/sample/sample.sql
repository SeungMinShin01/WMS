-- 테스트할 때 이곳에서 INSERT문을 넣는다. migration/ 안에다 직접 작성 X
-- docker compose up -d 이후에 실행할것 
-- 몇 번을 실행해도 같은 상태가 되도록 먼저 비운다

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE document_item_detail;
TRUNCATE TABLE document_item;
TRUNCATE TABLE document;
TRUNCATE TABLE stock;
TRUNCATE TABLE lot;
TRUNCATE TABLE location;
TRUNCATE TABLE partner;
TRUNCATE TABLE product;
SET FOREIGN_KEY_CHECKS = 1;

-- 아래부터 INSERT ...

-- 1. 품목
INSERT INTO product (product_code, product_name, spec, unit, min_ship_days) VALUES
('SKU-1001', '매운맛 봉지라면', '120g*40', 'BOX', 30),   -- 1
('SKU-1002', '해물맛 컵라면',   '65g*24',  'BOX', 30),   -- 2
('SKU-1003', '통밀 크래커',     '200g*12', 'BOX', 30);   -- 3

-- 2. 거래처
INSERT INTO partner (partner_code, partner_name, partner_type, contact) VALUES
('PT-001', '한빛식품',        'SUPPLIER', '031-456-1001'),   -- 1
('PT-002', '다온제과',        'SUPPLIER', '031-456-1002'),   -- 2
('PT-006', '중앙마트 강남점', 'CUSTOMER', '02-555-6006');    -- 3

-- 3. 공급사가 알려 준 LOT
INSERT INTO lot (product_id, lot_code, expiry_date) VALUES
(1, 'LOT-20260915-PT001-01', '2027-03-15'),   -- 1: 라면
(1, 'LOT-20260920-PT001-02', '2027-03-20'),   -- 2: 라면
(2, 'LOT-20260918-PT001-03', '2027-02-18'),   -- 3: 컵라면
(3, 'LOT-20260912-PT002-01', '2027-04-30');   -- 4: 크래커

-- 4. 문서 3건 (입고 2, 출고 1)
--    오후 문서를 먼저 넣음 → 목록이 예정일 순으로 나오는지 확인용
INSERT INTO document (document_no, type, partner_id, expected_at) VALUES
('IN-20261001-002',  'INBOUND',  2, '2026-10-01 14:00:00'),   -- 1: 다온제과 (오후)
('IN-20261001-001',  'INBOUND',  1, '2026-10-01 09:00:00'),   -- 2: 한빛식품 (오전)
('OUT-20261008-001', 'OUTBOUND', 3, '2026-10-08 10:00:00'),   -- 3: 중앙마트 (출고)
('OUT-20261007-001', 'OUTBOUND', 3, '2026-10-07 15:00');

-- 5. 요청 줄 (입고는 LOT 있음, 출고는 NULL)
INSERT INTO document_item (document_id, product_id, lot_id, expected_qty) VALUES
(1, 3, 4,    60),   -- 1: 문서1 크래커 60
(2, 1, 1,    60),   -- 2: 문서2 라면 LOT-01 60
(2, 1, 2,    40),   -- 3: 문서2 라면 LOT-02 40
(2, 2, 3,    80),   -- 4: 문서2 컵라면 80
(3, 1, NULL, 90),   -- 5: 문서3(출고) 라면 90
(4, 2, NULL, 30),   -- 6: 문서4(출고) 컵라면 30
(4, 3, NULL, 20);   -- 7: 문서4(출고) 크래커 20

-- 6. 칸 5개
INSERT INTO location (location_code, is_active) VALUES
('A-01-01', TRUE),   -- 1
('A-01-02', TRUE),   -- 2
('A-02-01', TRUE),   -- 3
('B-01-01', TRUE),   -- 4
('B-01-02', TRUE);   -- 5

-- 7. 문서1(크래커 60)은 검수·적재까지 끝난 상태로 둔다
--    → 입고-6 재고 조회, 출고-3 할당을 입고 흐름 없이도 테스트할 수 있게
--    재고: 크래커 LOT(4)이 A-01-01(1번 칸)에 60개
INSERT INTO stock (lot_id, location_id, qty, allocated_qty) VALUES
(4, 1, 60, 0);   -- 1

--    검수·적재 결과: 문서1 요청 줄(1) → LOT 4, 칸 1, 재고 1, 60개
INSERT INTO document_item_detail (document_item_id, lot_id, location_id, stock_id, qty) VALUES
(1, 4, 1, 1, 60);   -- 1

