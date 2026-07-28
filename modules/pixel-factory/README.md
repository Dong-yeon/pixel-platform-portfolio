# PixelFactory

자동차 부품 가공 라인 **OEE 실시간 모니터링** 데모.
이벤트 기반 컴포저블 구조 — 자세한 목표/원칙/로드맵은 [CLAUDE.md](CLAUDE.md) 참고.

## 구조

| 디렉터리 | 역할 | 상태 |
|---|---|---|
| `services/oee-service/` | Spring Boot 3 백엔드 (MQTT 수집·이벤트 영속화·API) | 개발 중 |
| `simulator/` | 설비 시뮬레이터 (MQTT 발행) | 동작 |
| `web/` | 실시간 OEE 대시보드 | Phase 3 예정 |
| `infra/` | docker-compose (PostgreSQL, Mosquitto) | — |
| `docs/` | MQTT 토픽 계약, 백로그 | — |

## 실행 (로컬)

요구 사항: Docker Desktop, JDK 17

```powershell
# 1. PostgreSQL + Mosquitto 기동
cd infra
docker compose up -d

# 2. 백엔드 실행 (포트 9001) — 첫 기동 시 Flyway 마이그레이션 + 데모 유저 시드
cd ..\services\oee-service
.\gradlew.bat bootRun

# 3. (별도 터미널) 시뮬레이터 실행 — 설비 3대가 MQTT로 이벤트 발행
cd simulator
.\gradlew.bat run
```

- Swagger UI: http://localhost:9001/swagger-ui.html
- Health: `GET http://localhost:9001/api/health`
- 이벤트 확인: `GET /api/events/recent`, 설비 상태: `GET /api/equipments`
- MQTT 토픽 계약: [docs/mqtt-topics.md](docs/mqtt-topics.md)
- 시뮬레이터 배속: `SIM_SPEED` 환경변수 (기본 10배속)

## 데모 계정

비밀번호는 모두 `password` (첫 기동 시 자동 시드).

| username | 롤 |
|---|---|
| `admin` | ADMIN |
| `inspector` | INSPECTOR |
| `operator` | OPERATOR |

```http
POST http://localhost:9001/api/auth/login
Content-Type: application/json

{
  "username": "operator",
  "password": "password"
}
```
