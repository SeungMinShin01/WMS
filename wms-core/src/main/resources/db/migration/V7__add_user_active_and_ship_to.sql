-- V7: 계정 사용 여부 + 출고 문서 배송지·연락처
-- 규칙: 이전 버전 파일은 수정하지 않는다. 변경은 새 버전 파일로만.

-- 1. 계정 사용 여부. FALSE = 비활성(로그인 불가)
--    계정을 지우지 않는 이유: 감사 로그·작업 이력의 "누가 했는지"를 남기기 위해
ALTER TABLE user_account
  ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER role;

-- 2. 출고 문서 배송지·연락처 (입고 문서는 NULL)
--    거래처 주소를 조인하지 않고 문서에 복사(스냅샷):
--    나중에 거래처 주소·연락처가 바뀌어도 이미 만든 출고 문서는 당시 값 그대로
ALTER TABLE document
  ADD COLUMN ship_address VARCHAR(200) NULL AFTER expected_at,
  ADD COLUMN ship_contact VARCHAR(50)  NULL AFTER ship_address;

-- 3. 이미 있는 출고 문서는 거래처의 주소·연락처로 채워 둠 (V4 tenant_id 채운 방식과 같음)
UPDATE document d JOIN partner p ON p.partner_id = d.partner_id
   SET d.ship_address = p.address,
       d.ship_contact = p.contact
 WHERE d.type = 'OUTBOUND';