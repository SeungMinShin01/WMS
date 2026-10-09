SELECT stock_id, qty, allocated_qty FROM stock WHERE stock_id IN (3, 6);
SELECT document_id, status, completed_at FROM document WHERE document_id = 9;
SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';