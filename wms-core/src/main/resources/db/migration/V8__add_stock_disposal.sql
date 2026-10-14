-- V8: 재고 폐기 기록
-- 폐기는 문서(입고·출고) 없이 일어나는 재고 변동이라 document_item_detail 에 담을 수 없어 별도 표로 기록
-- 재고 줄은 지우지 않고 qty = 0 (예전 입고·출고 기록이 stock_id 로 가리키고 있음)
CREATE TABLE stock_disposal (
  disposal_id  INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  stock_id     INT          NOT NULL,                 -- 어느 칸의 어느 LOT 재고 (LOT·칸·화주는 stock 에서 알 수 있음)
  qty          INT          NOT NULL CHECK (qty > 0), -- 폐기한 실물 수량
  reason       VARCHAR(200) NULL,                     -- 사유 (예: 소비기한 경과)
  user_id      INT          NOT NULL,                 -- 처리자 (관리자)
  disposed_at  DATETIME     NOT NULL DEFAULT NOW(),
  FOREIGN KEY (stock_id) REFERENCES stock (stock_id),
  FOREIGN KEY (user_id)  REFERENCES user_account (user_id)
);