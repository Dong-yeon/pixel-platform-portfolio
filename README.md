# PixelFactory

자동차 부품 가공 라인 **OEE 실시간 모니터링** 데모.
이벤트 기반 컴포저블 구조 — 자세한 목표/원칙/로드맵은 [CLAUDE.md](CLAUDE.md) 참고.

## 구조

| 디렉터리 | 역할 | 상태 |
|---|---|---|
| `services/oee-service/` | Spring Boot 3 백엔드 (이벤트 수집·OEE 계산·API) | 개발 중 |
| `simulator/` | 설비 시뮬레이터 (MQTT 발행) | Phase 1 예정 |
| `web/` | 실시간 OEE 대시보드 | Phase 3 예정 |
| `infra/` | docker-compose (PostgreSQL, 추후 Mosquitto) | — |
| `docs/` | 백로그, 설계 문서 | — |

## 실행 (로컬)

요구 사항: Docker Desktop, JDK 17

```powershell
# 1. PostgreSQL 기동
cd infra
docker compose up -d postgres

# 2. 백엔드 실행 (포트 8081)
cd ..\services\oee-service
.\gradlew.bat bootRun
```

- Swagger UI: http://localhost:8081/swagger-ui.html
- Health: `GET http://localhost:8081/api/health`

## Mock 로그인 (임시)

```http
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{
  "username": "operator",
  "password": "password"
}
```

- `admin` → `ADMIN`
- `inspector` → `INSPECTOR`
- 그 외 → `OPERATOR`

Phase 1에서 User 테이블 + PasswordEncoder 기반 실제 인증으로 교체 예정.
