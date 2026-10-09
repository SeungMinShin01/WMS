# 속도 측정: 데이터 규모 하나에 대해 API 별로 예열 → 본 측정 반복
# 서버는 미리 띄워 둔다 (AUTH_ENABLED=false, SQL 로그 끔)
#
# 사용법 (WMS 폴더에서, DB 비밀번호는 환경변수로):
#   $env:WMS_DB_PWD = "<DB 비밀번호>"
#   powershell -ExecutionPolicy Bypass -File scripts/perf/speed/speed.ps1 -Scale 1000
#   powershell -ExecutionPolicy Bypass -File scripts/perf/speed/speed.ps1 -Scale 10000 -Only stocks,history
#
# 결과: scripts/perf/result-speed-<Scale>/
#   data.txt          만든 데이터 행 수
#   <EP>-<회차>.json   회차별 응답 시간 (예열 회차는 끝나면 지움)
param(
  [Parameter(Mandatory = $true)][ValidateSet(1000, 10000, 100000)][int]$Scale,
  [int]$Runs = 5,            # 본 측정 회차
  [int]$Warmup = 3,          # 예열 회차 (결과 버림)
  [int]$Iter = 50,           # 회차당 요청 수 (읽기·쓰기 공통)
  [int]$WarmIter = 20,       # 예열 회차의 요청 수
  [int]$Vus = 5,             # 동시 사용자 수
  [string[]]$Only = @(),     # 일부 API 만 (비우면 전부)
  [switch]$SkipLoad,         # 데이터를 다시 만들지 않음 (읽기만 다시 잴 때)
  [switch]$NoPng,            # Grafana PNG 저장 건너뜀
  [string]$PngEps = 'stocks,history,outbound_detail,carry,alloc',   # PNG 를 뽑을 API ('all' 이면 전부)
  [string]$PngPanels = '2,3,9,10,11',   # 처리량, p95, API별 p95, JVM 힙, GC ('' 이면 전부)
  [string]$Database = 'wms_db',
  [string]$Sample = ''
)

$ErrorActionPreference = 'Stop'
$dbPwd = $env:WMS_DB_PWD
if (-not $dbPwd) { throw 'Set environment variable WMS_DB_PWD first' }

$speedDir = $PSScriptRoot
$perf = Split-Path $speedDir -Parent
$outName = "result-speed-$Scale"
$out = Join-Path $perf $outName
if (-not (Test-Path $out)) { New-Item -ItemType Directory -Path $out | Out-Null }
if ($Sample -eq '') {
  $Sample = Join-Path $perf '..\..\wms-core\src\main\resources\db\sample\sample2.sql'
}

$reads  = @('stocks', 'history', 'inbounds', 'inbound_detail', 'inspections', 'locations', 'recommend',
            'outbounds', 'outbound_detail', 'preview', 'picking')
$writes = @('carry', 'alloc', 'ship')
$eps = $reads + $writes
if ($Only.Count -gt 0) { $eps = $eps | Where-Object { $Only -contains $_ } }

# 쓰기 대상 수: 회차마다 겹치지 않게 (예열 + 본 측정) x 요청 수 + 여유 10
$T = ($Warmup * $WarmIter) + ($Runs * $Iter) + 10

function Invoke-SqlFile([string]$path) {
  docker cp $path wms-db-1:/tmp/perf_in.sql | Out-Null
  docker exec -e MYSQL_PWD=$dbPwd wms-db-1 sh -c "mysql -uroot --default-character-set=utf8mb4 $Database < /tmp/perf_in.sql"
}

# 1. 데이터 만들기: sample2 (초기화) → 대량 데이터
if (-not $SkipLoad) {
  Write-Host "== generate data N=$Scale T=$T"
  Invoke-SqlFile $Sample
  $gen = Get-Content (Join-Path $speedDir 'gen_data.sql') -Raw -Encoding UTF8
  $tmp = Join-Path $env:TEMP 'perf_gen.sql'
  # BOM 없는 UTF-8 로 저장 (BOM 이 있으면 mysql 이 첫 줄을 못 읽음)
  [System.IO.File]::WriteAllText($tmp, "SET @N = $Scale; SET @T = $T;`n" + $gen, (New-Object System.Text.UTF8Encoding $false))
  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  $counts = Invoke-SqlFile $tmp
  $sw.Stop()
  $counts | Out-File -Encoding utf8 (Join-Path $out 'data.txt')
  "generate $([int]$sw.Elapsed.TotalSeconds)s / Warmup $Warmup x $WarmIter / Runs $Runs x $Iter / VUS $Vus" |
    Out-File -Append -Encoding utf8 (Join-Path $out 'data.txt')
  $counts
}

# 2. API 별 측정
$writeUsed = @{ carry = 0; alloc = 0; ship = 0 }
foreach ($ep in $eps) {
  foreach ($r in ((1 - $Warmup)..$Runs)) {
    $n = if ($r -le 0) { $WarmIter } else { $Iter }
    $offset = 0
    if ($writes -contains $ep) { $offset = $writeUsed[$ep]; $writeUsed[$ep] += $n }
    docker run --rm -i -v "${perf}:/scripts" `
      -e EP=$ep -e RUN=$r -e ITER=$n -e VUS=$Vus -e OFFSET=$offset -e T=$T -e TAG="speed$Scale" `
      -e BASE=http://host.docker.internal:8080 -e OUT="/scripts/$outName" `
      -e K6_PROMETHEUS_RW_SERVER_URL=http://host.docker.internal:9090/api/v1/write `
      grafana/k6 run --quiet -o experimental-prometheus-rw /scripts/speed/speed.js
  }
}

# 3. 예열 결과 지우기 (회차 0 이하)
Get-ChildItem $out -Filter '*.json' | Where-Object { $_.BaseName -match '-(-?\d+)$' -and [int]$Matches[1] -le 0 } | Remove-Item

# 4. API 별 Grafana PNG (본 측정 회차 구간). -NoPng 로 건너뜀
if (-not $NoPng) {
  $pngList = if ($PngEps -eq 'all') { $eps } else { $eps | Where-Object { ($PngEps -split ',') -contains $_ } }
  foreach ($ep in $pngList) {
    if ($PngPanels -eq '') {
      powershell -ExecutionPolicy Bypass -File (Join-Path $perf 'grafana-png.ps1') -ResultDir $out -S $ep
    } else {
      powershell -ExecutionPolicy Bypass -File (Join-Path $perf 'grafana-png.ps1') -ResultDir $out -S $ep -Panels $PngPanels
    }
  }
}
Write-Host "== done: $out"
