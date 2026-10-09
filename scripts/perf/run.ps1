# Consistency test, one run: reset -> (scenario prep) -> pre snapshot -> k6 -> post snapshot
# Usage (from WMS folder):
#   powershell -ExecutionPolicy Bypass -File scripts/perf/run.ps1 -S carry -Run 1 -Database wms_db -Sample ..\WMS-measure\wms-core\src\main\resources\db\sample\sample2.sql
#   S = alloc | carry | ship | oversell | cross | spread   (-Vus 150 with carry = overload)
# Output (scripts/perf/result):
#   {S}-{Run}.json        k6 response summary (a/b groups, p95, p99, max)
#   {S}-{Run}-pre-db.txt  DB state before k6
#   {S}-{Run}-db.txt      DB state after k6
# k6 metrics are also pushed to Prometheus (monitoring stack) with tag testid={S}-{Run}
param(
  [Parameter(Mandatory = $true)][ValidateSet('alloc', 'carry', 'ship', 'oversell', 'cross', 'spread')][string]$S,
  [Parameter(Mandatory = $true)][int]$Run,
  [string]$Database = 'wms_before',
  [int]$Vus = 20,
  [string]$Sample = ''
)

$dbPwd = 1234

$perf = $PSScriptRoot
$out = Join-Path $perf 'result'
if (-not (Test-Path $out)) { New-Item -ItemType Directory -Path $out | Out-Null }
if ($Sample -eq '') {
  $Sample = Join-Path $perf '..\..\..\WMS-before\wms-core\src\main\resources\db\sample\sample2.sql'
}
$checkSql = Join-Path $perf "check_$S.sql"

function Invoke-Sql([string]$sql) {
  docker exec -e MYSQL_PWD=$dbPwd wms-db-1 mysql -uroot $Database -e $sql
}

Write-Host "== [$S #$Run] DB=$Database VUS=$Vus"

# 1. reset data (sample2 TRUNCATE + INSERT)
docker cp $Sample wms-db-1:/tmp/perf_sample.sql | Out-Null
docker exec -e MYSQL_PWD=$dbPwd wms-db-1 sh -c "mysql -uroot --default-character-set=utf8mb4 $Database < /tmp/perf_sample.sql"

# 2. scenario prep (sample2 가 매번 TRUNCATE 하므로 회차마다 새로 만든다)
$prep = @{
  # 문서 9 -> PICKING, 피킹 줄(detail 19, 20) -> PICKED (ED-52 규칙)
  ship     = "UPDATE document SET status='PICKING' WHERE document_id=9; UPDATE document_item_detail SET status='PICKED' WHERE detail_id IN (19, 20);"
  # 출고 문서 990 (진라면 50) : 문서 14 와 재고 12 를 두고 경쟁
  oversell = "INSERT INTO document (tenant_id, document_id, document_no, type, partner_id, expected_at, status) VALUES (1, 990, 'OUT-PERF-990', 'OUTBOUND', 4, NOW(), 'WAITING'); " +
             "INSERT INTO document_item (document_item_id, document_id, product_id, lot_id, expected_qty) VALUES (990, 990, 2, NULL, 50);"
  # 재고 900 (신라면 LOT-02 @칸13, 50개) : 적재와 할당이 같은 행을 건드리게
  cross    = "INSERT INTO stock (tenant_id, stock_id, lot_id, location_id, qty, allocated_qty) VALUES (1, 900, 2, 13, 50, 0);"
  # 칸 901~920, 검수 기록 901~920 (즉석밥 LOT 9, 1개씩) : 서로 겹치지 않는 적재 20건
  spread   = "INSERT INTO location (location_id, location_code, is_active) WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 20) SELECT 900 + i, CONCAT('PERF-', i), TRUE FROM n; " +
             "INSERT INTO document_item_detail (detail_id, document_item_id, lot_id, qty, status, remark) WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 20) SELECT 900 + i, 10, 9, 1, 'INSPECTED', 'perf' FROM n;"
}
if ($prep.ContainsKey($S)) { Invoke-Sql $prep[$S] }

# 3. pre snapshot (DB state right before k6)
$pre = Get-Content $checkSql -Raw | docker exec -i -e MYSQL_PWD=$dbPwd wms-db-1 mysql -uroot -t $Database
$pre | Out-File -Encoding utf8 (Join-Path $out "$S-$Run-pre-db.txt")
Write-Host "-- before"
$pre

# 4. k6 concurrent requests (+ push k6 metrics to Prometheus remote-write receiver)
docker run --rm -i -v "${perf}:/scripts" `
  -e S=$S -e RUN=$Run -e VUS=$Vus -e BASE=http://host.docker.internal:8080 -e OUT=/scripts/result `
  -e K6_PROMETHEUS_RW_SERVER_URL=http://host.docker.internal:9090/api/v1/write `
  -e "K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),avg,max" `
  grafana/k6 run --quiet --summary-trend-stats "avg,p(95),p(99),max" -o experimental-prometheus-rw --tag testid="$S-$Run" /scripts/consistency.js

# 5. post snapshot (DB state after k6)
$post = Get-Content $checkSql -Raw | docker exec -i -e MYSQL_PWD=$dbPwd wms-db-1 mysql -uroot -t $Database
$post | Out-File -Encoding utf8 (Join-Path $out "$S-$Run-db.txt")
Write-Host "-- after"
$post