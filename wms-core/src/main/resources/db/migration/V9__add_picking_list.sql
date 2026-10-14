-- V9: QR 피킹 리스트
-- picking_list      : 출고 문서 1개 = 피킹 리스트 1개, 관리자가 작업자에게 배정
-- picking_list_item : 리스트 안의 품목 줄 (할당 줄 detail 1개 = 품목 줄 1개), 작업자가 스캔해서 집은 수량을 쌓음

CREATE TABLE picking_list (
  picking_list_id  INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  picking_no       VARCHAR(30)  NOT NULL UNIQUE,      
  document_id      INT          NOT NULL UNIQUE,          -- 문서 1개에 리스트 1개만 (두 번 만들면 409)
  worker_id        INT          NULL,                     -- 배정된 작업자 (배정 전 NULL)
  status           VARCHAR(20)  NOT NULL,                 -- CREATED / ASSIGNED / PICKING / DONE / CANCELED
  assigned_at      DATETIME     NULL,                     -- 배정한 시각
  started_at       DATETIME     NULL,                     -- 첫 스캔 시각
  completed_at     DATETIME     NULL,                     -- 다 집은 시각
  created_at       DATETIME     NOT NULL DEFAULT NOW(),
  updated_at       DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  CONSTRAINT fk_picking_list_document FOREIGN KEY (document_id) REFERENCES document (document_id),
  CONSTRAINT fk_picking_list_worker   FOREIGN KEY (worker_id)   REFERENCES user_account (user_id),
  CONSTRAINT chk_picking_list_status
      CHECK (status IN ('CREATED','ASSIGNED','PICKING','DONE','CANCELED'))
);

CREATE TABLE picking_list_item (
  picking_list_item_id  INT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  picking_list_id       INT       NOT NULL,                -- 어느 리스트의 줄인지
  detail_id             INT       NOT NULL UNIQUE,         -- 할당 줄 (한 할당 줄은 한 리스트에만)
  seq                   INT       NOT NULL,                -- 집는 순서
  picked_qty            INT       NOT NULL DEFAULT 0,      -- 지금까지 집은 수량
  picked_at             DATETIME  NULL,                    -- 다 집은 시각
  created_at            DATETIME  NOT NULL DEFAULT NOW(),
  updated_at            DATETIME  NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  CONSTRAINT fk_picking_item_list   FOREIGN KEY (picking_list_id) REFERENCES picking_list (picking_list_id),
  CONSTRAINT fk_picking_item_detail FOREIGN KEY (detail_id)       REFERENCES document_item_detail (detail_id),
  CONSTRAINT chk_picking_item_qty   CHECK (picked_qty >= 0)
);