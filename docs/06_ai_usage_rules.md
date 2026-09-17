```markdown
# 프로젝트: {프로젝트명}

{한 줄 설명. 예: 식품 창고의 입고·재고·출고를 관리하는 웹 서비스}

## 스택 (버전 고정)

- Java 17, Spring Boot 4.1.1, Spring Data JPA, MySQL 8.x
- React 18, Vite, axios
- 자세한 버전: build.gradle, package.json, docker-compose.yml 참고

## 코드를 쓸 때 반드시 지킬 것

- 패키지: domain/{도메인}/controller|service|repository|entity|dto, 공통은 global/
- 컨트롤러에 로직 금지. 엔티티를 컨트롤러 밖으로 내보내지 않는다 (DTO 변환).
- 응답은 항상 global.response.ApiResponse<T>로 감싼다.
- 예외는 BusinessException(ErrorCode)만 던진다.
- 상태값은 enum. 문자열 상태 금지.
- 엔티티에 @Setter 금지. 연관관계 LAZY.
- 프론트: 컴포넌트에서 직접 axios 호출 금지, api/{도메인}.js를 거친다.
- 새 의존성 추가 금지. 필요하면 제안만 하고 사람이 결정한다.

## 네이밍

- 엔티티 단수 PascalCase / 테이블·컬럼 snake_case
- DTO: {도메인}{동작}Request, {도메인}Response
- 메서드: create / get / getList / update / delete
- boolean: is/has 접두. 상수 UPPER_SNAKE
- 프론트: 컴포넌트 PascalCase, 훅 useXxx, 핸들러 handleXxx, props 콜백 onXxx

## API 규칙

- /api/{기능명}/{복수명사}, kebab-case, 동사 금지
- 목록: page, size, sort 파라미터. 응답 content/page/size/totalElements/totalPages
- 날짜 ISO 8601, 금액 정수, enum 대문자 문자열

## 응답 형식

{ "success": true, "data": {...}, "message": null }
{ "success": false, "data": null, "message": "...", "code": "ORDER_001" }

## 작업 방식

- 코드를 만들 때 왜 그렇게 했는지 한두 줄 설명을 함께 준다.
- 대안이 있으면 대안과 트레이드오프를 먼저 말하고, 선택은 사람이 한다.
- 파일을 통째로 다시 쓰지 말고 바뀌는 부분만 보여준다.
```
