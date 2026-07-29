# pixel-platform

PixelFactory(OEE) + PixelFleet(AMR)을 게이트웨이 + 통합 대시보드 아래 묶은 **모노레포**.
포트: gateway **9000** · factory 9001 · fleet 9002 · dashboard(dev) 9200.

로드맵·단계별 계획: `docs/` 재구성 계획서.

## 절대 원칙

1. **모노레포 ≠ 모놀리스** — 모듈은 자체 빌드·DB·포트로 독립 배포 가능. 루트 통합 Gradle 빌드 없음.
2. **컴포저블** — 모듈 간 직접 코드/DB 참조 금지. 게이트웨이·REST·MQTT 계약으로만 통신.
3. **DB per module** — Postgres 인스턴스 공유, DB 분리.
4. **게이트웨이가 단일 진입점** — 외부·대시보드는 9000만.

## 스택

Gradle · Java 17 · Spring Boot 3.3.5 · Spring Cloud Gateway · JPA/QueryDSL · Flyway ·
PostgreSQL · Redis(fleet) · Paho MQTT / Mosquitto · React.

**MES 웹 스택이 아니다.** RealGrid / MyBatis / JSP / SQL Server(T-SQL) 규칙을 적용하지 않는다.

## Skill

`skills/` 가 모든 Claude skill의 **단일 원본(SSOT)** 이다. 각 프로젝트의 `.claude/skills/` 는 전부 사본.

- 편집은 `skills/` 에서만. 프로젝트 쪽 사본을 직접 고치면 다음 배포 때 덮어써진다.
- 배포: `./scripts/sync-skills.sh` (Linux/git-bash) 또는 `.\scripts\sync-skills.ps1` (PowerShell)
- 확인만: 위 명령에 `--check` / `-Check`
- 매핑은 `scripts/skill-targets.tsv` 한 곳에서 관리. 프로젝트가 늘면 여기에 한 줄 추가.

이 repo 작업 시 자동 로드되는 것은 `pixel-platform` · `pixelfleet` · `pixelfactory` 3개다.
`skills/` 의 고객사 MES skill들은 **보관용**이며 이 repo 작업에 적용하지 않는다.

## 주의

이 repo는 **private 전제**다. `skills/` 에 고객사 도메인 정보(테이블명·업무규칙·채번 포맷)가 있어,
공개 전환 시 해당 디렉터리 분리가 선행돼야 한다. 파일 삭제만으로는 git 히스토리에 남는다.
