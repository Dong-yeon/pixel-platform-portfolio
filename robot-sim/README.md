# robot-sim

가짜 로봇 시뮬레이터. ROS 2 하드웨어 연동(Phase 4) 전까지 로봇 역할을 대신한다.
`docs/mqtt-topics.md` 계약에 따라 MQTT 로 상태/위치/배터리/작업 텔레메트리를 발행하고,
관제 서버가 배차 시 보내는 `fleet/{code}/command`(GOTO)를 구독해 작업을 수행한다.

Spring Boot 앱(웹/DB 없음). 관제 서버와는 **MQTT 계약으로만** 연결된다(컴포저블).

## 동작

- 시작 시 각 가상 로봇을 홈(dock)에 배치하고 `IDLE` 상태로 온라인.
- 매 tick(기본 1초):
  - **MOVING** — 목표를 향해 `speed` 만큼 이동, 위치 발행, 배터리 소모.
    작업 수행 중이면 낮은 확률로 실패(→ `task failed`, 관제 서버 재시도 유발).
    목적지 도착 시 `task completed` 후 `IDLE`.
  - **IDLE** — 배터리가 임계치 미만이면 가장 가까운 dock 으로 이동해 **CHARGING**.
    아니면 `roam` 설정에 따라 임의 노드로 순찰(대시보드가 살아있게).
  - **CHARGING** — 충전 후 95% 이상이면 `IDLE`.
- 배터리는 정수 %가 바뀔 때만 발행(노이즈 억제), 위치는 이동 tick 마다 발행.

## 실행

```bash
# 브로커가 떠 있어야 한다 (infra/docker-compose.yml)
cd robot-sim
./gradlew bootRun          # Windows: .\gradlew.bat bootRun
```

## 전체 루프 데모

```bash
# 1) 로그인 → 토큰
TOKEN=$(curl -s -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' | jq -r '.data.accessToken')

# 2) 운송 작업 생성 (노드는 STATION-A/B/C, WAREHOUSE, DOCK-1/2 사용)
curl -s -X POST http://localhost:8082/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"taskCode":"T-1001","originNode":"STATION-A","destinationNode":"WAREHOUSE","priority":"HIGH"}'

# 3) 배차는 스케줄러가 2초마다 자동 실행(대기 작업 → 가용 로봇에 GOTO 발행).
#    즉시 돌리고 싶으면 수동 호출:
curl -s -X POST http://localhost:8082/api/tasks/dispatch -H "Authorization: Bearer $TOKEN"

# 4) 이벤트/로봇 상태 확인
curl -s http://localhost:8082/api/events -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8082/api/robots -H "Authorization: Bearer $TOKEN"
```

## 설정 (`application.yml`, `sim.*`)

| 키 | 기본값 | 설명 |
|---|---|---|
| `tick-interval-ms` | 1000 | tick 주기 |
| `speed` | 1.5 | tick 당 이동 거리(맵 단위) |
| `battery-drain-per-tick` | 0.4 | 이동 tick 당 배터리 소모(%) |
| `charge-per-tick` | 2.0 | 충전 tick 당 회복(%) |
| `low-battery-threshold` | 20 | 이 아래면 dock 복귀 |
| `failure-rate` | 0.02 | 이동 tick 당 작업 실패 확률 |
| `roam` | true | IDLE 로봇 순찰 여부 |
| `robots[]` | AMR-01~03 | 로봇 code/name/home |

로봇 code 는 관제 서버 시드(`V2__seed_robots.sql`)의 `AMR-01~03` 과 일치해야
텔레메트리가 반영된다.

## Phase 4

이 자리를 ROS 2 브릿지 노드(Gazebo + TurtleBot3 + Nav2)로 교체한다.
MQTT 계약이 동일하므로 관제 서버는 변경 없이 실물/시뮬 로봇을 받는다.
