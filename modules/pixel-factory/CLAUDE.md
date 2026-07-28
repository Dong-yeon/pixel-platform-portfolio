# PixelFactory

자동차 부품 가공 라인의 **OEE(설비종합효율) 실시간 모니터링** 데모 시스템.
설비 시뮬레이터가 MQTT로 이벤트를 발행하고, 백엔드가 이를 수집·계산해서
웹 대시보드에 실시간으로 보여주는 **이벤트 기반 컴포저블 구조**를 목표로 한다.

## 목표 아키텍처

```
simulator ──MQTT──▶ [Mosquitto broker] ──▶ oee-service ──WebSocket/SSE──▶ web
(설비 텔레메트리)                            │  ├ 이벤트 영속화 (PostgreSQL)      (실시간 대시보드)
                                            │  ├ OEE 계산 (A × P × Q)
                                            │  └ REST API (작업지시/이벤트 조회)
                                            └─▶ ai-service (Phase 4, 이상 감지)
```

| 디렉터리 | 역할 |
|---|---|
| `services/oee-service/` | Spring Boot 3 백엔드 — 이벤트 수집·영속화, OEE 계산, REST/WebSocket API |
| `simulator/` | 가공 설비 시뮬레이터 — 사이클타임/상태/불량 이벤트를 MQTT로 발행 |
| `web/` | 실시간 OEE 대시보드 + 작업지시 조작 UI |
| `infra/` | docker-compose (PostgreSQL, Mosquitto), 배포 설정 |
| `docs/` | 백로그, 설계 문서 |

## 절대 원칙

1. **이벤트가 단일 진실 공급원.** 모든 상태 변화는 FactoryEvent로 기록되고,
   OEE 지표는 이벤트 스트림에서 계산한다. 이벤트를 우회한 상태 변경 금지.
2. **컴포저블.** simulator / oee-service / web / ai-service는 MQTT·REST 계약으로만
   통신한다. 서비스 간 코드/DB 직접 참조 금지.
3. **게임 메커닉 금지, 인터랙션 유지.** 점수·레벨·보상 같은 게임 시스템은 넣지 않는다.
   단, "조작하면 실시간으로 반응한다"(작업지시 시작/중단 → 대시보드 즉시 반영)는
   이 프로젝트의 핵심 경험이므로 반드시 유지한다.
4. **범위는 가공 라인 OEE.** 물류(AGV/창고), MRB/QMS 확장 프로세스는 v2 백로그
   (`docs/BACKLOG.md`)로 보내고 1차 범위에 넣지 않는다.
5. **스키마는 마이그레이션으로.** `ddl-auto: update` 의존은 로컬 한정.
   dev/운영 환경 스키마 변경은 Flyway 마이그레이션으로만 한다.

## 공통 코어 vs 산업 특화

- **공통 코어** (산업 무관, 다른 도메인에 재사용 가능):
  `common` 패키지(응답 포맷·예외·BaseEntity·설정), auth/JWT, user,
  FactoryEvent 저장·조회 골격, MQTT 수집 파이프라인, WebSocket push.
- **산업 특화** (자동차 부품 가공 도메인):
  WorkOrder 상태머신, 이벤트 타입 정의, OEE 계산식(가동률×성능×품질),
  설비/라인 마스터, 대시보드 화면 구성.
- 새 코드를 넣을 때 이 경계를 기준으로 패키지/서비스 위치를 정한다.
  공통 코어에 산업 특화 개념(품번, 로트, 설비 상태 등)이 스며들면 안 된다.

## 로드맵

- **Phase 0** — CLAUDE.md 작성, 모노레포 재구성, 게임 플레이버/범위 밖 코드 정리 ✅
- **Phase 1** — infra에 Mosquitto 추가, MQTT 토픽 설계(`docs/mqtt-topics.md`),
  simulator 최소 구현(설비 상태·사이클 발행), oee-service MQTT 컨슈머,
  설비/라인 마스터 엔티티, Flyway 도입, 실제 인증(User + PasswordEncoder)으로 Mock 교체 ✅
- **Phase 2** — OEE 계산 엔진(설비/라인/시프트 단위), WebSocket/SSE 실시간 push
- **Phase 3** — web 대시보드(실시간 OEE, 이벤트 타임라인, 작업지시 조작), Railway 배포
- **Phase 4** — ai-service (AI_ANOMALY_DETECTED 발행 주체)

## 개발 환경

- Java 17 (Gradle toolchain), Spring Boot 3.3, PostgreSQL 16, Gradle wrapper 사용
- 로컬: `infra/docker-compose.yml`로 Postgres(추후 Mosquitto 포함) 기동 후
  `services/oee-service`에서 `.\gradlew.bat bootRun` (포트 8081)
- 배포: Railway (앱 + DB + MQTT 통합, Phase 3에서)
- FactoryEvent는 계속 쌓이는 테이블 — 시뮬레이터 발행 주기와 이벤트 보존 정책을
  항상 함께 고려한다 (Railway 사용량 이슈).

## 작업 규칙

- 파일 삭제/대규모 이동은 사전에 계획을 제시하고 승인 후 진행한다.
- 범위 밖 기능 요청은 먼저 `docs/BACKLOG.md`에 기록한다.
