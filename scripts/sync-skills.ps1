<#
.SYNOPSIS
  skills/ (SSOT) -> 각 프로젝트의 .claude/skills/ 로 배포한다.

.DESCRIPTION
  이 repo의 skills/ 가 유일한 원본이다. 각 프로젝트 폴더의 .claude/skills/ 는
  전부 이 스크립트가 만들어내는 사본이며, 직접 편집하지 않는다.
  (편집은 skills/ 에서 하고 이 스크립트를 다시 돌린다)

.EXAMPLE
  .\scripts\sync-skills.ps1 -Check     # 차이만 보고, 쓰지 않음
  .\scripts\sync-skills.ps1            # 실제 배포
#>
param(
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$RepoRoot   = Split-Path -Parent $PSScriptRoot
$SkillsRoot = Join-Path $RepoRoot 'skills'

# 프로젝트 경로 -> 배포할 skill 목록
$Targets = [ordered]@{
    'D:\happyeon\06.[CUSTOMER]\customer_mes'        = @('mes-analysis','customer-mes')
    'D:\happyeon\04.[CUSTOMER]\customer'           = @('mes-analysis','customer-mes')
    'D:\happyeon\02.[CUSTOMER]\customer'            = @('mes-analysis','customer-mes')
    'D:\happyeon\07.[CUSTOMER]\[CUSTOMER]'               = @('mes-analysis','customer-mes')
    'D:\happyeon\05.[CUSTOMER]\customer_erp'              = @('mes-analysis','customer-mes')
    'D:\happyeon\01.[CUSTOMER]\customer_boot_v1'       = @('customer-mes')
    'D:\happyeon\03.[CUSTOMER]\customer_web'         = @('customer-web')
    'D:\happyeon\03.[CUSTOMER]\customer_inout_final' = @('customer-inout-pda')
    'D:\happyeon\99.Happyeon\PixelFleet'       = @('pixelfleet')
    'D:\happyeon\99.Happyeon\PixelFactory'     = @('pixelfactory')
    # 이 repo 자신 — 플랫폼 작업 세션에서 자동 로드되는 것만
    'D:\happyeon\99.Happyeon\pixel-platform'   = @('pixelfleet','pixelfactory')
}

function Get-DirHash([string]$Path) {
    if (-not (Test-Path $Path)) { return $null }
    $parts = Get-ChildItem -Path $Path -Recurse -File | Sort-Object FullName | ForEach-Object {
        $rel = $_.FullName.Substring($Path.Length).TrimStart('\')
        "$rel|" + (Get-FileHash $_.FullName -Algorithm MD5).Hash
    }
    if (-not $parts) { return '' }
    $joined = [string]::Join("`n", $parts)
    $bytes  = [System.Text.Encoding]::UTF8.GetBytes($joined)
    $md5    = [System.Security.Cryptography.MD5]::Create()
    return [BitConverter]::ToString($md5.ComputeHash($bytes)).Replace('-','')
}

$changed = 0; $ok = 0; $skipped = 0

foreach ($proj in $Targets.Keys) {
    if (-not (Test-Path $proj)) {
        Write-Host "[SKIP] $proj  (폴더 없음)" -ForegroundColor DarkYellow
        $skipped++
        continue
    }
    foreach ($name in $Targets[$proj]) {
        $src = Join-Path $SkillsRoot $name
        if (-not (Test-Path $src)) {
            Write-Host "[MISS] skills\$name  원본 없음 — 확인 필요" -ForegroundColor Red
            continue
        }
        $dst = Join-Path $proj ".claude\skills\$name"

        if ((Get-DirHash $src) -eq (Get-DirHash $dst)) {
            $ok++
            continue
        }

        $changed++
        $state = if (Test-Path $dst) { 'DIFF' } else { 'NEW ' }
        Write-Host "[$state] $name  ->  $proj" -ForegroundColor Cyan
        if (-not $Check) {
            $parent = Split-Path -Parent $dst
            if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
            if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
            Copy-Item $src $dst -Recurse -Force
        }
    }
}

Write-Host ""
if ($Check) {
    if ($changed -eq 0) {
        Write-Host "동기화 상태 정상 — 일치 $ok, 스킵 $skipped" -ForegroundColor Green
    } else {
        Write-Host "차이 $changed 건 (쓰지 않음). 반영하려면 -Check 없이 실행." -ForegroundColor Yellow
        exit 1
    }
} else {
    Write-Host "배포 완료 — 갱신 $changed, 이미일치 $ok, 스킵 $skipped" -ForegroundColor Green
}
