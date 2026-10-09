-- ============================================================
-- 속도 측정용 대량 데이터 (sample2.sql 을 먼저 넣은 뒤 실행)
-- 실행 전에 두 변수를 정한다 (speed.ps1 이 앞에 붙여 줌)
--   SET @N = 10000;   -- 규모: 재고 N행 · 처리 상세 약 N행
--   SET @T = 1000;    -- 쓰기 측정 대상 수 (적재·할당·출고확정 각각)
--
-- 만드는 것 (sample2 의 작은 ID 와 겹치지 않게 100만 단위 ID 사용)
--   품목 300 (화주 1·2·3 에 100개씩) · 거래처 6 (화주별 공급사·납품처)
--   칸 N/5 · LOT N/2 · 재고 N (LOT 하나가 칸 2곳에)
--   입고 문서 N/10 (완료, 품목 5줄씩, 적재 상세 N/2)
--   출고 문서 N/10 (출고완료, 품목 5줄씩, 출고 상세 N/2)
--   쓰기 대상 T개씩
--     적재   : 입고 문서 3000001~ (INSPECTED) · 검수 상세 3000001~ · 빈 칸 3000001~
--     할당   : 출고 문서 4000001~ (WAITING) · 품목 줄 4000001~ · 전용 재고 4000001~ (가용 10)
--     출고확정: 출고 문서 5000001~ (PICKING) · 피킹 상세 5000001~ (PICKED) · 전용 재고 5000001~ (선점 1)
-- 화주 규칙: 품목 1000001+3m+(t-1) 의 화주 = t. 문서·재고·LOT 는 모두 같은 화주로 맞춘다
-- ============================================================

-- 0. 숫자표 1~100000 (재귀 없이 자릿수 곱으로 생성, 이미 있으면 그대로 사용)
CREATE TABLE IF NOT EXISTS perf_num (n INT NOT NULL PRIMARY KEY);
INSERT IGNORE INTO perf_num (n)
SELECT a.d + b.d * 10 + c.d * 100 + e.d * 1000 + f.d * 10000 + 1
FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) f;

SET @P = 300;                       -- 품목 수 (3의 배수)
SET @L = @N DIV 5;                  -- 칸 수
SET @LOTS = @N DIV 2;               -- LOT 수 = 입고 품목 줄 수
SET @DOCS = @N DIV 10;              -- 입고·출고 문서 수 (각각)

-- 1. 거래처 6 : 1000000 + 2t-1 = 화주 t 공급사, 1000000 + 2t = 화주 t 납품처
INSERT INTO partner (partner_id, tenant_id, partner_code, partner_name, partner_type)
SELECT 1000000 + n, (n + 1) DIV 2, CONCAT('PF-PT-', n), CONCAT('측정거래처', n),
       IF(n % 2 = 1, 'SUPPLIER', 'CUSTOMER')
FROM perf_num WHERE n <= 6;

-- 2. 품목 300 : 화주 = (n-1) % 3 + 1, 소비기한 검사 통과하도록 min_ship_days 0
INSERT INTO product (product_id, tenant_id, product_code, product_name, unit, min_ship_days)
SELECT 1000000 + n, (n - 1) % 3 + 1, CONCAT('PF-', n), CONCAT('측정품목', n), 'BOX', 0
FROM perf_num WHERE n <= @P;

-- 3. 칸 : 일반 N/5 + 쓰기 대상용 (적재 빈 칸 · 할당 재고 칸 · 출고 재고 칸) 각 T
INSERT INTO location (location_id, location_code, capacity, is_active)
SELECT 1000000 + n, CONCAT('PF-L', n), NULL, TRUE FROM perf_num WHERE n <= @L;
INSERT INTO location (location_id, location_code, capacity, is_active)
SELECT 3000000 + n, CONCAT('PF-CL', n), NULL, TRUE FROM perf_num WHERE n <= @T;
INSERT INTO location (location_id, location_code, capacity, is_active)
SELECT 4000000 + n, CONCAT('PF-AL', n), NULL, TRUE FROM perf_num WHERE n <= @T;
INSERT INTO location (location_id, location_code, capacity, is_active)
SELECT 5000000 + n, CONCAT('PF-SL', n), NULL, TRUE FROM perf_num WHERE n <= @T;

-- 4. LOT N/2 : LOT j 는 입고 문서 (j-1) DIV 5 + 1 의 품목 줄 → 그 문서 화주의 품목
--    화주 td = ((j-1) DIV 5) % 3 + 1, 품목 = 1000001 + 3 * ((j-1) % 100) + (td-1)
INSERT INTO lot (lot_id, product_id, lot_code, expiry_date)
SELECT 1000000 + n,
       1000001 + 3 * ((n - 1) % (@P DIV 3)) + ((n - 1) DIV 5) % 3,
       CONCAT('PF-LOT-', n),
       DATE_ADD('2030-01-01', INTERVAL (n % 365) DAY)
FROM perf_num WHERE n <= @LOTS;
-- 쓰기 대상 LOT (적재용, 화주 1 품목 1000001)
INSERT INTO lot (lot_id, product_id, lot_code, expiry_date)
SELECT 3000000 + n, 1000001, CONCAT('PF-CLOT-', n), '2030-06-01' FROM perf_num WHERE n <= @T;

-- 5. 재고 N : 재고 i 의 LOT = (i-1) % (N/2) + 1, 칸 = (i-1) % (N/5) + 1 → LOT 하나가 칸 2곳
INSERT INTO stock (stock_id, tenant_id, lot_id, location_id, qty, allocated_qty)
SELECT 1000000 + s.n, p.tenant_id, 1000000 + (s.n - 1) % @LOTS + 1, 1000000 + (s.n - 1) % @L + 1, 100, 0
FROM perf_num s
JOIN lot l ON l.lot_id = 1000000 + (s.n - 1) % @LOTS + 1
JOIN product p ON p.product_id = l.product_id
WHERE s.n <= @N;
-- 할당 대상 전용 재고 (가용 10) / 출고확정 대상 전용 재고 (선점 1)
INSERT INTO stock (stock_id, tenant_id, lot_id, location_id, qty, allocated_qty)
SELECT 4000000 + n, 1, 1000001, 4000000 + n, 10, 0 FROM perf_num WHERE n <= @T;
INSERT INTO stock (stock_id, tenant_id, lot_id, location_id, qty, allocated_qty)
SELECT 5000000 + n, 1, 1000001, 5000000 + n, 10, 1 FROM perf_num WHERE n <= @T;

-- 6. 입고 문서 N/10 (완료) + 품목 줄 N/2 + 적재 상세 N/2
INSERT INTO document (document_id, tenant_id, document_no, type, source, partner_id, expected_at, completed_at, status)
SELECT 1000000 + n, (n - 1) % 3 + 1, CONCAT('PF-IN-', n), 'INBOUND', 'WMS',
       1000000 + 2 * ((n - 1) % 3 + 1) - 1,
       DATE_SUB(NOW(), INTERVAL (n % 90) DAY), DATE_SUB(NOW(), INTERVAL (n % 90) DAY), 'COMPLETED'
FROM perf_num WHERE n <= @DOCS;
INSERT INTO document_item (document_item_id, document_id, product_id, lot_id, expected_qty)
SELECT 1000000 + n, 1000000 + (n - 1) DIV 5 + 1, l.product_id, l.lot_id, 100
FROM perf_num JOIN lot l ON l.lot_id = 1000000 + n
WHERE n <= @LOTS;
INSERT INTO document_item_detail (detail_id, document_item_id, lot_id, location_id, stock_id, qty, status)
SELECT 1000000 + n, 1000000 + n, 1000000 + n, s.location_id, s.stock_id, 100, 'STORED'
FROM perf_num JOIN stock s ON s.stock_id = 1000000 + n
WHERE n <= @LOTS;

-- 7. 출고 문서 N/10 (출고완료) + 품목 줄 N/2 + 출고 상세 N/2
--    출고 품목 줄 q 는 재고 (N/2 + q) 에서 1개 출고. 그 재고의 LOT = q → 화주·품목이 문서와 같음
INSERT INTO document (document_id, tenant_id, document_no, type, source, partner_id, expected_at, completed_at, status)
SELECT 2000000 + n, (n - 1) % 3 + 1, CONCAT('PF-OUT-', n), 'OUTBOUND', 'WMS',
       1000000 + 2 * ((n - 1) % 3 + 1),
       DATE_SUB(NOW(), INTERVAL (n % 60) DAY), DATE_SUB(NOW(), INTERVAL (n % 60) DAY), 'SHIPPED'
FROM perf_num WHERE n <= @DOCS;
INSERT INTO document_item (document_item_id, document_id, product_id, lot_id, expected_qty)
SELECT 2000000 + n, 2000000 + (n - 1) DIV 5 + 1, l.product_id, NULL, 1
FROM perf_num JOIN lot l ON l.lot_id = 1000000 + n
WHERE n <= @LOTS;
INSERT INTO document_item_detail (detail_id, document_item_id, lot_id, location_id, stock_id, qty, status)
SELECT 2000000 + n, 2000000 + n, s.lot_id, s.location_id, s.stock_id, 1, 'SHIPPED'
FROM perf_num JOIN stock s ON s.stock_id = 1000000 + @LOTS + n
WHERE n <= @LOTS;

-- 8. 쓰기 대상: 적재 (입고 문서 INSPECTED · 품목 줄 · 검수 상세 INSPECTED, 화주 1)
INSERT INTO document (document_id, tenant_id, document_no, type, source, partner_id, expected_at, status)
SELECT 3000000 + n, 1, CONCAT('PF-CIN-', n), 'INBOUND', 'WMS', 1000001, NOW(), 'INSPECTED'
FROM perf_num WHERE n <= @T;
INSERT INTO document_item (document_item_id, document_id, product_id, lot_id, expected_qty)
SELECT 3000000 + n, 3000000 + n, 1000001, 3000000 + n, 10 FROM perf_num WHERE n <= @T;
INSERT INTO document_item_detail (detail_id, document_item_id, lot_id, location_id, stock_id, qty, status)
SELECT 3000000 + n, 3000000 + n, 3000000 + n, NULL, NULL, 10, 'INSPECTED' FROM perf_num WHERE n <= @T;

-- 9. 쓰기 대상: 할당 (출고 문서 WAITING · 품목 줄 1개 · 전용 재고 4000000+n)
INSERT INTO document (document_id, tenant_id, document_no, type, source, partner_id, expected_at, status)
SELECT 4000000 + n, 1, CONCAT('PF-AOUT-', n), 'OUTBOUND', 'WMS', 1000002, DATE_ADD(NOW(), INTERVAL 1 DAY), 'WAITING'
FROM perf_num WHERE n <= @T;
INSERT INTO document_item (document_item_id, document_id, product_id, lot_id, expected_qty)
SELECT 4000000 + n, 4000000 + n, 1000001, NULL, 1 FROM perf_num WHERE n <= @T;

-- 10. 쓰기 대상: 출고확정 (출고 문서 PICKING · 품목 줄 · 피킹 상세 PICKED · 전용 재고 5000000+n)
INSERT INTO document (document_id, tenant_id, document_no, type, source, partner_id, expected_at, status)
SELECT 5000000 + n, 1, CONCAT('PF-SOUT-', n), 'OUTBOUND', 'WMS', 1000002, NOW(), 'PICKING'
FROM perf_num WHERE n <= @T;
INSERT INTO document_item (document_item_id, document_id, product_id, lot_id, expected_qty)
SELECT 5000000 + n, 5000000 + n, 1000001, NULL, 1 FROM perf_num WHERE n <= @T;
INSERT INTO document_item_detail (detail_id, document_item_id, lot_id, location_id, stock_id, qty, status)
SELECT 5000000 + n, 5000000 + n, 1000001, 5000000 + n, 5000000 + n, 1, 'PICKED' FROM perf_num WHERE n <= @T;

-- 11. 확인
SELECT @N AS N, @T AS T,
       (SELECT COUNT(*) FROM stock) AS stock_rows,
       (SELECT COUNT(*) FROM document_item_detail) AS detail_rows,
       (SELECT COUNT(*) FROM document) AS document_rows,
       (SELECT COUNT(*) FROM location) AS location_rows,
       (SELECT COUNT(*) FROM lot) AS lot_rows;
SELECT 'tenant mismatch (0 이어야 함)' AS chk, COUNT(*) AS cnt
FROM stock s JOIN lot l ON l.lot_id = s.lot_id JOIN product p ON p.product_id = l.product_id
WHERE s.tenant_id <> p.tenant_id
UNION ALL
SELECT 'doc-item tenant mismatch (0 이어야 함)', COUNT(*)
FROM document_item i JOIN document d ON d.document_id = i.document_id JOIN product p ON p.product_id = i.product_id
WHERE d.tenant_id <> p.tenant_id;
