// 정합성 측정: 같은 자원(또는 같은 재고 행)에 동시 요청 → 응답 집계 (DB 확인은 check_*.sql)
// 보통은 run.ps1 로 실행.
//   S = carry     같은 검수 기록을 VUS 명이 동시 적재 (중복 적재)          -Vus 150 이면 과부하
//       alloc     같은 출고 문서를 VUS 명이 동시 할당
//       ship      같은 출고 문서를 VUS 명이 동시 출고확정
//       oversell  서로 다른 출고 문서 2개가 같은 재고(12)의 가용 50 을 동시 할당 (초과 할당)
//       cross     적재(+80) 와 할당(+50) 이 같은 재고 행(900) 을 동시 변경 (덮어쓰기)
//       spread    서로 다른 검수 기록 20개 → 서로 다른 칸 20개 (충돌 없음, 속도 비용)
// 응답: ok 2xx / rejected 409 / busy 503 / bad 그 외 4xx(스크립트 오류) / failed 5xx / neterr 연결 실패
//       _a, _b = 두 종류를 섞는 시나리오의 앞 작업 / 뒤 작업
import http from "k6/http";
import { Counter } from "k6/metrics";

const BASE = __ENV.BASE || "http://localhost:8080";
const S = __ENV.S;
const RUN = __ENV.RUN || "0";
const VUS = Number(__ENV.VUS || 20);
const OUT = __ENV.OUT || "scripts/perf/result";

const C = {};
for (const g of ["a", "b"]) {
  C[g] = {
    ok: new Counter(`ok_2xx_${g}`),
    rejected: new Counter(`rejected_409_${g}`),
    busy: new Counter(`busy_503_${g}`),
    bad: new Counter(`bad_4xx_${g}`),
    failed: new Counter(`failed_5xx_${g}`),
    neterr: new Counter(`neterr_0_${g}`),
  };
}

const JSON_HEADER = { headers: { "Content-Type": "application/json" } };
const half = Math.max(1, Math.floor(VUS / 2));
const once = (exec, vus) => ({
  executor: "per-vu-iterations",
  vus,
  iterations: 1,
  maxDuration: "60s",
  exec,
});

// 두 종류를 섞는 시나리오는 a·b 를 동시에 시작한다
const PLANS = {
  carry: { a: once("carry", VUS) },
  alloc: { a: once("alloc14", VUS) },
  ship: { a: once("ship9", VUS) },
  oversell: { a: once("alloc14", half), b: once("alloc990", half) },
  cross: { a: once("carryCross", half), b: once("allocCross", half) },
  spread: { a: once("carrySpread", VUS) },
};
if (!PLANS[S])
  throw new Error("S 는 " + Object.keys(PLANS).join(" | ") + " 중 하나");
export const options = { scenarios: PLANS[S] };

function record(res, g) {
  const c = C[g];
  if (res.status === 0) c.neterr.add(1);
  else if (res.status < 300) c.ok.add(1);
  else if (res.status === 409) c.rejected.add(1);
  else if (res.status === 503) c.busy.add(1);
  else if (res.status < 500) c.bad.add(1);
  else c.failed.add(1);
}
const put = (path, body) =>
  http.put(`${BASE}${path}`, body ? JSON.stringify(body) : null, JSON_HEADER);
const post = (path, body) =>
  http.post(`${BASE}${path}`, JSON.stringify(body), JSON_HEADER);

// ---------- 요청 ----------
// 검수 기록 14(신라면 LOT-02 80) → 칸 13(무제한)
export function carry() {
  record(put("/wms/inspections", { detailId: 14, locationId: 13 }), "a");
}

// 문서 14 진라면 60 = 재고 12 50 + 재고 3 10
export function alloc14() {
  record(
    post("/wms/allocations/14/pickinglist", [
      { documentItemId: 28, stockId: 12, qty: 50 },
      { documentItemId: 28, stockId: 3, qty: 10 },
    ]),
    "a",
  );
}
export function ship9() {
  record(put("/wms/outbounds/9/ship"), "a");
}

// oversell: 준비 데이터로 출고 문서 990(진라면 50) 을 만들어 둠
//   a = 문서 14 (재고 12 에서 50), b = 문서 990 (재고 12 에서 50) → 재고 12 가용은 50 뿐이라 하나만 성공해야 정상
export function alloc990() {
  record(
    post("/wms/allocations/990/pickinglist", [
      { documentItemId: 990, stockId: 12, qty: 50 },
    ]),
    "b",
  );
}

// cross: 준비 데이터로 재고 900(신라면 LOT-02 @칸13, 50개) 을 만들어 둠
//   a 적재: 검수 기록 14(같은 LOT 80) 를 칸 13 에 → 재고 900 qty +80
//   b 할당: 문서 10 신라면 50 = 재고 900 50, 센트룸 5 = 재고 8 5 → 재고 900 선점 +50
export function carryCross() {
  record(put("/wms/inspections", { detailId: 14, locationId: 13 }), "a");
}
export function allocCross() {
  record(
    post("/wms/allocations/10/pickinglist", [
      { documentItemId: 21, stockId: 900, qty: 50 },
      { documentItemId: 22, stockId: 8, qty: 5 },
    ]),
    "b",
  );
}

// spread: 준비 데이터로 검수 기록 901~920, 칸 901~920 을 만들어 둠. VU n → 기록 900+n 을 칸 900+n 에
export function carrySpread() {
  record(
    put("/wms/inspections", { detailId: 900 + __VU, locationId: 900 + __VU }),
    "a",
  );
}

// ---------- 끝난 뒤: 요약 저장 ----------
export function handleSummary(data) {
  const n = (k) => (data.metrics[k] ? data.metrics[k].values.count : 0);
  const group = (g) => ({
    ok: n(`ok_2xx_${g}`),
    rejected: n(`rejected_409_${g}`),
    busy: n(`busy_503_${g}`),
    bad: n(`bad_4xx_${g}`),
    failed: n(`failed_5xx_${g}`),
    neterr: n(`neterr_0_${g}`),
  });
  const d = data.metrics.http_req_duration.values;
  const summary = {
    scenario: S,
    run: Number(RUN),
    vus: VUS,
    a: group("a"),
    b: group("b"),
    p95_ms: Math.round(d["p(95)"]),
    p99_ms: Math.round(d["p(99)"] || 0),
    max_ms: Math.round(d.max),
    at: new Date().toISOString(),
  };
  return {
    stdout: JSON.stringify(summary) + "\n",
    [`${OUT}/${S}-${RUN}.json`]: JSON.stringify(summary, null, 2),
  };
}
