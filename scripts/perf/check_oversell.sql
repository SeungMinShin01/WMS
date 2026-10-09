-- 기대: 재고 12 선점 <= 50 (두 문서 중 하나만 성공). 초과 할당이면 선점 100, 가용 -50
SELECT stock_id, qty, allocated_qty, qty - allocated_qty AS available FROM stock WHERE stock_id IN (3, 12);
SELECT document_id, status FROM document WHERE document_id IN (14, 990);
SELECT document_item_id, COUNT(*) AS detail_rows, COALESCE(SUM(qty), 0) AS detail_sum FROM document_item_detail WHERE document_item_id IN (28, 990) GROUP BY document_item_id;
SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';