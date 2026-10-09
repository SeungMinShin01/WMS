-- 기대: 20개 모두 STORED, 칸 901~920 재고 20행 합계 20
SELECT COUNT(*) AS stored_cnt FROM document_item_detail WHERE detail_id BETWEEN 901 AND 920 AND status = 'STORED';
SELECT COUNT(*) AS stock_rows, COALESCE(SUM(qty), 0) AS total FROM stock WHERE location_id BETWEEN 901 AND 920;
SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';