-- ERDCloud 가져오기용 (V1__init_schema.sql 간소화본: CHECK·인덱스·생성컬럼 식 제거, FK는 ALTER로 분리)

CREATE TABLE `warehouse` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `warehouse_code` VARCHAR(10) NOT NULL COMMENT '창고코드 WH-01',
    `warehouse_name` VARCHAR(50) NOT NULL COMMENT '창고명',
    `address` VARCHAR(200) COMMENT '주소',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '창고';

CREATE TABLE `product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_code` VARCHAR(20) NOT NULL COMMENT '품목코드 SKU-10001',
    `product_name` VARCHAR(100) NOT NULL COMMENT '품목명',
    `spec` VARCHAR(50) COMMENT '규격 500ml×20',
    `unit` VARCHAR(10) NOT NULL COMMENT '재고 단위 EA / BOX',
    `is_expiry_managed` CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '소비기한 관리 여부. N이면 소비기한을 9999-12-31로 저장',
    `min_ship_days` INT NOT NULL DEFAULT 0 COMMENT '출고허용 잔여일. 소비기한까지 이 일수보다 적게 남은 재고는 할당하지 않음',
    `pallet_qty` INT NOT NULL COMMENT '팔레트 1장(= 로케이션 1칸)에 올릴 수 있는 최대 수량',
    `safety_stock` INT NOT NULL DEFAULT 0 COMMENT '안전재고 (2차)',
    `is_active` CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '사용여부',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '품목';

CREATE TABLE `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL COMMENT '로그인 아이디',
    `password` VARCHAR(100) NOT NULL COMMENT 'BCrypt 해시',
    `name` VARCHAR(50) NOT NULL COMMENT '이름',
    `role` VARCHAR(20) NOT NULL COMMENT 'ADMIN 관리자 / WORKER 작업자',
    `is_active` CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '사용여부',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '사용자 (테이블명은 예약어 회피로 복수형)';

CREATE TABLE `delivery_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `do_no` VARCHAR(30) NOT NULL COMMENT '출고지시번호 DO-20260919-001',
    `status` VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED / ALLOCATED / PICKING / PICKED / SHIPPED / CANCELED',
    `allocated_at` DATETIME COMMENT '할당 일시',
    `shipped_at` DATETIME COMMENT '출고확정 일시',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '출고지시 (여러 주문의 묶음)';

CREATE TABLE `zone` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `warehouse_id` BIGINT NOT NULL COMMENT '창고',
    `zone_code` VARCHAR(10) NOT NULL COMMENT '구역코드 A / B / C',
    `zone_name` VARCHAR(50) NOT NULL COMMENT '구역명 (권장 용도일 뿐, 적치를 제한하지 않음)',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '구역';

CREATE TABLE `purchase_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `po_no` VARCHAR(30) NOT NULL COMMENT '발주번호 PO-20260919-001',
    `supplier_name` VARCHAR(100) NOT NULL COMMENT '공급처명 (거래처 테이블 없이 텍스트)',
    `expected_date` DATE NOT NULL COMMENT '입고예정일',
    `status` VARCHAR(20) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED / INSPECTING / PUTAWAY / COMPLETED / CANCELED',
    `remark` VARCHAR(200) COMMENT '비고',
    `completed_at` DATETIME COMMENT '입고확정 일시',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '발주 (= 입고예정)';

CREATE TABLE `orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_no` VARCHAR(30) NOT NULL COMMENT '주문번호 ORD-20260919-001',
    `ship_to_name` VARCHAR(100) NOT NULL COMMENT '배송지명 (받는 곳)',
    `ship_to_address` VARCHAR(200) NOT NULL COMMENT '배송지 주소',
    `ship_to_phone` VARCHAR(20) COMMENT '배송지 연락처',
    `requested_date` DATE NOT NULL COMMENT '출고요청일',
    `status` VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED / ASSIGNED / SHIPPED / CANCELED',
    `delivery_order_id` BIGINT COMMENT '묶인 출고지시. 접수 상태면 NULL',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '주문 (테이블명은 예약어 회피로 복수형)';

CREATE TABLE `location` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `zone_id` BIGINT NOT NULL COMMENT '구역',
    `location_code` VARCHAR(20) NOT NULL COMMENT '로케이션코드 A-01-03-2 (구역-통로-베이-단). 1칸 = 표준 팔레트 1장 자리',
    `aisle` INT NOT NULL COMMENT '통로 1~4',
    `bay` INT NOT NULL COMMENT '베이 1~10',
    `shelf_level` INT NOT NULL COMMENT '단 1~3 (1=바닥)',
    `is_active` CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '사용여부',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '로케이션 (팔레트 랙 한 칸 = 팔레트 1장)';

CREATE TABLE `purchase_order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `purchase_order_id` BIGINT NOT NULL COMMENT '발주',
    `product_id` BIGINT NOT NULL COMMENT '품목',
    `expected_qty` INT NOT NULL COMMENT '예정수량',
    `variance_reason` VARCHAR(20) COMMENT '차이사유 NOT_ARRIVED / WRONG_ITEM / ETC (검수 합계 < 예정일 때 필수)',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '발주 품목';

CREATE TABLE `order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL COMMENT '주문',
    `product_id` BIGINT NOT NULL COMMENT '품목',
    `qty` INT NOT NULL COMMENT '주문수량',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '주문 품목';

CREATE TABLE `delivery_order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `delivery_order_id` BIGINT NOT NULL COMMENT '출고지시',
    `product_id` BIGINT NOT NULL COMMENT '품목',
    `required_qty` INT NOT NULL COMMENT '총 소요량 (묶인 주문들의 합)',
    `allocated_qty` INT NOT NULL DEFAULT 0 COMMENT '할당수량',
    `shipped_qty` INT NOT NULL DEFAULT 0 COMMENT '출고수량',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '출고지시 품목 (품목별 합산)';

CREATE TABLE `inspection` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `purchase_order_item_id` BIGINT NOT NULL COMMENT '발주 품목',
    `expiry_date` DATE NOT NULL COMMENT '소비기한 (관리 안 하는 품목은 9999-12-31)',
    `good_qty` INT NOT NULL DEFAULT 0 COMMENT '정상수량',
    `reject_qty` INT NOT NULL DEFAULT 0 COMMENT '불량수량',
    `reject_reason` VARCHAR(20) COMMENT 'DAMAGED 파손 / EXPIRY_SHORT 소비기한 부족',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '검수 결과 (발주 품목 × 소비기한 1행)';

CREATE TABLE `stock` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NOT NULL COMMENT '품목',
    `location_id` BIGINT NOT NULL COMMENT '로케이션',
    `expiry_date` DATE NOT NULL COMMENT '소비기한',
    `on_hand_qty` INT NOT NULL DEFAULT 0 COMMENT '실물재고',
    `allocated_qty` INT NOT NULL DEFAULT 0 COMMENT '선점재고',
    `available_qty` INT NOT NULL COMMENT '가용재고 (생성 컬럼, 직접 쓰지 않음)',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '재고 (품목 × 로케이션 × 소비기한 1행)';

CREATE TABLE `putaway` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `inspection_id` BIGINT NOT NULL COMMENT '검수 결과',
    `location_id` BIGINT NOT NULL COMMENT '적치 로케이션',
    `qty` INT NOT NULL COMMENT '적치수량',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '적치 지정 (검수 결과를 어느 칸에 몇 개 올릴지)';

CREATE TABLE `stock_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `stock_id` BIGINT NOT NULL COMMENT '재고 행',
    `product_id` BIGINT NOT NULL COMMENT '품목 (복사값)',
    `location_id` BIGINT NOT NULL COMMENT '로케이션 (복사값)',
    `expiry_date` DATE NOT NULL COMMENT '소비기한 (복사값)',
    `tx_type` VARCHAR(20) NOT NULL COMMENT 'INBOUND / ALLOCATE / DEALLOCATE / SHORTAGE / OUTBOUND / ADJUST',
    `on_hand_delta` INT NOT NULL COMMENT '실물 증감',
    `allocated_delta` INT NOT NULL COMMENT '선점 증감',
    `on_hand_after` INT NOT NULL COMMENT '변경 후 실물',
    `allocated_after` INT NOT NULL COMMENT '변경 후 선점',
    `ref_type` VARCHAR(20) NOT NULL COMMENT 'PURCHASE_ORDER / DELIVERY_ORDER / ADJUSTMENT',
    `ref_id` BIGINT COMMENT '근거 문서 id (조정은 NULL)',
    `reason` VARCHAR(200) COMMENT '사유 (조정·결품 필수)',
    `created_by` VARCHAR(50) COMMENT '처리자',
    `created_at` DATETIME NOT NULL COMMENT '발생일시',
    PRIMARY KEY (`id`)
) COMMENT = '재고 이력';

CREATE TABLE `allocation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `delivery_order_item_id` BIGINT NOT NULL COMMENT '출고지시 품목',
    `stock_id` BIGINT NOT NULL COMMENT '할당된 재고 행 (로케이션·소비기한은 stock 조인)',
    `allocated_qty` INT NOT NULL COMMENT '할당수량',
    `pick_seq` INT COMMENT '피킹 순번 (피킹 시작 시 로케이션 코드 순으로 부여)',
    `picked_qty` INT COMMENT '피킹수량 (입력 전 NULL)',
    `short_qty` INT NOT NULL DEFAULT 0 COMMENT '결품수량 (2차)',
    `short_reason` VARCHAR(30) COMMENT '결품사유 (2차)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING 대기 / PICKED 완료 / SHORT 결품 / CANCELED 취소',
    `created_at` DATETIME NOT NULL COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL COMMENT '수정일시',
    PRIMARY KEY (`id`)
) COMMENT = '할당 겸 피킹 항목 (출고지시 품목 × 재고 행 1행)';

ALTER TABLE `zone` ADD CONSTRAINT `fk_zone_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`);
ALTER TABLE `orders` ADD CONSTRAINT `fk_orders_delivery_order` FOREIGN KEY (`delivery_order_id`) REFERENCES `delivery_order` (`id`);
ALTER TABLE `location` ADD CONSTRAINT `fk_location_zone` FOREIGN KEY (`zone_id`) REFERENCES `zone` (`id`);
ALTER TABLE `purchase_order_item` ADD CONSTRAINT `fk_po_item_po` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`);
ALTER TABLE `purchase_order_item` ADD CONSTRAINT `fk_po_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`);
ALTER TABLE `order_item` ADD CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`);
ALTER TABLE `order_item` ADD CONSTRAINT `fk_order_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`);
ALTER TABLE `delivery_order_item` ADD CONSTRAINT `fk_do_item_do` FOREIGN KEY (`delivery_order_id`) REFERENCES `delivery_order` (`id`);
ALTER TABLE `delivery_order_item` ADD CONSTRAINT `fk_do_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`);
ALTER TABLE `inspection` ADD CONSTRAINT `fk_inspection_po_item` FOREIGN KEY (`purchase_order_item_id`) REFERENCES `purchase_order_item` (`id`);
ALTER TABLE `stock` ADD CONSTRAINT `fk_stock_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`);
ALTER TABLE `stock` ADD CONSTRAINT `fk_stock_location` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`);
ALTER TABLE `putaway` ADD CONSTRAINT `fk_putaway_inspection` FOREIGN KEY (`inspection_id`) REFERENCES `inspection` (`id`);
ALTER TABLE `putaway` ADD CONSTRAINT `fk_putaway_location` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`);
ALTER TABLE `stock_history` ADD CONSTRAINT `fk_history_stock` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`id`);
ALTER TABLE `allocation` ADD CONSTRAINT `fk_allocation_do_item` FOREIGN KEY (`delivery_order_item_id`) REFERENCES `delivery_order_item` (`id`);
ALTER TABLE `allocation` ADD CONSTRAINT `fk_allocation_stock` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`id`);
