# pixel-platform

PixelFactory(OEE) · PixelFleet(AMR) · PixelWMS(창고) · PixelQMS(품질)를
게이트웨이 + 통합 대시보드 아래 묶은 **모노레포**. 기술적으로 가장 깊은 모듈은 PixelFleet
(레인 그래프 경로계산·구간 점유·Redis 실시간 상태)이고, PixelFactory가 코드 규모상 가장 크다.
포트: gateway **9000** · factory 9001 · fleet 9002 · wms 9003 · qms 9004 · dashboard(dev) 9200.

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

원본 작업 환경에는 Claude Code skill을 여러 프로젝트에 동기화하는 `skills/` SSOT + 배포
스크립트가 있었다. 공개용으로 정리하며 고객사 도메인 정보가 담긴 skill 디렉터리와 그 배포
매핑(`scripts/skill-targets.tsv`)은 히스토리에서 제거했다. `scripts/sync-skills.*`는 SSOT
동기화 방식을 보여주기 위해 남겨뒀다(매핑 파일이 없어 실행해도 아무 일도 하지 않는다).
