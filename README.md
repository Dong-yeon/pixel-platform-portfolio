# Pixel Platform

Happyeon SmartFactory Pixel Project — 여러 제조 도메인 모듈을 **API 게이트웨이 + 통합 대시보드**
아래 묶어 하나의 화면에서 관제하는 스마트팩토리 통합 플랫폼.

```
                  ┌─────────────────────────┐
                  │  Dashboard              │
                  │  OEE / AMR / KPI        │
                  └───────────┬─────────────┘
                              │
                  ┌───────────┴─────────────┐
                  │  API Gateway (9000)     │
                  └───────────┬─────────────┘
                  ┌───────────┴─────────────┐
                  │                         │
          ┌───────┴────────┐       ┌────────┴───────┐
          │ PixelFactory   │       │ PixelFleet     │
          │ OEE·설비·생산   │       │ AMR·작업지시    │
          │ 불량·PLC       │       │ 배터리·경로     │
          │ :9001          │       │ :9002          │
          └────────────────┘       └────────────────┘
```

## 구성

| 경로 | 역할 | 상태 |
|---|---|---|
| `platform/gateway/` | Spring Cloud Gateway — 라우팅·인증 집약 | 예정 (P3) |
| `platform/dashboard/` | 통합 React 대시보드 (OEE + AMR + KPI) | 예정 (P4) |
| `modules/pixel-factory/` | 가공라인 **OEE 모니터링** (MQTT·이벤트 소싱) | 이식 완료 |
| `modules/pixel-fleet/` | AMR **군집 관제(FMS)** (MQTT·Redis·WebSocket) | 이식 완료 |
| `shared/` | 공통 코어(common·auth·user) — 모듈 공유 | 예정 (P5) |
| `infra/` | docker-compose (PostgreSQL, Mosquitto, Redis) | 완료 |
| `docs/` | 플랫폼 문서·재구성 계획서 | — |

향후 확장: PixelVision(비전 검사), PixelQuality(품질), PixelEnergy(에너지), PixelAI(이상감지).

## 포트 규약

플랫폼은 **9000번대**를 쓴다 (다른 로컬 프로젝트와 충돌 방지).

| 포트 | 서비스 |
|---|---|
| 9000 | API Gateway |
| 9001 | pixel-factory |
| 9002 | pixel-fleet |
| 9100 | 통합 대시보드 (dev) |
| 5432 / 1883 / 6379 | Postgres / Mosquitto / Redis |

새 모듈은 9003(vision), 9004(quality) … 순으로 이어간다.

## 로컬 실행

```bash
cd infra && docker compose up -d          # Postgres + Mosquitto + Redis
```
```powershell
cd modules\pixel-factory\services\oee-service     ; .\gradlew.bat bootRun   # :9001
cd modules\pixel-fleet\services\control-service   ; .\gradlew.bat bootRun   # :9002
cd modules\pixel-fleet\robot-sim                  ; .\gradlew.bat bootRun
```

## 원칙

1. **모노레포 ≠ 모놀리스.** 각 모듈은 자체 빌드·DB·포트·배포 단위를 갖는 독립 서비스다.
2. **컴포저블.** 모듈 간 코드/DB 직접 참조 금지. 게이트웨이·REST·MQTT 계약으로만 통신한다.
3. **DB per module.** 모듈마다 own 스키마. 공유가 필요하면 계약(이벤트/REST)으로 푼다.
4. **게이트웨이가 단일 진입점.** 대시보드·외부는 게이트웨이만 바라본다.
5. **이벤트가 단일 진실 공급원.** 각 모듈의 상태 변화는 이벤트로 기록되고 지표는 거기서 파생된다.

## 모듈별 문서

각 모듈의 상세 아키텍처·로드맵은 모듈 안의 `CLAUDE.md`를 본다.

- [modules/pixel-factory/CLAUDE.md](modules/pixel-factory/CLAUDE.md) — OEE 모니터링
- [modules/pixel-fleet/CLAUDE.md](modules/pixel-fleet/CLAUDE.md) — AMR 군집 관제
- [docs/pixel-platform-plan.md](docs/pixel-platform-plan.md) — 플랫폼 재구성 계획서

## 개발 환경

- Java 17, Spring Boot 3.3, PostgreSQL 16, Redis 7, Mosquitto, Node 20+
- **빌드는 PowerShell `.\gradlew.bat`로.** bash `./gradlew`는 Windows에서 `-Xmx` 파싱이 깨진다.
- 모듈은 각자 Gradle wrapper로 독립 빌드한다(루트 통합 빌드 없음).
