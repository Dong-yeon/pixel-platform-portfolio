# infra — 통합 로컬 인프라

모듈들이 공유하는 백엔드 서비스(PostgreSQL, Mosquitto, Redis)를 한 번에 띄운다.
애플리케이션은 각 모듈에서 실행한다.

```bash
cd infra
docker compose up -d      # 기동
docker compose ps         # 상태
docker compose down       # 정지 (데이터 유지)
docker compose down -v    # 정지 + 데이터 삭제 (스키마 초기화)
```

## 서비스

| 서비스 | 포트 | 용도 |
|---|---|---|
| `pixel-postgres` | 5432 | 모듈당 DB 하나 (`pixelfactory`, `pixelfleet`, `pixelwms`, `pixelqms`) |
| `pixel-mosquitto` | 1883 | 모듈 공용 MQTT 브로커 (토픽 네임스페이스로 분리) |
| `pixel-redis` | 6379 | pixel-fleet 라이브 상태 + 실시간 push 팬아웃 |

## DB per module

| 모듈 | DB | 계정 |
|---|---|---|
| pixel-factory | `pixelfactory` | `pixel` / `pixel` |
| pixel-fleet | `pixelfleet` | `fleet` / `fleet` |
| pixel-wms | `pixelwms` | `wms` / `wms` |
| pixel-qms | `pixelqms` | `qms` / `qms` |

계정·DB명은 각 모듈이 단독 실행할 때 쓰던 값 그대로다 → **모듈 설정 변경 불필요**.
새 모듈은 `postgres-init/01-create-databases.sql`에 role + database를 추가한다.

> `postgres-init/`의 스크립트는 **빈 볼륨에서 최초 기동할 때만** 실행된다.
> 스크립트를 고친 뒤에는 `docker compose down -v`로 볼륨을 지우고 다시 올려야 반영된다.

## MQTT 토픽 네임스페이스

브로커는 공유하되 접두사로 분리한다.

| 모듈 | 토픽 | 구독 필터 |
|---|---|---|
| pixel-factory | `factory/{lineCode}/{equipmentCode}/{kind}` | `factory/#` |
| pixel-fleet | `fleet/{robotCode}/{kind}`, `fleet/{robotCode}/command` | `fleet/#` |
| pixel-wms | (발행 없음) | `fleet/tasks/#` |
| pixel-qms | (발행 없음) | `factory/quality/#` |

**모듈 간 통지** — 발행자는 구독자를 모른다(컴포저블). 마디 수가 달라 자기 자신의 핸들러는 무시한다.

- fleet → `fleet/tasks/{taskCode}/{completed|failed}` (4마디; 로봇 텔레메트리는 3마디) — WMS가 구독해 재고를 차감한다.
- factory → `factory/quality/inspection-requested` (3마디; 설비 텔레메트리는 4마디) — 불량 임계 초과 시.
  QMS가 구독해 검사를 만든다. 홀드/해제는 QMS가 factory `/api/quality/**` REST로 요청한다.

## 모듈 실행 (인프라 기동 후)

```powershell
# pixel-factory (:8081)
cd modules\pixel-factory\services\oee-service ; .\gradlew.bat bootRun

# pixel-fleet (:8082)
cd modules\pixel-fleet\services\control-service ; .\gradlew.bat bootRun
cd modules\pixel-fleet\robot-sim               ; .\gradlew.bat bootRun

# pixel-wms (:9003)
cd modules\pixel-wms\services\wms-service ; .\gradlew.bat bootRun

# pixel-qms (:9004)
cd modules\pixel-qms\services\qms-service ; .\gradlew.bat bootRun
```

> 볼륨이 이미 있으면 `postgres-init`이 다시 돌지 않아 새 모듈 DB가 없다. 볼륨을 지우지 않고
> 추가하려면:
> ```powershell
> docker exec pixel-postgres psql -U pixel -d pixelfactory -c "create role wms with login password 'wms'"
> docker exec pixel-postgres psql -U pixel -d pixelfactory -c "create database pixelwms owner wms"
> docker exec pixel-postgres psql -U pixel -d pixelfactory -c "create role qms with login password 'qms'"
> docker exec pixel-postgres psql -U pixel -d pixelfactory -c "create database pixelqms owner qms"
> ```
