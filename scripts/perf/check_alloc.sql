SELECT COUNT(*) AS detail_rows, COALESCE(SUM(qty), 0) AS detail_sum FROM document_item_detail WHERE document_item_id = 28;
SELECT stock_id, qty, allocated_qty FROM stock WHERE stock_id IN (3, 12);
SELECT document_id, status FROM document WHERE document_id = 14;