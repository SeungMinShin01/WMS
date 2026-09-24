-- 테스트할 때 이곳에서 INSERT문을 넣는다. migration/ 안에다 직접 작성 X
-- 몇 번을 실행해도 같은 상태가 되도록 먼저 비운다
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE document_item_detail;
TRUNCATE TABLE document_item;
TRUNCATE TABLE document;
TRUNCATE TABLE stock;
TRUNCATE TABLE lot;
TRUNCATE TABLE location;
TRUNCATE TABLE partner;
TRUNCATE TABLE product;
SET FOREIGN_KEY_CHECKS = 1;

-- 아래부터 INSERT ...

