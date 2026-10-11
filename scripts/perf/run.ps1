# Consistency test, one run: reset -> (ship prep) -> pre snapshot -> k6 -> post snapshot
# Usage (from WMS folder):
#   powershell -ExecutionPolicy Bypass -File scripts/perf/run.ps1 -S carry -Run 1 -Database wms_db -Sample wms-core/src/main/resources/db/sample/sample2.sql
# Output (scripts/perf/result):
#   {S}-{Run}.json        k6 response summary
#   {S}-{Run}-pre-db.txt  DB state before k6
#   {S}-{Run}-db.txt      DB state after k6
# k6 metrics are also pushed to Prometheus (monitoring stack) with tag testid={S}-{Run}
param(
  [Parameter(Mandatory = $true)][ValidateSet('alloc', 'carry', 'ship')][string]$S,
  [Parameter(Mandatory = $true)][int]$Run,
  [string]$Database = 'wms_before',
  [int]$Vus = 20,
  [string]$Sample = ''
)

$perf = $PSScriptRoot
$out = Join-Path $perf 'result'
if (-not (Test-Path $out)) { New-Item -ItemType Directory -Path $out | Out-Null }
if ($Sample -eq '') {
  $Sample = Join-Path $perf '..\..\..\WMS-before\wms-core\src\main\resources\db\sample\sample2.sql'
}
$checkSql = Join-Path $perf "check_$S.sql"

Write-Host "== [$S #$Run] DB=$Database VUS=$Vus"

# 1. reset data (sample2)
docker cp $Sample wms-db-1:/tmp/perf_sample.sql | Out-Null
docker exec -e MYSQL_PWD=1234 wms-db-1 sh -c "mysql -uroot --default-character-set=utf8mb4 $Database < /tmp/perf_sample.sql"

# 2. ship prep: document 9 -> PICKING, its picking lines (detail 19, 20) -> PICKED (ED-52 rule)
if ($S -eq 'ship') {
  docker exec -e MYSQL_PWD=1234 wms-db-1 mysql -uroot $Database -e "UPDATE document SET status='PICKING' WHERE document_id=9; UPDATE document_item_detail SET status='PICKED' WHERE detail_id IN (19, 20);"
}

# 3. pre snapshot (DB state right before k6)
$pre = Get-Content $checkSql -Raw | docker exec -i -e MYSQL_PWD=1234 wms-db-1 mysql -uroot -t $Database
$pre | Out-File -Encoding utf8 (Join-Path $out "$S-$Run-pre-db.txt")
Write-Host "-- before"
$pre

# 4. k6 concurrent requests (+ push k6 metrics to Prometheus remote-write receiver)
docker run --rm -i -v "${perf}:/scripts" `
  -e S=$S -e RUN=$Run -e VUS=$Vus -e BASE=http://host.docker.internal:8080 -e OUT=/scripts/result `
  -e K6_PROMETHEUS_RW_SERVER_URL=http://host.docker.internal:9090/api/v1/write `
  -e "K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),avg,max" `
  grafana/k6 run --quiet -o experimental-prometheus-rw --tag testid="$S-$Run" /scripts/consistency.js

# 5. post snapshot (DB state after k6)
$post = Get-Content $checkSql -Raw | docker exec -i -e MYSQL_PWD=1234 wms-db-1 mysql -uroot -t $Database
$post | Out-File -Encoding utf8 (Join-Path $out "$S-$Run-db.txt")
Write-Host "-- after"
$post