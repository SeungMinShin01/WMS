// 정합성 측정: 같은 자원에 동시 요청 → 응답 코드 집계 (DB 확인은 check_*.sql)
// 보통은 run.ps1 로 실행.
//   S = alloc | carry | ship
//
// 응답 분류
//   ok       : 2xx  성공
//   rejected : 409  서버 규칙이 막음 (정상이면 VUS-1 건)
//   bad      : 409 외 4xx  요청 자체가 틀림 → 0 이 아니면 스크립트부터 고칠 것
//   failed   : 5xx  서버 에러 (DB CHECK 위반 등)
import http from "k6/http";
import { Counter } from "k6/metrics";

// ---------- 설정값 (실행 명령의 -e 로 바꿀 수 있음) ----------
const BASE = __ENV.BASE || "http://localhost:8080";
const S = __ENV.S;
const RUN = __ENV.RUN || "0"; // 회차 번호 (0 = 리허설)
const VUS = Number(__ENV.VUS || 20); // 동시에 요청할 사람 수
const OUT = __ENV.OUT || "scripts/perf/result";

// ---------- 응답 코드별 카운터 (VU 20명 결과를 k6 가 합쳐 줌) ----------
const ok = new Counter("ok_2xx");
const rejected = new Counter("rejected_409");
const bad = new Counter("bad_4xx");
const failed = new Counter("failed_5xx");

// ---------- 실행 방식: VU 마다 딱 1번, 거의 동시에 ----------
export const options = {
  scenarios: {
    race: {
      executor: "per-vu-iterations",
      vus: VUS,
      iterations: 1,
      maxDuration: "30s",
    },
  },
};

const JSON_HEADER = { headers: { "Content-Type": "application/json" } };

// ---------- 시나리오별 요청 ----------
function call() {
  if (S === "alloc") {
    // 문서 14 · 품목 줄 28(진라면 60)
    // 규칙: 한 줄은 요청 수량(60)을 정확히 채워야 함 → 재고 12(가용 50) 50 + 재고 3(가용 20) 10
    return http.post(
      `${BASE}/wms/allocations/14/pickinglist`,
      JSON.stringify([
        { documentItemId: 28, stockId: 12, qty: 50 },
        { documentItemId: 28, stockId: 3, qty: 10 },
      ]),
      JSON_HEADER,
    );
  }
  if (S === "carry") {
    // 검수 기록 14(신라면 LOT-02 80개) 를 칸 13(무제한 빈 칸) 에 적재
    return http.put(
      `${BASE}/wms/inspections`,
      JSON.stringify({ detailId: 14, locationId: 13 }),
      JSON_HEADER,
    );
  }
  if (S === "ship") {
    // 문서 9 출고확정 (run.ps1 이 측정 전 PICKING 으로 바꿔 둠)
    return http.put(`${BASE}/wms/outbounds/9/ship`, null);
  }
  throw new Error("S 는 alloc | carry | ship 중 하나");
}

// ---------- VU 한 명이 하는 일: 요청 1번 → 응답 코드 분류 ----------
export default function () {
  const res = call();
  if (res.status < 300) ok.add(1);
  else if (res.status === 409) rejected.add(1);
  else if (res.status < 500) bad.add(1);
  else failed.add(1);
}

// ---------- 끝난 뒤: 요약 한 줄 출력 + 파일 저장 ----------
export function handleSummary(data) {
  const count = (k) => (data.metrics[k] ? data.metrics[k].values.count : 0);
  const summary = {
    scenario: S,
    run: Number(RUN),
    vus: VUS,
    ok: count("ok_2xx"),
    rejected: count("rejected_409"),
    bad: count("bad_4xx"),
    failed: count("failed_5xx"),
    p95_ms: Math.round(data.metrics.http_req_duration.values["p(95)"]),
    at: new Date().toISOString(),
  };
  return {
    stdout: JSON.stringify(summary) + "\n",
    [`${OUT}/${S}-${RUN}.json`]: JSON.stringify(summary, null, 2),
  };
}
