# PixelFleet

자율주행 물류로봇(AMR) 군집 관제 시스템(FMS) 데모.
Spring Boot 관제 서버 + MQTT + (ROS 2 / 시뮬레이터) 로봇 + React 관제 화면.

전체 설계와 원칙은 [CLAUDE.md](CLAUDE.md), 통신 계약은 [docs/mqtt-topics.md](docs/mqtt-topics.md) 참고.

## 구성

| 디렉터리 | 내용 | 상태 |
|---|---|---|
| `services/control-service/` | Spring Boot 3 관제 서버 (REST + MQTT 수집) | 골격 구현 |
| `robot-sim/` | 가짜 로봇 시뮬레이터 (MQTT 발행) | 예정 (Phase 1) |
| `web/` | React 실시간 관제 대시보드 | 예정 (Phase 3) |
| `infra/` | docker-compose (PostgreSQL, Mosquitto) | 완료 |

## 로컬 실행

```bash
# 1) 인프라 기동 (Postgres + Mosquitto)
cd infra
docker compose up -d

# 2) 관제 서버 실행 (포트 8082)
cd ../services/control-service
./gradlew bootRun          # Windows: .\gradlew.bat bootRun
```

- Swagger UI: http://localhost:8082/swagger-ui.html
- Health: http://localhost:8082/api/health

## 인증

JWT 기반. 데모 계정(비밀번호 `password`): `admin`, `dispatcher`, `operator`.
`/api/auth/login` 과 `/api/health`, Swagger 를 제외한 모든 `/api/**` 는 토큰이 필요하다.

```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
```

## 주요 API (현재)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/robots` | 로봇 목록·현재 상태 |
| GET | `/api/tasks` | 운송 작업 목록 |
| POST | `/api/tasks` | 운송 작업 생성 |
| POST | `/api/tasks/dispatch` | 배차 1회 실행(대기 작업 → 가용 로봇) |
| GET | `/api/events` | 최근 fleet 이벤트 (`?taskId=` 로 작업별 조회) |

## 텔레메트리 테스트 (mosquitto_pub)

```bash
# 로봇 온라인
mosquitto_pub -t fleet/AMR-01/status   -m '{"status":"IDLE"}'
mosquitto_pub -t fleet/AMR-01/position -m '{"x":10.0,"y":5.0}'
mosquitto_pub -t fleet/AMR-01/battery  -m '{"percent":72}'
# 작업 생성(REST) 후 배차, 로봇이 작업 완료 보고
mosquitto_pub -t fleet/AMR-01/task     -m '{"taskCode":"T-1001","event":"completed"}'
```
