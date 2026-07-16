# MQTT 토픽 계약

simulator → oee-service 이벤트 백본. 브로커는 Mosquitto(로컬 1883, 인증 없음 — 로컬 한정).

## 토픽 구조

```
factory/{lineCode}/{equipmentCode}/{kind}
```

- `lineCode`: 라인 코드 (예: `LINE-1`)
- `equipmentCode`: 설비 코드 (예: `CNC-01`) — oee-service의 equipments 마스터와 일치해야 함
- `kind`: `status` | `cycle`

oee-service는 `factory/#`를 QoS 1로 구독한다.

## 페이로드

### `status` — 설비 상태 변경

```json
{ "status": "DOWN", "reason": "BREAKDOWN", "ts": "2026-07-16T10:00:00Z" }
```

- `status`: `RUNNING` | `IDLE` | `DOWN` | `QUALITY_HOLD`
- `reason`: 선택 (상태 사유)
- 처리: equipments.status 갱신 + `EQUIPMENT_STATUS_CHANGED` 이벤트 기록
  (DOWN→ERROR, QUALITY_HOLD→WARNING, 그 외 INFO)

### `cycle` — 사이클 완료 (부품 1개 가공 완료)

```json
{ "cycleTimeMs": 31200, "defect": false, "ts": "2026-07-16T10:00:31Z" }
```

- `cycleTimeMs`: 실제 사이클 타임 — OEE Performance 계산 입력
- `defect`: 불량 여부 — OEE Quality 계산 입력
- 처리: `CYCLE_COMPLETED` 이벤트 기록 (defect=true → WARNING)

## OEE 계산과의 관계 (Phase 2)

- **Availability** = 가동 시간 / 계획 시간 ← `status` 이벤트의 RUNNING/DOWN 구간
- **Performance** = (이상 사이클타임 × 생산수) / 가동 시간 ← `cycle` 수 × equipments.ideal_cycle_time_ms
- **Quality** = 양품 수 / 생산 수 ← `cycle`의 defect 비율
