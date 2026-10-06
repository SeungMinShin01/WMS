-- V3: 처리 결과(detail) 한 줄의 상태
-- 1) 기본값을 잠깐 두고 컬럼 추가 → 2) 이미 있는 줄 채우기 → 3) 기본값 제거 (앞으로는 코드가 반드시 넣게)
ALTER TABLE document_item_detail
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'INSPECTED' AFTER qty,
  ADD CONSTRAINT chk_detail_status
      CHECK (status IN ('INSPECTED','STORED','ALLOCATED','PICKED','SHIPPED'));

UPDATE document_item_detail x
  JOIN document_item i ON i.document_item_id = x.document_item_id
  JOIN document d      ON d.document_id      = i.document_id
   SET x.status = CASE
         WHEN d.type = 'INBOUND' AND x.location_id IS NULL THEN 'INSPECTED'
         WHEN d.type = 'INBOUND'                           THEN 'STORED'
         WHEN d.status = 'SHIPPED'                         THEN 'SHIPPED'
         ELSE 'ALLOCATED' END;

ALTER TABLE document_item_detail ALTER COLUMN status DROP DEFAULT;