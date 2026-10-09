-- 기대(둘 다 성공): qty 130, 선점 50 / 덮어쓰기면 qty 50 이나 선점 0
SELECT stock_id, qty, allocated_qty FROM stock WHERE stock_id = 900;
SELECT detail_id, status, location_id, stock_id FROM document_item_detail WHERE detail_id = 14;
SELECT COUNT(*) AS alloc_rows, COALESCE(SUM(qty), 0) AS alloc_sum FROM document_item_detail WHERE document_item_id IN (21, 22);
SELECT document_id, status FROM document WHERE document_id IN (4, 10);
SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';