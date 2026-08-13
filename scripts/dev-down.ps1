<#
.SYNOPSIS
  pixel-platform 로컬 스택을 내린다.

.DESCRIPTION
  PID 파일을 두지 않는다 — 명령줄에 이 저장소의 실제 경로가 박혀 있으므로 그걸로 고른다.
  스크립트로 띄우지 않은 프로세스(직접 실행한 bootRun 등)도 같이 정리되고,
  다른 프로젝트의 JVM은 건드리지 않는다.

  **저장소 폴더명을 하드코딩하지 않는다.** 예전엔 명령줄에 리터럴 문자열 "pixel-platform"이
  있는지로 골랐는데, 클론 경로가 그 이름을 안 쓰면(포크·이름 변경·`D:\ppub_clean` 같은 임시
  클론) 전부 놓친다 — 실제로 겪었다(dev-up.ps1으로 띄운 8개 프로세스를 dev-down.ps1이
  "실행 중인 서비스 없음"으로 보고). 대신 스크립트가 서 있는 위치에서 역산한 `$Root`
  절대경로로 매칭한다 — 어디에 클론해도 항상 맞다.

  **Gradle 데몬은 죽이지 않고 정식으로 내린다.** 강제 종료하면 그 데몬이 붙들고 있던
  bootRun 자식들이 함께 죽는다(실제로 스택이 통째로 내려갔다).

.PARAMETER KeepDocker
  Postgres·Redis·Mosquitto는 그대로 둔다. DB만 들여다볼 때.

.PARAMETER KeepGradle
  Gradle 데몬을 남긴다. 곧 다시 빌드할 때(재기동 몇 초 아낀다).

.EXAMPLE
  .\scripts\dev-down.ps1
  .\scripts\dev-down.ps1 -KeepDocker
#>
param(
    [switch]$KeepDocker,
    [switch]$KeepGradle
)

# Continue여야 한다 — PowerShell 5.1은 네이티브 exe의 stderr를 오류로 감싸므로,
# Stop이면 gradle·docker가 한 줄만 뱉어도 정리 도중에 스크립트가 죽는다.
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot

$stopped = @()
$rootPattern = [regex]::Escape($Root)
Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='node.exe'" |
    Where-Object { $_.CommandLine -match $rootPattern -and $_.CommandLine -notmatch 'GradleDaemon' } |
    ForEach-Object {
        $cl = $_.CommandLine
        $name = if ($cl -match 'control-service') { 'fleet' }
            elseif ($cl -match 'robot-sim') { 'robot-sim' }
            elseif ($cl -match 'oee-service') { 'factory' }
            elseif ($cl -match 'pixel-factory[\\/]simulator') { 'factory-sim' }
            elseif ($cl -match 'wms-service') { 'wms' }
            elseif ($cl -match 'qms-service') { 'qms' }
            elseif ($cl -match 'gateway') { 'gateway' }
            elseif ($cl -match 'vite|dashboard') { 'dashboard' }
            else { 'etc' }
        $stopped += $name
        Stop-Process -Id $_.ProcessId -Force -EA SilentlyContinue
    }

if ($stopped.Count -gt 0) {
    Write-Host "정지: $(($stopped | Sort-Object -Unique) -join ', ')" -ForegroundColor Green
} else {
    Write-Host "실행 중인 서비스 없음" -ForegroundColor DarkGray
}

if (-not $KeepGradle) {
    Push-Location (Join-Path $Root 'modules/pixel-fleet/services/control-service')
    $out = & .\gradlew.bat --stop --console=plain 2>&1
    Pop-Location
    ($out | Select-String -Pattern 'Daemon') | Select-Object -First 2 | ForEach-Object { "Gradle: $_" }
}

if (-not $KeepDocker) {
    docker stop pixel-postgres pixel-redis pixel-mosquitto *> $null
    Write-Host "컨테이너 정지 (데이터는 볼륨에 남는다)" -ForegroundColor Green
}
