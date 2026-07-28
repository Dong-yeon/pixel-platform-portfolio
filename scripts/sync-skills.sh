#!/usr/bin/env bash
# skills/ (SSOT) -> 각 프로젝트 .claude/skills/ 배포
#
#   ./scripts/sync-skills.sh --check   차이만 확인 (쓰지 않음, 차이 있으면 exit 1)
#   ./scripts/sync-skills.sh           배포
#
# 매핑은 scripts/skill-targets.tsv 하나만 본다 (PowerShell 판과 공유).
# happyeon 루트는 이 repo 위치에서 역산하므로 Cowork 샌드박스/git-bash 양쪽에서 동작한다.
set -uo pipefail

CHECK=0
[ "${1:-}" = "--check" ] && CHECK=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
SKILLS_ROOT="$REPO_ROOT/skills"
HAPPYEON_ROOT="$(cd "$REPO_ROOT/../.." && pwd)"   # .../99.Happyeon/pixel-platform -> .../
MAP="$SCRIPT_DIR/skill-targets.tsv"

dirhash() {
  [ -d "$1" ] || { echo "MISSING"; return; }
  (cd "$1" && find . -type f | sort | xargs md5sum 2>/dev/null | md5sum | cut -d' ' -f1)
}

changed=0; same=0; skipped=0; missing=0

while IFS=$'\t' read -r rel names; do
  case "${rel:-}" in ''|\#*) continue ;; esac
  proj="$HAPPYEON_ROOT/$rel"
  if [ ! -d "$proj" ]; then
    echo "[SKIP] $rel  (폴더 없음)"; skipped=$((skipped+1)); continue
  fi
  IFS=',' read -ra arr <<< "$names"
  for name in "${arr[@]}"; do
    src="$SKILLS_ROOT/$name"
    if [ ! -d "$src" ]; then
      echo "[MISS] skills/$name 원본 없음 — 확인 필요"; missing=$((missing+1)); continue
    fi
    dst="$proj/.claude/skills/$name"
    if [ "$(dirhash "$src")" = "$(dirhash "$dst")" ]; then
      same=$((same+1)); continue
    fi
    changed=$((changed+1))
    state=$([ -d "$dst" ] && echo "DIFF" || echo "NEW ")
    echo "[$state] $name  ->  $rel"
    if [ "$CHECK" -eq 0 ]; then
      mkdir -p "$dst"
      # rm 이 막힌 환경(샌드박스 등)에서 rm 실패 후 cp -r 이 dst 안에 한 겹 더 복사돼
      # <name>/<name>/ 중첩 디렉터리를 만든다. rm 은 실패 허용, 복사는 -T 로 "내용만" 덮어쓴다.
      rm -rf "$dst" 2>/dev/null || true
      mkdir -p "$dst"
      cp -rT "$src" "$dst"
    fi
  done
done < "$MAP"

echo
if [ "$CHECK" -eq 1 ]; then
  if [ "$changed" -eq 0 ] && [ "$missing" -eq 0 ]; then
    echo "동기화 정상 — 일치 $same, 스킵 $skipped"
  else
    echo "차이 $changed 건, 원본누락 $missing 건 (쓰지 않음)"; exit 1
  fi
else
  echo "배포 완료 — 갱신 $changed, 이미일치 $same, 스킵 $skipped, 원본누락 $missing"
  [ "$missing" -gt 0 ] && exit 1
fi
exit 0
