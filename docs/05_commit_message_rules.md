# 05. 커밋 메시지 규칙

## 형식

```
<type>(<scope>): <제목> (#이슈번호)

<본문 — 선택. 왜 바꿨는지. 무엇을 바꿨는지는 diff가 말해준다>
```

- 제목은 **한글 허용**, 50자 이내, 마침표 없음.
- scope는 도메인명 소문자 (`order`, `stock`, `auth`, `global`). 여러 도메인이면 생략.
- 이슈(지라/깃허브) 번호는 있으면 반드시 붙인다.

## type — 이 일곱 개만

| type | 언제 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 구조 개선 |
| `perf` | **성능 개선** — 수치가 있으면 제목에 넣는다 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서 (결정 로그, 트러블슈팅, README) |
| `chore` | 설정, 빌드, 의존성, 더미데이터 스크립트 |

`perf`를 따로 두는 이유: 마지막 주에 `git log --oneline --grep=perf`로 성능 커밋만 뽑아 Before/After 정리에 바로 쓴다.

## 예시

```
feat(order): 주문 생성 API 추가 (#12)
fix(stock): 재고 차감 시 음수 허용되던 버그 수정 (#18)
perf(order): 주문 목록 N+1 제거 — 쿼리 101회 → 2회, 320ms → 45ms (#31)
perf(stock): stock(product_id) 인덱스 추가 — 조회 1.8s → 12ms (#33)
refactor(global): 공통 응답 래퍼를 record로 변경
docs: 트러블슈팅 — 재고 동시성 비관적 락 도입 기록
chore: 더미데이터 100만 건 생성 스크립트 추가
```

## 나쁜 예 → 고친 예

| 나쁜 예 | 왜 | 고친 예 |
|---|---|---|
| `수정` | 뭘 | `fix(order): 주문 취소 시 상태 검증 누락 수정` |
| `feat: 주문 기능 완성` | 너무 큼. 커밋을 쪼개라 | `feat(order): 주문 생성 API`, `feat(order): 주문 목록 화면` |
| `Fix bug` | 영어여도 상관없지만 내용이 없음 | `fix(auth): 토큰 만료 시 401 대신 500 나던 문제 수정` |

## 커밋 크기

- 커밋 하나 = 설명 한 문장으로 끝나는 변화 하나.
- "그리고"가 들어가면 두 커밋이다.
- 하루에 여러 번 커밋해도 된다. PR은 squash merge라 develop 히스토리는 깔끔하게 유지된다.

## 하지 말 것

- `.env`, `application-local.yml`, DB 비밀번호 커밋. `.gitignore` 첫날 확인.
- `git add .` 후 확인 없이 커밋. `git status`로 IDE 설정 파일이 섞였는지 본다.
- `--force` 푸시 (자기 feature 브랜치에서만 예외적으로 허용, develop/main은 절대 금지).
