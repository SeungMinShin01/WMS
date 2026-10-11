-- V5: 같은 품목에 같은 LOT 번호는 하나만 (입고 품목 등록 시 LOT 재사용/등록의 안전장치)
-- LOT 번호는 공급사가 정해서 온다. 우리는 번호를 만들지 않고 lot 표에 기록만 한다
ALTER TABLE lot
  ADD CONSTRAINT uk_lot_product_code UNIQUE (product_id, lot_code);