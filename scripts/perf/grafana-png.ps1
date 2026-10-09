# 측정 구간의 Grafana 패널 8개를 PNG 로 저장
# 사용법 (WMS 폴더에서):
#   자동 구간: 결과 폴더의 {S}-*.json 'at'(끝난 시각) 중 가장 이른 것 -60초 ~ 가장 늦은 것 +15초
#     powershell -ExecutionPolicy Bypass -File scripts/perf/grafana-png.ps1 -ResultDir scripts/perf/result-W1 -S carry
#   직접 구간:
#     powershell -ExecutionPolicy Bypass -File scripts/perf/grafana-png.ps1 -Name W1-carry -From "2026-10-09 10:12:00" -To "2026-10-09 10:15:00"
# 저장: <ResultDir 또는 scripts/perf/result>/png/<Name>-<패널번호>-<패널이름>.png
param(
  [string]$ResultDir = '',
  [string]$S = '',
  [string]$Name = '',
  [string]$From = '',
  [string]$To = '',
  [string]$Grafana = 'http://localhost:3000',
  [int]$Width = 1200,
  [int]$Height = 450
)

$panels = [ordered]@{
  1 = 'db-pool'; 2 = 'throughput'; 3 = 'p95'; 4 = '5xx'
  5 = 'carry-queue'; 6 = 'db-acquire'; 7 = 'k6-status'; 8 = 'p99'
}

function ToMs([datetime]$t) { ([DateTimeOffset]$t).ToUnixTimeMilliseconds() }

if ($ResultDir -ne '') {
  if ($S -eq '') { throw '-ResultDir 를 쓰면 -S (carry, cross ...) 도 필요' }
  $files = Get-ChildItem (Join-Path $ResultDir "$S-*.json")
  if ($files.Count -eq 0) { throw "$ResultDir 에 $S-*.json 이 없음" }
  $times = $files | ForEach-Object { [datetime](Get-Content $_ -Raw | ConvertFrom-Json).at }
  $fromMs = ToMs (($times | Sort-Object)[0].AddSeconds(-60))
  $toMs = ToMs (($times | Sort-Object)[-1].AddSeconds(15))
  if ($Name -eq '') { $Name = "$(Split-Path $ResultDir -Leaf)-$S" }
  $out = Join-Path $ResultDir 'png'
} else {
  if ($From -eq '' -or $To -eq '' -or $Name -eq '') { throw '-ResultDir 가 없으면 -Name -From -To 가 필요' }
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