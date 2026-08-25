# P27 설계 문서 — 배터리 3단계 재정렬 + 엘리베이터를 배타적 자원으로

> 상태: **구현 + 실기동 검증 완료(2026-08-25).** `docs/p20~p26-*.md`와 같은 형식.
>
> 근거 자료: `amr 사양요구서.docx` §"AMR 충전 전략"(배터리 레벨 상/중/하, 임계값·전략표)
> §"엘리베이터 연동"(0~12단계 연동 프로세스). 후자는 실물 엘리베이터 제어반과의 신호
> 프로토콜(도어 개방 유지 명령 등)까지 다루는데, 이 포트폴리오엔 그 신호를 낼 실물도
> 시뮬레이터의 문 개폐 모델도 없다 — **없는 데이터를 지어내지 않는다**는 원칙 그대로,
> 신호 하나하나를 흉내 내지 않고 그 프로세스가 실제로 만드는 **물리적 제약**(엘리베이터는
> 한 번에 한 대만 태운다)만 가져온다.

---

## 0. 범위 재확정 — 두 항목 다 "새 데이터를 지어내는" 방향이 아니라 "이미 아는 제약을 강제하는" 방향

- **배터리**: 지금도 임계값 두 개(로봇 자가충전 30%, 배차 최소 25%)가 있다 — 사양서의
  3단계(상 80%·중 30~80%·하 30% 미만)와 **숫자가 다르고, "유휴 시 상시 충전"이 없다.**
  숫자를 사양서에 맞추고 빠진 규칙 하나(유휴 로봇은 80% 미만이면 충전)를 채운다 — 새
  개념을 만들지 않는다.
- **엘리베이터**: 사양서의 12단계 핸드셰이크(문 열림 유지 명령 등)를 그대로 흉내 내려면
  없는 도어 센서·PLC 신호를 지어내야 한다. 대신 그 프로세스가 실제로 강제하는 **물리적
  사실**만 가져온다 — 엘리베이터 카는 한 대뿐이고, 한 번에 한 운송만 태운다. 지금
  `createHandoffOrder`는 이 제약을 아예 모른다(두 운송이 동시에 층을 넘어도 서로
  독립적인 12초 타이머를 각자 받는다) — `TrafficController`가 레인 구간을 배타적으로
  잠그는 것과 같은 종류의 구멍이다.

---

## 1. 핵심 원칙 재확인

| 원칙 | 이 설계가 지키는 방법 |
|---|---|
| 없는 데이터를 시각효과로 지어내지 않는다 | 문 열림/닫힘 세부 단계·PLC 신호는 **범위 밖**(8절) — 시뮬레이터가 실제로 아는 사실(로봇 위치·주문 상태·경과 시간)만으로 표현 가능한 것만 다룬다 |
| 전례 재사용 | 엘리베이터 배타적 점유는 `TrafficController`(P20, 구간 배타 잠금)와 **같은 패턴**이다 — 새 동시성 모델을 발명하지 않는다 |
| 두 배차 정책이 같은 불변식을 지켜야 한다(P23 주석) | `MIN_BATTERY_PERCENT` 변경을 `NearestBatteryAwareAssignmentPolicy`/`GraphCostAwareAssignmentPolicy` 양쪽에 동일하게 반영 |

---

## 2. 확정 결정 (D1 ~ D5)

### D1. 배터리 임계값을 사양서 숫자로 재정렬

| | 지금 | 사양서 | 이후 |
|---|---|---|---|
| 배차 최소(신규 작업 수락) | 25% | "50% 이상이어야 신규 작업 할당" | **50%** |
| 자가충전 트리거(유휴 시) | 30% 미만일 때만 | "상(≥80%)은 충전 안 함, 중(30~80%)은 유휴 시 충전" | **80% 미만이면 유휴 시 충전** |

`NearestBatteryAwareAssignmentPolicy.MIN_BATTERY_PERCENT`·`GraphCostAwareAssignmentPolicy.
MIN_BATTERY_PERCENT`를 25→50으로, robot-sim `SimProperties.lowBatteryThreshold`(30)를
`highBatteryThreshold`(80)로 바꾼다 — **이름도 바꾼다**: 지금 이름은 "낮으면 충전"이라는
뜻인데, 새 규칙은 "**높지 않으면**(80% 미만) 충전"이라 반대 방향이다. 사양서의 상/중/하
3단계 중 **중·하를 하나로 합친다** — 시뮬레이터 관점에서 둘의 행동(유휴 시 자리 이동해
충전)이 똑같기 때문이다(로봇이 "작업 중이면 완료 후 즉시", "유휴면 즉시" 둘 다 결국
"다음 유휴 시점에 충전 여부를 본다"는 하나의 판정으로 접힌다 — 진행 중인 작업을
중단시키는 인터럽트는 이 시뮬레이터에 없다, 8절).

### D2. 불변식 재확인 — 사각지대 재검증

기존 주석("로봇의 충전 복귀 기준 > 관제 서버의 배차 최소 배터리")이 지키려던 것: 배차도
안 되고 충전도 안 가는 배터리 구간이 없어야 한다. 숫자를 바꾼 뒤에도
**`highBatteryThreshold`(80) > `MIN_BATTERY_PERCENT`(50)**가 성립해 그대로 유지된다 —
30~50% 구간의 로봇은 (a) 새 작업은 못 받지만 (b) 유휴 상태가 되는 즉시 80% 미만이므로
충전하러 간다. 사각지대가 없다.

### D3. `ElevatorController` 신설 — `TrafficController`와 같은 패턴

```java
@Component
public class ElevatorController {
    // 지금 이 포트폴리오에 실제 구현된 엘리베이터는 창고동 하나뿐이다(elevatorNode()의
    // "WH-" 하드코딩과 같은 전제 — 새 하드코딩이 아니라 기존 것과 일관되게 맞춘다).
    private static final String SHAFT_ID = "WH-ELEVATOR";
    private final Map<String, LocalDateTime> nextFreeAt = new ConcurrentHashMap<>();

    /** 지금 요청하면 언제부터 탈 수 있는가 — 이미 예약된 마지막 탑승 이후로 큐잉된다. */
    public synchronized LocalDateTime reserve(LocalDateTime requestedAt, int rideSeconds) {
        LocalDateTime start = nextFreeAt.getOrDefault(SHAFT_ID, requestedAt);
        if (start.isBefore(requestedAt)) {
            start = requestedAt;
        }
        LocalDateTime arrival = start.plusSeconds(rideSeconds);
        nextFreeAt.put(SHAFT_ID, arrival);
        return arrival;
    }
}
```

- **왜 `TrafficController`처럼 "잡았다 놓는다" 방식이 아니라 시간 계산인가.** 레인 구간은
  "누가 지금 쥐고 있는가"를 실시간으로 알아야 하지만(로봇 위치 텔레메트리로 진행을
  본다), 엘리베이터는 이미 "얼마나 걸리는지"(`elevatorTravelSeconds`, 고정값)를 알고
  있어 **예약 시점에 전체 큐를 계산**할 수 있다 — 별도 점유/반납 생명주기(로봇이 언제
  내렸는지 보고)가 필요 없다. `release`가 없는 이유이기도 하다(시간이 지나면 자동으로
  다음 예약이 그 뒤를 잇는다).
- 인메모리다(`TrafficController`와 같음, DB 없음) — 서버가 재시작되면 큐가 비지만, 그
  시점에 실제로 승강 중이던 화물도 없으므로(재시작 자체가 드문 데모 환경) 무해하다.

### D4. `createHandoffOrder`가 `ElevatorController`를 통해 대기 시간을 반영

```java
if (crossesFloor) {
    startNode = elevatorNode(arrivalFloor);
    LocalDateTime requestedAt = LocalDateTime.now();
    LocalDateTime arrival = elevatorController.reserve(requestedAt, elevatorTravelSeconds);
    availableAt = arrival;
    boolean queued = arrival.isAfter(requestedAt.plusSeconds(elevatorTravelSeconds).minusNanos(1));
    logMessage = queued
        ? "엘리베이터: " + finished.getOrderCode() + " 다른 운송이 먼저 사용 중 — " + startNode + "에서 "
                + arrival + "까지 대기 후 인수"
        : "엘리베이터: " + finished.getOrderCode() + " 화물이 " + arrivalFloor + "층으로 이동 중 ("
                + elevatorTravelSeconds + "초 후 " + startNode + "에서 인수)";
}
```

큐잉이 실제로 있었는지(`queued`)를 판정해 이벤트 메시지를 구분한다 — 지어낸 정보가
아니라 방금 계산한 실제 대기 시간의 유무다.

### D5. `elevatorTravelSeconds`(12초, 고정값)는 그대로 둔다

사양서가 정확한 왕복 시간을 안 준다(엘리베이터 기종·층고에 따라 다르다) — 지금 값을
바꿀 근거가 없다. D3~D4는 "몇 초 걸리는지"가 아니라 "동시에 몇 대가 쓸 수 있는지"를
고치는 것이라 이 값과 독립적이다.

---

## 3. 실행 단계

- [ ] fleet: `NearestBatteryAwareAssignmentPolicy`/`GraphCostAwareAssignmentPolicy`의
      `MIN_BATTERY_PERCENT` 25→50(D1·D2)
- [ ] robot-sim: `SimProperties.lowBatteryThreshold` → `highBatteryThreshold`(80),
      `application.yml` 갱신, `Simulator.tickIdle` 조건 반전(`< low` → `< high`)(D1)
- [ ] fleet: `ElevatorController` 신설(D3), `OrderService.createHandoffOrder`에 배선(D4)
- [ ] e2e: factory+fleet+robot-sim 재기동 — 배차가 50% 미만 로봇을 거르는지, 유휴 로봇이
      80% 미만이면 스스로 충전 이동하는지, 두 운송이 거의 동시에 층을 넘을 때 두 번째가
      대기 메시지와 함께 늦게 인수되는지 실기동 확인

---

## 4. 리스크 & 롤백

- **배차 최소를 50%로 올리면 데모 트래픽에서 가용 로봇 수가 줄어들 수 있다** — 특히
  `DemoTaskGenerator`가 계속 새 작업을 만드는 상황에서 로봇들이 30~50% 구간을 오래
  맴돌면 체감상 "일 안 하는 로봇"이 늘어 보일 수 있다. D2의 불변식(80% 미만 유휴 시
  충전)이 이 구간 체류 시간 자체를 줄이므로 상쇄되지만, e2e에서 실측해 확인한다.
- **엘리베이터 큐가 무한정 길어질 가능성** — 지금은 검토하지 않는다(실제로 초당 여러
  건이 몰리는 트래픽 패턴이 없다). 필요해지면 큐 길이 상한이나 우선순위 큐로 확장할 자리
  (`ElevatorController.reserve`가 이미 유일한 진입점이라 확장이 쉽다).
- **롤백 수단**: D3·D4는 `ElevatorController` 하나와 `createHandoffOrder`의 호출 한 줄로
  닫혀 있다 — 문제가 생기면 `reserve()` 호출을 예전 `now().plusSeconds(...)` 계산으로
  되돌리면 즉시 원복된다. D1·D2(배터리 숫자)는 상수 두 곳 되돌리면 끝난다.

---

## 5. 완료 기준

- [ ] 배터리 50% 미만 로봇은 새 주문을 배차받지 않는다(두 정책 모두)
- [ ] 유휴 로봇은 배터리 80% 미만이면 스스로 충전 스테이션으로 이동한다
- [ ] 30~50% 로봇이 유휴 상태에서 방치되지 않고(불변식) 충전을 시작한다
- [ ] 같은 시간대에 두 운송이 층을 넘으면, 두 번째가 첫 번째가 끝난 뒤로 순연되고
      이벤트 로그에 대기 사실이 남는다
- [ ] 기존 엘리베이터 단일 운송 시나리오(큐잉 없음)는 회귀 없이 예전과 같은 12초 타이머로 동작한다

---

## 6. 이번엔 안 하는 것 (범위 밖)

- **문 열림/닫힘·PLC 신호 단계별 시뮬레이션** — 실물 도어 센서가 없다(0절 근거). 이걸
  만들면 "없는 데이터를 지어낸다"는 원칙을 정면으로 어긴다.
- **진행 중인 작업을 배터리 상태 변화로 중단(인터럽트)** — 지금 시뮬레이터에 그런
  메커니즘이 없다(D1 근거). 로봇은 항상 현재 레그를 마친 뒤에야 다음 판단을 한다.
- **엘리베이터 우선순위 큐(긴급 운송 새치기)** — 지금 `reserve()`는 요청 순서(FIFO)다.
  주문 우선순위(`FleetOrder.priority`)를 큐 순서에 반영하는 건 별도 검토 대상.
- **여러 엘리베이터·여러 건물** — 지금 구현이 창고동 하나뿐이라(D3 근거) 다중 샤프트는
  다룰 대상이 없다. 새 건물에 엘리베이터가 추가되면 `SHAFT_ID`를 건물별로 나누는 확장이
  필요하다.
