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
주문(다단 스텝) 실행 상태 보고. **`taskCode`가 아니라 `orderCode`** — P19-1로 주문 모델이
바뀌면서 필드명도 같이 바뀌었다.
```json
{ "orderCode": "FO-00000001", "event": "started" }
{ "orderCode": "FO-00000001", "event": "step-done", "stepIndex": 0 }
{ "orderCode": "FO-00000001", "event": "failed", "reason": "obstacle timeout" }
```
`event` ∈ `started` | `step-done` | `failed`. **`completed`는 없다** — 완료는 서버가
마지막 스텝의 `step-done`에서 스스로 판단한다(주문이 몇 스텝짜리인지는 서버만 안다).
→ 각각 `TASK_STARTED` / (스텝 경계마다 다음 레그 예약, 마지막 스텝이면 주문을 닫고
`TASK_COMPLETED` + downlink `ORDER_DONE`) / `TASK_FAILED`. 실패 시 재시도 예산(3회) 내에서
전체 재큐, 소진되면 `fault`로 얼려 사람의 retry-failed를 기다린다.

## Downlink (관제 서버 → 로봇)

스텝 이동을 로봇에게 지시하는 하행 토픽. `OrderService.dispatchOnce()`/스텝 경계마다
관제 서버가 발행하고, robot-sim(추후 ROS 2 브릿지)이 구독한다.

### `fleet/{robotCode}/command`
```json
{ "command": "GOTO", "orderCode": "FO-00000001", "stepIndex": 0, "location": "WH-PICK",
  "forLoad": true, "forUnload": false, "waypoints": [[1.0, 2.0], [3.5, 2.0]] }
{ "command": "ORDER_DONE", "orderCode": "FO-00000001" }
```
- `GOTO` — 로봇은 `waypoints`(서버가 계산한 경로, 구간 점유 통제를 위해 순서대로)를 따라
  `location`으로 이동해 `stepIndex` 스텝을 실행한다(`forLoad`/`forUnload`로 싣기/내리기 구분).
- `ORDER_DONE` — 주문이 완전히 끝났으니 로봇은 다음 배차를 기다리는 상태로 돌아간다.
- 로봇은 스텝 시작 시 `fleet/{code}/task {event:"started"}`(첫 스텝만), 스텝 도착 시
  `step-done`, 중간 실패 시 `failed`를 uplink로 보고한다.
- 관제 서버는 배차 시 로봇 상태를 낙관적으로 `MOVING`으로 표시해 같은 로봇이 중복
  배정되지 않게 하고, 이후 실제 상태는 로봇 텔레메트리로 갱신한다.

## 레이아웃 이벤트 (P20-4)

로봇 스코프가 아니라 **레이아웃(그래프 엣지) 스코프**라 토픽 모양이 다르다 — 세그먼트가
4개다. 누가 보내는지는 계약에서 안 가린다(지금은 robot-sim의 `ObstacleSimulator`가 보내지만,
컴포저블 원칙상 이 계약만 지키면 무엇이든 발행할 수 있다).

### `fleet/layout/{buildingCode}/obstacle`
```json
{ "kind": "OBSTACLE_ADDED", "fromNode": "JCT-14-U", "toNode": "JCT-27-U",
  "reason": "지게차 통행", "validUntil": "2026-08-06T10:15:00Z" }
{ "kind": "OBSTACLE_CLEARED", "fromNode": "JCT-14-U", "toNode": "JCT-27-U", "reason": "정리 완료" }
```
- `kind` ∈ `OBSTACLE_ADDED` | `OBSTACLE_CLEARED`
- `fromNode`/`toNode` — 막힌 엣지의 두 끝(순서 무관, 방향과 무관하게 정본 id로 정규화된다 —
  `LaneGraph.canonicalEdgeId`). factory `layout_edges`에 실재하는 엣지가 아니어도 조용히
  무시된다(그래프에 없는 엣지는 막을 게 없다).
- `validUntil` — ISO-8601. 없거나 못 읽으면 기본 2분, 최대 10분으로 자른다.
- `buildingCode`는 지금은 로깅용일 뿐 라우팅에 쓰이지 않는다(엣지는 노드 쌍만으로 전역 유일).

→ `ObstacleService`가 `ObstacleStore`(Redis, TTL 기반)에 반영하고
`LAYOUT_OBSTACLE_ADDED`/`LAYOUT_OBSTACLE_CLEARED` 이벤트를 남긴 뒤, 대기 중인 주문의 경로
캐시를 비운다 — 다음 재시도에서 `LaneGraph`가 새로 계산해 막힌 엣지를 피한다(설계 근거:
`docs/p20-layout-routing-design.md` D4·D5).

> **factory DB엔 안 쓴다.** `layout_edges`는 정적 토폴로지(무엇과 무엇이 이어져 있는가)만
> 갖는다 — "지금 막혀 있는가"는 로봇 위치처럼 자주 바뀌는 라이브 상태라 fleet의 Redis에만
> 있다(DB per module).

## 설계 노트

- **이벤트가 단일 진실 공급원.** 모든 uplink 는 `fleet_events`에 기록되고, 로봇/작업
  현재 상태는 이벤트를 적용한 파생 상태다.
- position 은 고빈도라 타임라인 push 대상에서 기본 제외(이벤트로는 남김).
  보존 정책은 시뮬레이터 발행 주기와 함께 조정한다.
- 알 수 없는 robotCode/taskCode 텔레메트리는 컨슈머를 죽이지 않고 warn 로깅만 한다.
