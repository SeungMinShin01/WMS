SELECT COUNT(*) AS stock_rows, COALESCE(SUM(qty), 0) AS total_qty FROM stock WHERE lot_id = 2;
SELECT detail_id, location_id, stock_id FROM document_item_detail WHERE detail_id = 14;
SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';