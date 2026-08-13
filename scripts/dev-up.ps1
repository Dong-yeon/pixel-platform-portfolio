<#
.SYNOPSIS
  pixel-platform 로컬 스택을 필요한 만큼만 기동한다.

.DESCRIPTION
  전부 띄울 필요는 거의 없다. 무엇을 검증하느냐에 따라 셋 중 하나를 고른다.

    -Stack fleet   기본. docker + factory + robot-sim + fleet
                   → AMR 배차·주행·주문 엔진을 볼 때.
    -Stack e2e     위 + wms
                   → 출고지시 → 운송 → 재고 차감(층간 이송 포함)을 볼 때.
    -Stack full    위 + qms + factory-sim + gateway + 대시보드
                   → 화면을 볼 때.

  **factory는 어느 조합에서도 빠지지 않는다.** fleet이 평면도(노드 좌표와 **층**)를
  factory에서 받아오기 때문이다. 없으면 하드코딩 폴백으로 도는데 그 폴백에는 층 정보가
  없어 모든 노드를 1층으로 본다 — 엘리베이터 분할이 한 번도 일어나지 않으면서
  겉으로는 정상처럼 보인다. 그래서 이 스크립트는 factory가 뜬 것을 확인한 뒤에 fleet을 띄운다.

  **데모 작업 생성기는 기본으로 꺼져 있다.** 8초마다 주문을 만들어 fleet_orders와
  fleet_events가 끝없이 자란다(보존 정책이 없다). 흐르는 화면이 필요할 때만 -Demo.

  **bootRun이 아니라 jar로 띄운다.** bootRun은 부모 Gradle JVM을 계속 붙들고 있어
  서비스마다 프로세스가 둘이 된다. 게다가 다른 모듈을 빌드하면서 shared/를 다시
  컴파일하면 실행 중인 서비스의 클래스패스가 어긋난다(실제로 WMS가 401을 뱉었다).

.PARAMETER Demo
  데모 운송 작업 생성기를 켠다(fleet). 안 켜면 작업은 사람이 만들 때만 생긴다.

.PARAMETER SkipBuild
  jar 빌드를 건너뛰고 기존 build/libs 의 것을 쓴다. 코드를 안 고쳤을 때.

.EXAMPLE
  .\scripts\dev-up.ps1                    # 엔진 검증용 최소 구성
  .\scripts\dev-up.ps1 -Stack e2e -Demo   # 출고 e2e + 배경 흐름
  .\scripts\dev-up.ps1 -Stack full -Demo  # 화면 데모
#>
param(
    [ValidateSet('fleet', 'e2e', 'full')]
    [string]$Stack = 'fleet',
    [switch]$Demo,
    [switch]$SkipBuild
)

# **Stop이 아니라 Continue다.** PowerShell 5.1은 네이티브 exe의 stderr를 ErrorRecord로
# 감싸는데(NativeCommandError), Stop이면 그 한 줄이 스크립트를 죽인다 — gradle이 경고를
# 한 줄 뱉거나 docker 데몬이 안 떠 있기만 해도 거기서 끝난다. 성패는 $LASTEXITCODE로 본다.
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
$LogDir = Join-Path $Root 'logs'
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

# 기동 순서가 곧 의존 순서다.
#  - factory가 fleet보다 먼저: 평면도(층 포함)를 못 받으면 fleet이 5분간 폴백으로 돈다.
#  - robot-sim이 fleet보다 먼저: 로봇이 MQTT를 구독하기 전에 배차되면 아무도 못 받고,
#    그 주문은 워치독 30초를 기다렸다 재큐된다(기동 직후에만 나던 잡음의 원인).
#
# kind: 'boot' = Spring Boot(bootJar → java -jar) / 'dist' = 순수 Java(installDist → java -cp).
# factory-sim만 Spring Boot가 아니라 application 플러그인 프로젝트여서 bootJar가 없다.
$SERVICES = @(
    @{ name = 'factory';     dir = 'modules/pixel-factory/services/oee-service';   port = 9001; kind = 'boot'; stacks = @('fleet', 'e2e', 'full') }
    @{ name = 'robot-sim';   dir = 'modules/pixel-fleet/robot-sim';                port = 0;    kind = 'boot'; stacks = @('fleet', 'e2e', 'full'); ready = 'Simulator started' }
    @{ name = 'fleet';       dir = 'modules/pixel-fleet/services/control-service'; port = 9002; kind = 'boot'; stacks = @('fleet', 'e2e', 'full') }
    @{ name = 'wms';         dir = 'modules/pixel-wms/services/wms-service';       port = 9003; kind = 'boot'; stacks = @('e2e', 'full') }
    @{ name = 'qms';         dir = 'modules/pixel-qms/services/qms-service';       port = 9004; kind = 'boot'; stacks = @('full') }
    @{ name = 'factory-sim'; dir = 'modules/pixel-factory/simulator';              port = 0;    kind = 'dist'; stacks = @('full')
       install = 'simulator'; mainClass = 'com.pixelfactory.simulator.FactorySimulator' }
    @{ name = 'gateway';     dir = 'platform/gateway';                             port = 9000; kind = 'boot'; stacks = @('full') }
)

# IPv4와 IPv6를 **둘 다** 찔러 본다. Vite는 [::1]에만 리스닝하는데 'localhost'로 주면
# .NET이 127.0.0.1만 시도하고 끝나서, 멀쩡히 뜬 대시보드를 "안 뜸"으로 봤다.
function Wait-Port([int]$Port, [int]$TimeoutSec = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        foreach ($addr in @('127.0.0.1', '::1')) {
            try {
                # 주소 패밀리를 맞춰 소켓을 만든다 — TcpClient 기본은 IPv4라
                # ::1에 그냥 붙이면 리스닝 중이어도 실패한다.
                $ip = [System.Net.IPAddress]::Parse($addr)
                $c = New-Object System.Net.Sockets.TcpClient($ip.AddressFamily)
                $c.Connect($ip, $Port)
                $c.Close()
                return $true
            } catch {
                # 다음 주소로
            }
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

function Wait-LogMarker([string]$Path, [string]$Marker, [int]$TimeoutSec = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $Path) {
            if (Select-String -Path $Path -SimpleMatch $Marker -Quiet) { return $true }
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

# ---- 1. 인프라 ----
Write-Host "[1/3] 인프라(Postgres·Redis·Mosquitto)" -ForegroundColor Cyan
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "  Docker Desktop 기동 중…"
    Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe' -WindowStyle Hidden
    $deadline = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $deadline) {
        docker info *> $null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 5
    }
}
docker start pixel-postgres pixel-redis pixel-mosquitto *> $null
if ($LASTEXITCODE -ne 0) {
    # 컨테이너가 아직 없으면 compose로 만든다.
    Push-Location (Join-Path $Root 'infra')
    docker compose up -d
    Pop-Location
}
$deadline = (Get-Date).AddSeconds(90)
while ((Get-Date) -lt $deadline) {
    docker exec pixel-postgres pg_isready -U pixel *> $null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 2
}
Write-Host "  준비됨" -ForegroundColor Green

# ---- 2. 빌드 ----
$targets = $SERVICES | Where-Object { $_.stacks -contains $Stack }
if ($SkipBuild) {
    Write-Host "[2/3] 빌드 건너뜀(-SkipBuild)" -ForegroundColor Cyan
} else {
    Write-Host "[2/3] jar 빌드 ($($targets.Count)개)" -ForegroundColor Cyan
    foreach ($svc in $targets) {
        Write-Host "  $($svc.name)…" -NoNewline
        $task = if ($svc.kind -eq 'dist') { 'installDist' } else { 'bootJar' }
        Push-Location (Join-Path $Root $svc.dir)
        $out = & .\gradlew.bat $task --console=plain 2>&1
        $code = $LASTEXITCODE
        Pop-Location
        if ($code -ne 0) {
            Write-Host " 실패" -ForegroundColor Red
            $out | Select-String -Pattern 'error|FAILURE' | Select-Object -First 10
            exit 1
        }
        Write-Host " ok" -ForegroundColor Green
    }
}

# ---- 3. 기동 ----
# 데모 생성기는 켤 때만 켠다. 자식 프로세스가 이 환경변수를 물려받는다.
$env:DEMO_TASK_GENERATOR_ENABLED = if ($Demo) { 'true' } else { 'false' }
Write-Host "[3/3] 기동 (stack=$Stack, 데모 생성기=$($env:DEMO_TASK_GENERATOR_ENABLED))" -ForegroundColor Cyan

foreach ($svc in $targets) {
    if ($svc.kind -eq 'dist') {
        $lib = Join-Path $Root "$($svc.dir)/build/install/$($svc.install)/lib"
        if (-not (Test-Path $lib)) {
            Write-Host "  $($svc.name): 산출물 없음 — -SkipBuild 없이 다시 실행할 것" -ForegroundColor Red
            exit 1
        }
        $javaArgs = @('-cp', (Join-Path $lib '*'), $svc.mainClass)
    } else {
        $libs = Join-Path $Root "$($svc.dir)/build/libs"
        $jar = Get-ChildItem $libs -Filter '*.jar' -EA SilentlyContinue |
            Where-Object { $_.Name -notlike '*-plain.jar' } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($null -eq $jar) {
            Write-Host "  $($svc.name): jar 없음 — -SkipBuild 없이 다시 실행할 것" -ForegroundColor Red
            exit 1
        }
        $javaArgs = @('-jar', $jar.FullName)
    }

    $log = Join-Path $LogDir "$($svc.name).log"
    if (Test-Path $log) { Remove-Item $log -Force }
    Start-Process -FilePath 'java' -ArgumentList $javaArgs `
        -WorkingDirectory (Join-Path $Root $svc.dir) `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -WindowStyle Hidden | Out-Null

    Write-Host "  $($svc.name)…" -NoNewline
    $ok = $true
    if ($svc.port -gt 0) {
        $ok = Wait-Port $svc.port
    } elseif ($svc.ready) {
        $ok = Wait-LogMarker $log $svc.ready
    } else {
        Start-Sleep -Seconds 3
    }
    if ($ok) {
        Write-Host " ok" -ForegroundColor Green
    } else {
        Write-Host " 안 뜸 — logs/$($svc.name).log 확인" -ForegroundColor Red
        exit 1
    }
}

if ($Stack -eq 'full') {
    $dashDir = Join-Path $Root 'platform/dashboard'
    # 최초 클론엔 node_modules가 없다 — 그대로 npm run dev를 돌리면 "'vite'은(는) ... 명령이
    # 아닙니다"로 조용히 실패한다(실제로 겪음). 있으면 install을 건너뛰어 재기동은 빠르게 유지.
    if (-not (Test-Path (Join-Path $dashDir 'node_modules'))) {
        Write-Host "  dashboard 의존성 설치…" -NoNewline
        Push-Location $dashDir
        npm install --no-fund --no-audit *> $null
        $code = $LASTEXITCODE
        Pop-Location
        if ($code -ne 0) {
            Write-Host " 실패 — npm install을 수동으로 확인할 것" -ForegroundColor Red
            exit 1
        }
        Write-Host " ok" -ForegroundColor Green
    }

    Write-Host "  dashboard…" -NoNewline
    $log = Join-Path $LogDir 'dashboard.log'
    Start-Process -FilePath 'npm.cmd' -ArgumentList 'run', 'dev' `
        -WorkingDirectory $dashDir `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -WindowStyle Hidden | Out-Null
    if (Wait-Port 9200) { Write-Host " ok  http://localhost:9200" -ForegroundColor Green }
    else { Write-Host " 안 뜸 — logs/dashboard.log 확인" -ForegroundColor Red }
}

Write-Host ""
Write-Host "로그: logs\*.log   내릴 때: .\scripts\dev-down.ps1" -ForegroundColor DarkGray
