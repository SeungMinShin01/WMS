-- V11: 로케이션 비고 (사람이 직접 적는 값)
-- 실제 적재수량·혼용·적치 품목은 재고(stock)에서 조회 때 계산하므로 컬럼을 두지 않는다
--   (컬럼으로 두면 적재·출고·폐기마다 같이 고쳐야 하고, 빠뜨리면 재고와 숫자가 어긋남)
ALTER TABLE location ADD COLUMN remark VARCHAR(200) NULL AFTER capacity;