// 속도 측정 (k6) — API 하나를 ITER 번 호출해 응답 시간 분포를 남긴다
// speed.ps1 이 환경변수로 호출한다. 직접 실행 예:
//   docker run --rm -i -v "${PWD}/scripts/perf:/scripts" -e EP=stocks -e RUN=1 grafana/k6 run /scripts/speed/speed.js
//
// EP     : 측정할 API (아래 READS / WRITES 의 키)
// ITER   : 요청 수 (기본 50)        VUS : 동시 사용자 수 (기본 5)
// OFFSET : 쓰기 대상 시작 번호 (회차마다 겹치지 않게 speed.ps1 이 계산)
// T      : 쓰기 대상 개수 (gen_data.sql 의 @T 와 같아야 함)
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://host.docker.internal:8080';
const EP = __ENV.EP;
const RUN = __ENV.RUN || '1';
const ITER = parseInt(__ENV.ITER || '50');
const VUS = parseInt(__ENV.VUS || '5');
const OFFSET = parseInt(__ENV.OFFSET || '0');
const T = parseInt(__ENV.T || '500');
const OUT = __ENV.OUT || '/scripts/result-speed';
const TAG = __ENV.TAG || 'speed';

const JSON_HDR = { headers: { 'Content-Type': 'application/json' }, timeout: '120s' };
const GET_OPT = { timeout: '120s' };

// 읽기: 같은 주소를 반복 호출 (데이터를 바꾸지 않음)
const READS = {
  stocks:          () => http.get(`${BASE}/wms/stocks`, GET_OPT),                              // 재고 목록
  history:         () => http.get(`${BASE}/wms/stocks/history`, GET_OPT),                      // 재고 이력
  inbounds:        () => http.get(`${BASE}/wms/inbounds`, GET_OPT),                            // 입고 목록
  inbound_detail:  () => http.get(`${BASE}/wms/inbounds/1000001`, GET_OPT),                    // 입고 상세
  inspections:     () => http.get(`${BASE}/wms/inspections/1000001`, GET_OPT),                 // 검수 결과 목록
  locations:       () => http.get(`${BASE}/wms/inspections/locations`, GET_OPT),               // 적재 칸 선택 목록
  recommend:       () => http.get(`${BASE}/wms/inspections/recommend/${3000000 + T}`, GET_OPT), // 적치 추천
  outbounds:       () => http.get(`${BASE}/wms/outbounds`, GET_OPT),                           // 출고 목록
  outbound_detail: () => http.get(`${BASE}/wms/outbounds/2000001`, GET_OPT),                   // 출고 상세 (출고 가능 수량 계산)
  preview:         () => http.get(`${BASE}/wms/allocations/${4000000 + T}/preview`, GET_OPT),  // 할당 미리보기
  picking:         () => http.get(`${BASE}/wms/allocations/2000001`, GET_OPT),                 // 피킹 리스트
};

// 쓰기: 요청마다 다른 대상 (i = OFFSET + 1 ~ OFFSET + ITER)
const WRITES = {
  carry: (i) => http.put(`${BASE}/wms/inspections`,                                           // 적재
    JSON.stringify({ detailId: 3000000 + i, locationId: 3000000 + i }), JSON_HDR),
  alloc: (i) => http.post(`${BASE}/wms/allocations/${4000000 + i}/pickinglist`,               // 할당 (피킹리스트 생성)
    JSON.stringify([{ documentItemId: 4000000 + i, stockId: 4000000 + i, qty: 1 }]), JSON_HDR),
  ship:  (i) => http.put(`${BASE}/wms/outbounds/${5000000 + i}/ship`, null, JSON_HDR),         // 출고확정
};

if (!READS[EP] && !WRITES[EP]) throw new Error(`알 수 없는 EP: ${EP}`);
if (WRITES[EP] && OFFSET + ITER >= T) throw new Error(`쓰기 대상 부족: OFFSET ${OFFSET} + ITER ${ITER} >= T ${T}`);

export const options = {
  scenarios: {
    speed: { executor: 'shared-iterations', vus: VUS, iterations: ITER, maxDuration: '15m' },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  tags: { testid: `${TAG}-${EP}-${RUN}` },
};

let shown = 0;   // 실패 응답은 VU 마다 처음 2건만 화면에 찍어 원인 확인

export default function () {
  let res;
  if (READS[EP]) {
    res = READS[EP]();
  } else {
    const i = OFFSET + exec.scenario.iterationInTest + 1;   // 0 부터 → 대상 번호
    res = WRITES[EP](i);
  }
  const ok = check(res, { '2xx': (r) => r.status >= 200 && r.status < 300 });
  if (!ok && shown < 2) {
    shown++;
    console.warn(`[${EP}] ${res.status} ${String(res.body).slice(0, 200)}`);
  }
}

export function handleSummary(data) {
  const d = data.metrics.http_req_duration.values;
  const reqs = data.metrics.http_reqs.values.count;
  const failed = data.metrics.http_req_failed ? data.metrics.http_req_failed.values.passes : 0;
  const recv = data.metrics.data_received ? data.metrics.data_received.values.count : 0;
  const result = {
    ep: EP, run: Number(RUN), iter: ITER, vus: VUS, offset: OFFSET,
    requests: reqs, failed: failed,
    avg_ms: Math.round(d.avg), min_ms: Math.round(d.min), med_ms: Math.round(d.med),
    p95_ms: Math.round(d['p(95)']), p99_ms: Math.round(d['p(99)']), max_ms: Math.round(d.max),
    rps: Number((data.metrics.http_reqs.values.rate).toFixed(2)),
    avg_resp_kb: reqs ? Math.round(recv / reqs / 1024) : 0,
    dur_ms: Math.round(data.state.testRunDurationMs),   // 회차 소요 시간 (Grafana PNG 구간 계산용)
    at: new Date().toISOString(),
  };
  const line = `${EP} #${RUN}  p95 ${result.p95_ms}ms  med ${result.med_ms}ms  max ${result.max_ms}ms  실패 ${failed}/${reqs}  응답 ${result.avg_resp_kb}KB\n`;
  return {
    stdout: line,
    [`${OUT}/${EP}-${RUN}.json`]: JSON.stringify(result, null, 2),
  };
}
