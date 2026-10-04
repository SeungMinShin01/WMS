# Consistency test, one run: reset -> (ship prep) -> k6 -> DB check
# Usage (from WMS folder):
#   powershell -ExecutionPolicy Bypass -File scripts/perf/run.ps1 -S alloc -Run 1
# Other DB / sample:
#   ... -S alloc -Run 1 -Database wms_db -Sample wms-core/src/main/resources/db/sample/sample2.sql
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

Write-Host "== [$S #$Run] DB=$Database VUS=$Vus"

# 1. reset data (sample2)
docker cp $Sample wms-db-1:/tmp/perf_sample.sql | Out-Null
docker exec -e MYSQL_PWD=1234 wms-db-1 sh -c "mysql -uroot --default-character-set=utf8mb4 $Database < /tmp/perf_sample.sql"

# 2. ship prep (document 9 -> PICKING)
if ($S -eq 'ship') {
  docker exec -e MYSQL_PWD=1234 wms-db-1 mysql -uroot $Database -e "UPDATE document SET status='PICKING' WHERE document_id=9"
}

# 3. k6 concurrent requests
docker run --rm -i -v "${perf}:/scripts" -e S=$S -e RUN=$Run -e VUS=$Vus -e BASE=http://host.docker.internal:8080 -e OUT=/scripts/result grafana/k6 run --quiet /scripts/consistency.js

# 4. DB check -> screen + file
$result = Get-Content (Join-Path $perf "check_$S.sql") -Raw | docker exec -i -e MYSQL_PWD=1234 wms-db-1 mysql -uroot -t $Database
$result | Out-File -Encoding utf8 (Join-Path $out "$S-$Run-db.txt")
$result