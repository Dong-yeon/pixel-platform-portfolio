# MQTT 토픽 계약

simulator → oee-service 이벤트 백본. 브로커는 Mosquitto(로컬 1883, 인증 없음 — 로컬 한정).

## 토픽 구조

```
factory/{lineCode}/{equipmentCode}/{kind}
```

- `lineCode`: 라인 코드 (예: `LINE-1`)
- `equipmentCode`: 설비 코드 (예: `CNC-01`) — oee-service의 equipments 마스터와 일치해야 함
- `kind`: `status` | `cycle`

oee-service는 `factory/#`를 QoS 1, **`cleanSession=false`**로 구독한다.
서비스가 내려가 있는 동안 브로커가 메시지를 큐에 쌓아 재접속 때 밀어 준다
(브로커에 `persistence true`가 켜져 있어야 브로커 재시작까지 견딘다).

> `cleanSession=false`는 **clientId가 세션 키**다. 같은 id로 두 인스턴스를 띄우면 서로
> 밀어내므로, 로컬에서 여러 개 띄울 때는 `MQTT_CLIENT_ID`로 구분한다.

## 발행 측 접속 구조

**시뮬레이터는 설비마다 별개로 접속한다**(clientId `simulator-{equipmentCode}`).
LWT(유언)는 접속당 하나뿐이라 접속을 공유하면 설비 한 대에만 유언을 걸 수 있다.
실제 현장에서도 설비마다 자기 장치가 브로커에 붙으므로 이쪽이 도메인에도 맞다.

## 페이로드

### `status` — 설비 상태 변경 · **retained**

```json
{ "status": "DOWN", "reason": "BREAKDOWN", "ts": "2026-07-16T10:00:00Z" }
```

- `status`: `RUNNING` | `IDLE` | `DOWN` | `QUALITY_HOLD`
- `reason`: 선택 (상태 사유)
- **retained = true.** "현재 상태"이므로 나중에 붙는 구독자도 즉시 알아야 한다.
  oee-service만 재기동해도 브로커가 마지막 상태를 다시 밀어 주므로 상태가 복원된다.
- 처리: equipments.status 갱신 + `EQUIPMENT_STATUS_CHANGED` 이벤트 기록
  (DOWN→ERROR, QUALITY_HOLD→WARNING, 그 외 INFO)

#### LWT (유언) — 발행자가 비정상 종료했을 때

브로커가 대신 같은 `status` 토픽에 발행한다. 유언이 없으면 설비가 마지막 `RUNNING`으로
영원히 남아 Availability가 부풀려진다.

```json
{ "status": "DOWN", "reason": "DISCONNECTED" }
```

- **`ts`가 없다.** 유언은 접속 시점에 브로커에 맡겨 두는 고정 문구라서, 발행 시각을 미리
  박으면 실제 죽은 시각과 무관한 값이 된다. 서버는 수신 시각으로 폴백하고 WARN을 남긴다.
- 정상 종료(`disconnect()`)면 유언은 발행되지 않는다. 대신 시뮬레이터가
  `{"status":"IDLE","reason":"SIMULATOR_STOPPED"}`을 남겨 "고장"과 "멈춤"을 구분한다.

### `cycle` — 사이클 완료 (부품 1개 가공 완료) · **retained 아님**

```json
{ "cycleTimeMs": 31200, "defect": false, "ts": "2026-07-16T10:00:31Z" }
```

- `cycleTimeMs`: 실제 사이클 타임 — OEE Performance 계산 입력
- `defect`: 불량 여부 — OEE Quality 계산 입력
- **retained 금지.** 지나간 사건이다. retained로 두면 구독자가 붙을 때마다 마지막 사이클이
  한 번 더 배달돼 생산수가 유령으로 늘어난다.
- 처리: `CYCLE_COMPLETED` 이벤트 기록 (defect=true → WARNING)

## `ts` — 발생시각

두 페이로드 모두 `ts`는 **UTC ISO-8601**(`Instant`)이다.

서버는 이를 `factory_events.occurred_at`에 **시스템 기본 시간대로 변환해** 저장한다.
`created_at`(서버 적재 시각)이 로컬 시각이므로, 한쪽만 UTC로 넣으면 같은 테이블에 시차가
생기고 두 컬럼을 섞어 쓰는 순간 구간 길이가 조용히 틀어진다.

**OEE 구간 계산은 `occurred_at`을 쓴다.** `created_at`은 감사용이며, 둘의 차이가 곧
파이프라인 지연이다. `ts`가 없거나 파싱되지 않으면 적재 시각으로 폴백하고 WARN을 남긴다.

## OEE 계산과의 관계 (Phase 2)

- **Availability** = 가동 시간 / 계획 시간 ← `status` 이벤트의 RUNNING/DOWN 구간
- **Performance** = (이상 사이클타임 × 생산수) / 가동 시간 ← `cycle` 수 × equipments.ideal_cycle_time_ms
- **Quality** = 양품 수 / 생산 수 ← `cycle`의 defect 비율
