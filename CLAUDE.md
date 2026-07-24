# PixelFleet

자율주행 물류로봇(AMR) **군집 관제 시스템(FMS)** 포트폴리오.
관제 서버가 운송 작업을 받아 우선순위·로봇 상태·배터리를 근거로 로봇에 배정하고,
로봇(ROS 2 / 시뮬레이터)은 MQTT로 상태를 보고하며, 웹 관제 화면이 이를 실시간으로
보여주는 **이벤트 기반 컴포저블 구조**를 목표로 한다.

> 자매 프로젝트 **PixelFactory**(가공 라인 OEE 모니터링)와 도메인은 다르지만,
> "이벤트 단일 진실 공급원 + MQTT 수집 + 실시간 push" 아키텍처 원칙과 공통 코어
> (common/auth/user/mqtt)를 공유한다.

## 목표 아키텍처

```
[React 관제 화면] ──REST(조회/명령)──▶ [control-service (Spring Boot FMS)]
  공장 지도            ◀─WebSocket(실시간 push)─┘   │
  로봇 위치·배터리                                    │ MQTT
  작업 진행·장애 로그                                 ▼
                                         [Mosquitto broker]
                                              │
                                    [ROS 2 브릿지 노드] ← 실물 없으면 robot-sim
                                    MQTT ↔ DDS
                                              │
                                    [Nav2 / SLAM / 상태보고]
```

| 디렉터리 | 역할 |
|---|---|
| `services/control-service/` | Spring Boot 3 관제 서버 — 작업 수집·배정·상태머신, 이벤트 영속화, REST/WebSocket API |
| `robot-sim/` | 가짜 로봇 시뮬레이터 — 위치/상태/배터리/작업 이벤트를 MQTT로 발행 (ROS 2 연동 전 단계) |
| `web/` | 실시간 관제 대시보드 (공장 지도, 로봇 마커, 작업/장애 로그) |
| `infra/` | docker-compose (PostgreSQL, Mosquitto) |
| `docs/` | MQTT 토픽 계약, 백로그 |

## 절대 원칙

1. **이벤트가 단일 진실 공급원.** 모든 상태 변화(로봇 텔레메트리, 작업 전이)는
   `fleet_events`로 기록되고, 로봇/작업의 현재 상태는 이벤트에서 파생된다.
   이벤트를 우회한 상태 변경 금지.
2. **컴포저블.** control-service / robot-sim(ROS 2) / web 은 MQTT·REST 계약으로만
   통신한다(`docs/mqtt-topics.md`). 서비스 간 코드/DB 직접 참조 금지.
3. **관제 서버가 몸통.** 포트폴리오의 깊이는 작업 할당 정책·상태머신·장애 재시도 등
   백엔드 오케스트레이션에 둔다. ROS 2 는 "연동되는 수준"으로 유지한다.
4. **스키마는 마이그레이션으로.** dev/운영 스키마 변경은 Flyway 로만. `ddl-auto: validate`.

## 공통 코어 vs 산업 특화

- **공통 코어** (도메인 무관, PixelFactory 와 공유하는 골격):
  `common`(응답·예외·BaseEntity·설정), auth/JWT, user,
  이벤트 저장·조회 골격, MQTT 수집 파이프라인, (Phase 2) WebSocket push.
- **산업 특화** (AMR 물류 도메인):
  Robot 상태, TransportTask 상태머신, 작업 할당 정책, FleetEvent 타입, 관제 화면.
- 새 코드는 이 경계를 기준으로 위치를 정한다. 공통 코어에 로봇/작업 개념이 스며들면 안 된다.

## 로드맵

- **Phase 0** — 리포 셋업, 공통 코어 이식, 도메인 골격(robot/task/event), MQTT 계약 ✅
- **Phase 1** — robot-sim 구현(상태·위치·배터리·작업 텔레메트리 발행 + GOTO 수행),
  downlink 명령 토픽(`fleet/{code}/command`) ✅ (현재). 남은 것: 배차 스케줄러(주기 실행),
  작업 할당 정책 고도화(최근접·배터리 가중)
- **Phase 2** — WebSocket/SSE 실시간 push (로봇 위치/이벤트를 대시보드로)
- **Phase 3** — web 관제 대시보드(지도·로봇 마커·작업 조작), Railway 배포
- **Phase 4** — robot-sim 을 ROS 2(Gazebo + TurtleBot3 + Nav2) 브릿지로 교체, 멀티로봇 교통정리

## 개발 환경

- Java 17 (Gradle toolchain), Spring Boot 3.3, PostgreSQL 16, Gradle wrapper 사용
- 로컬: `infra/docker-compose.yml`로 Postgres + Mosquitto 기동 후
  `services/control-service`에서 `.\gradlew.bat bootRun` (포트 8082)
- 데모 계정: `admin` / `dispatcher` / `operator`, 비밀번호 `password`
- `fleet_events`는 계속 쌓이는 테이블 — 시뮬레이터 발행 주기와 보존 정책을 함께 고려한다.

## 작업 규칙

- 파일 삭제/대규모 이동은 사전에 계획을 제시하고 승인 후 진행한다.
- 범위 밖 기능 요청은 먼저 `docs/BACKLOG.md`에 기록한다.
