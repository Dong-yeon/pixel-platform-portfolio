<#
.SYNOPSIS
  skills/ (SSOT) -> 각 프로젝트의 .claude/skills/ 로 배포한다.

.DESCRIPTION
  이 repo의 skills/ 가 유일한 원본이다. 각 프로젝트 폴더의 .claude/skills/ 는
  전부 이 스크립트가 만들어내는 사본이며, 직접 편집하지 않는다.
  (편집은 skills/ 에서 하고 이 스크립트를 다시 돌린다)

  매핑은 scripts/skill-targets.tsv 하나만 본다 (bash 판과 공유).
  예전엔 이 파일에 $Targets 를 하드코딩했는데, TSV 와 조용히 어긋나
  PowerShell 로 돌릴 때만 일부 skill 이 배포되지 않았다. 매핑을 두 벌 두지 않는다.

  happyeon 루트는 이 repo 위치에서 역산하므로(..\..) 경로를 하드코딩하지 않는다.

.EXAMPLE
  .\scripts\sync-skills.ps1 -Check     # 차이만 보고, 쓰지 않음
  .\scripts\sync-skills.ps1            # 실제 배포
#>
param(
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$RepoRoot     = Split-Path -Parent $PSScriptRoot
$SkillsRoot   = Join-Path $RepoRoot 'skills'
$MapFile      = Join-Path $PSScriptRoot 'skill-targets.tsv'
# .../99.Happyeon/pixel-platform -> .../  (bash 판과 동일한 역산)
$HappyeonRoot = (Resolve-Path (Join-Path $RepoRoot '..\..')).Path

if (-not (Test-Path $MapFile)) {
    Write-Host "[FAIL] 매핑 파일 없음: $MapFile" -ForegroundColor Red
    exit 1
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

$changed = 0; $ok = 0; $skipped = 0; $missing = 0

# TSV 를 한 줄씩 흘려 처리한다(bash 판과 동일). 형식: <99.Happyeon 상위 기준 상대경로>\t<skill,쉼표구분>
foreach ($line in (Get-Content -LiteralPath $MapFile -Encoding UTF8)) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }

    $parts = $trimmed -split "`t", 2
    if ($parts.Count -lt 2) {
        Write-Host "[WARN] 탭 구분이 아님 — 건너뜀: $trimmed" -ForegroundColor DarkYellow
        continue
    }

    $rel   = $parts[0].Trim()
    $proj  = Join-Path $HappyeonRoot $rel
    $names = $parts[1].Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }

    if (-not (Test-Path $proj)) {
        Write-Host "[SKIP] $rel  (폴더 없음)" -ForegroundColor DarkYellow
        $skipped++
        continue
    }

    foreach ($name in $names) {
        $src = Join-Path $SkillsRoot $name
        if (-not (Test-Path $src)) {
            Write-Host "[MISS] skills\$name  원본 없음 — 확인 필요" -ForegroundColor Red
            $missing++
            continue
        }
        $dst = Join-Path $proj ".claude\skills\$name"

        if ((Get-DirHash $src) -eq (Get-DirHash $dst)) {
            $ok++
            continue
        }

        $changed++
        if (Test-Path $dst) { $state = 'DIFF' } else { $state = 'NEW ' }
        Write-Host "[$state] $name  ->  $rel" -ForegroundColor Cyan
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
    if ($changed -eq 0 -and $missing -eq 0) {
        Write-Host "동기화 상태 정상 — 일치 $ok, 스킵 $skipped" -ForegroundColor Green
    } else {
        Write-Host "차이 $changed 건, 원본누락 $missing 건 (쓰지 않음). 반영하려면 -Check 없이 실행." -ForegroundColor Yellow
        exit 1
    }
} else {
    Write-Host "배포 완료 — 갱신 $changed, 이미일치 $ok, 스킵 $skipped, 원본누락 $missing" -ForegroundColor Green
    if ($missing -gt 0) { exit 1 }
}
exit 0
