# 측정 구간의 Grafana 패널을 PNG 로 저장
# 사용법 (WMS 폴더에서):
#   자동 구간: 결과 폴더의 {S}-*.json 으로 구간을 정한다
#     - JSON 에 dur_ms(속도 측정)가 있으면: 가장 이른 (at - dur_ms) - Pad ~ 가장 늦은 at + Tail
#     - 없으면(동시성 측정): 가장 이른 at - 60초 ~ 가장 늦은 at + Tail
#     powershell -ExecutionPolicy Bypass -File scripts/perf/grafana-png.ps1 -ResultDir scripts/perf/result-S1-I-W1 -S carry
#     powershell -ExecutionPolicy Bypass -File scripts/perf/grafana-png.ps1 -ResultDir scripts/perf/result-speed-1000 -S stocks
#   직접 구간:
#     powershell -ExecutionPolicy Bypass -File scripts/perf/grafana-png.ps1 -Name W1-carry -From "2026-10-09 10:12:00" -To "2026-10-09 10:15:00"
# 저장: <ResultDir 또는 scripts/perf/result>/png/<Name>-<패널번호>-<패널이름>.png
param(
  [string]$ResultDir = '',
  [string]$S = '',
  [string]$Name = '',
  [string]$From = '',
  [string]$To = '',
  [int]$Pad = 10,
  [int]$Tail = 15,
  [string]$Grafana = 'http://localhost:3000',
  [int]$Width = 1200,
  [int]$Height = 450
)

# 키를 문자열로 둔다. [ordered] 해시에 정수로 접근하면 키가 아니라 순서(0부터)로 읽혀 이름이 한 칸씩 밀린다
$panels = [ordered]@{
  '1' = 'db-pool'; '2' = 'throughput'; '3' = 'p95'; '4' = '5xx'
  '5' = 'carry-queue'; '6' = 'db-acquire'; '7' = 'k6-status'; '8' = 'p99'
  '9' = 'api-p95'; '10' = 'jvm-heap'; '11' = 'gc-pause'
}

function ToMs([datetime]$t) { ([DateTimeOffset]$t).ToUnixTimeMilliseconds() }

if ($ResultDir -ne '') {
  if ($S -eq '') { throw '-ResultDir needs -S (carry, cross, stocks ...)' }
  $files = Get-ChildItem (Join-Path $ResultDir "$S-*.json")
  if ($files.Count -eq 0) { throw "no $S-*.json in $ResultDir" }
  $starts = @(); $ends = @()
  foreach ($f in $files) {
    $j = Get-Content $f -Raw | ConvertFrom-Json
    $end = ToMs ([datetime]$j.at)
    $ends += $end
    if ($j.dur_ms) { $starts += $end - [long]$j.dur_ms - ($Pad * 1000) } else { $starts += $end - 60000 }
  }
  $fromMs = ($starts | Measure-Object -Minimum).Minimum
  $toMs = ($ends | Measure-Object -Maximum).Maximum + ($Tail * 1000)
  if ($Name -eq '') { $Name = "$(Split-Path $ResultDir -Leaf)-$S" }
  $out = Join-Path $ResultDir 'png'
} else {
  if ($From -eq '' -or $To -eq '' -or $Name -eq '') { throw 'without -ResultDir, -Name -From -To are required' }
  $fromMs = ToMs (Get-Date $From)
  $toMs = ToMs (Get-Date $To)
  $out = Join-Path $PSScriptRoot 'result/png'
}
if (-not (Test-Path $out)) { New-Item -ItemType Directory -Path $out | Out-Null }

Write-Host "== $Name  $([DateTimeOffset]::FromUnixTimeMilliseconds($fromMs).LocalDateTime) ~ $([DateTimeOffset]::FromUnixTimeMilliseconds($toMs).LocalDateTime)"
foreach ($id in $panels.Keys) {
  $url = "$Grafana/render/d-solo/wms-measure/wms-measure?orgId=1&panelId=$id&from=$fromMs&to=$toMs" +
         "&width=$Width&height=$Height&tz=Asia%2FSeoul&var-DS=Prometheus"
  $file = Join-Path $out ("{0}-{1}-{2}.png" -f $Name, $id, $panels[$id])
  try {
    Invoke-WebRequest -Uri $url -OutFile $file -TimeoutSec 60 -UseBasicParsing
    Write-Host "  saved $file"
  } catch {
    Write-Host "  FAIL panel $id : $($_.Exception.Message)"
  }
}
