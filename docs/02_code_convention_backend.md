## 패키지 구조 — 도메인 기준

```
com.team.project
├── global/                      # 공통. 한 사람이 뼈대 만들 때 먼저 작성
│   ├── config/                  # WebConfig, SwaggerConfig, JpaConfig
│   ├── exception/               # GlobalExceptionHandler, BusinessException, ErrorCode(enum)
│   ├── response/                # ApiResponse<T> 공통 래퍼
│   ├── entity/                  # BaseEntity (createdAt, updatedAt) // 공통 사용 엔티티
│   └── security/                # 인증 관련
└── domain/
    └── order/                   # 도메인 하나 = 폴더 하나 = 담당자 한 명
        ├── controller/          # OrderController
        ├── service/             # OrderService
        ├── repository/          # OrderRepository
        ├── entity/              # Order, OrderItem, OrderStatus(enum)
        └── dto/                 # OrderCreateRequest, OrderResponse
```

## 네이밍

| 대상            | 규칙                                                          | 예                             |
| --------------- | ------------------------------------------------------------- | ------------------------------ |
| 엔티티          | 단수 PascalCase                                               | `Order`, `OrderItem`           |
| 테이블          | snake_case 단수 (`@Table(name = "orders")`처럼 예약어만 예외) | `order_item`                   |
| 컬럼            | snake_case                                                    | `created_at`, `stock_quantity` |
| 요청 DTO        | `{도메인}{동작}Request`                                       | `OrderCreateRequest`           |
| 응답 DTO        | `{도메인}Response`, 목록은 `{도메인}ListResponse`             | `OrderResponse`                |
| 컨트롤러 메서드 | create / get / getList / update / delete                      | `getOrderList()`               |
| 서비스 메서드   | 컨트롤러와 같은 동사                                          | `createOrder()`                |
| 리포지토리      | JPA 명명 규칙 그대로                                          | `findByStatus()`               |
| boolean         | is / has 접두                                                 | `isActive`, `hasStock`         |
| 상수            | UPPER_SNAKE                                                   | `MAX_ORDER_QUANTITY`           |
| 상태값          | **반드시 enum**. 문자열 상태 금지                             | `OrderStatus.PENDING`          |
| 에러 코드       | `{도메인}_{번호}` enum                                        | `ORDER_001`                    |

## 계층 규칙

- Controller: 요청 검증(`@Valid`), 서비스 호출, `ApiResponse`로 감싸 반환. **로직 없음.**
- Service: 비즈니스 로직, 트랜잭션(`@Transactional`). 클래스에 `readOnly = true`, 쓰기 메서드에만 `@Transactional`.
- Repository: 쿼리만.
- **엔티티를 컨트롤러 밖으로 내보내지 않는다.** 항상 DTO로 변환.
- DTO ↔ 엔티티 변환은 DTO 안의 정적 메서드(`OrderResponse.from(order)`) 또는 엔티티의 `toEntity()`로 통일.
- 예외는 `BusinessException(ErrorCode)`만 던진다. `RuntimeException` 직접 던지기 금지.

## 공통 응답 형식 (global/response)

```json
{ "success": true,  "data": { ... }, "message": null }
{ "success": false, "data": null,   "message": "재고가 부족합니다", "code": "STOCK_001" }
```

## 엔티티 규칙

- 모든 엔티티는 `BaseEntity` 상속 (`createdAt`, `updatedAt` 자동).
- `@Setter` 금지. 상태 변경은 의미 있는 메서드로 (`order.cancel()`, `stock.decrease(qty)`).
- 연관관계는 `LAZY` 기본. `EAGER` 금지.
- 생성자는 `@Builder`
