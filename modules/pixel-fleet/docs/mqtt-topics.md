# MQTT 토픽 계약 (Robot ↔ Control Server)

로봇(또는 ROS 2 브릿지)과 관제 서버 사이의 **유일한 통신 계약**이다.
관제 서버는 DDS를 알지 못하고, 로봇은 REST/DB를 알지 못한다. 모든 텔레메트리는
이 토픽 규약으로만 오간다.

## 토픽 구조

```
fleet/{robotCode}/{kind}
```

- `robotCode` — 로봇 식별자 (예: `AMR-01`)
- `kind` — 메시지 종류: `status` | `position` | `battery` | `task`
- QoS 1, payload 는 UTF-8 JSON

관제 서버 구독 필터: `fleet/#` (`application.yml`의 `mqtt.topic-filter`).

## Uplink (로봇 → 관제 서버)

### `fleet/{robotCode}/status`
로봇 상태 변화 보고.
```json
{ "status": "IDLE" }
```
`status` ∈ `IDLE`, `MOVING`, `CHARGING`, `ERROR`, `OFFLINE`
→ `ROBOT_STATUS_CHANGED` 이벤트 기록. `ERROR`=ERROR, `OFFLINE`=WARNING severity.

### `fleet/{robotCode}/position`
현재 위치(맵 좌표) + **적재 여부**. 고빈도.
```json
{ "x": 12.5, "y": 3.2, "laden": true }
```
- `laden` — 파렛트를 싣고 있는가(적재) / 아닌가(공차). 없으면 공차로 본다.
- 로봇의 마지막 위치·적재 상태만 갱신(라이브 상태). 고빈도라 tick 당 이벤트는 남기지 않는다
  (보존 비용). 갱신은 WebSocket 으로 바로 push.

> **`laden`을 별도 토픽으로 "바뀔 때만" 보내지 않는 이유.** 상태 변경만 발행하면 그 한 건이
> 유실됐을 때 서버와 **영구히** 어긋난다 — 이 프로젝트에서 로봇 상태·배터리로 이미 두 번 겪었다.
> 위치는 이동 중 매 tick 나가므로 여기 실어 보내면 **자가 복구**된다.
>
> 서버도 leg 구조로 추론할 수 있다(leg1=공차 / leg2=적재). 그래도 로봇이 보고하는 쪽을 택했다 —
> 실제 AMR이라면 파렛트 센서가 아는 물리 상태이고, 서버 재기동으로 추론 근거가 날아가지 않는다.

### `fleet/{robotCode}/battery`
배터리 잔량(%).
```json
{ "percent": 85 }
```
→ 배터리 갱신. 임계치(20%) 아래로 내려가는 순간 `ROBOT_BATTERY_LOW` (WARNING).

### `fleet/{robotCode}/task`
작업 실행 상태 보고.
```json
{ "taskCode": "T-1001", "event": "started" }
{ "taskCode": "T-1001", "event": "completed" }
{ "taskCode": "T-1001", "event": "failed", "reason": "obstacle timeout" }
```
`event` ∈ `started` | `completed` | `failed`
→ 각각 `TASK_STARTED` / `TASK_COMPLETED` / `TASK_FAILED`. 실패 시 재시도 예산 내에서 재큐잉.

## Downlink (관제 서버 → 로봇)

작업 할당을 로봇에게 지시하는 하행 토픽. `TaskService.dispatchOnce()`가 대기 작업을
가용 로봇에 배정하면 관제 서버가 발행하고, robot-sim(추후 ROS 2 브릿지)이 구독한다.

### `fleet/{robotCode}/command`
```json
{ "command": "GOTO", "taskCode": "T-1001", "origin": "STATION-A", "destination": "STATION-B" }
```
- `command` — 현재 `GOTO` 만. 로봇은 origin(픽업)→destination(드롭)으로 이동.
- 로봇은 이동 시작 시 `fleet/{code}/task {event:"started"}`, 도착 시 `completed`,
  중간 실패 시 `failed` 를 uplink 로 보고한다.
- 관제 서버는 배차 시 로봇 상태를 낙관적으로 `MOVING` 으로 표시해 같은 로봇이 중복
  배정되지 않게 하고, 이후 실제 상태는 로봇 텔레메트리로 갱신한다.

## 설계 노트

- **이벤트가 단일 진실 공급원.** 모든 uplink 는 `fleet_events`에 기록되고, 로봇/작업
  현재 상태는 이벤트를 적용한 파생 상태다.
- position 은 고빈도라 타임라인 push 대상에서 기본 제외(이벤트로는 남김).
  보존 정책은 시뮬레이터 발행 주기와 함께 조정한다.
- 알 수 없는 robotCode/taskCode 텔레메트리는 컨슈머를 죽이지 않고 warn 로깅만 한다.
