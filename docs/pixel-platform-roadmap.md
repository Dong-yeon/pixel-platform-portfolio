# Pixel Platform — 데모 완성 로드맵 (P8 ~ P15)

> **목적:** MES · QMS · WMS · 로봇관제 4개 시스템이 **하나의 공장으로 맞물려 돌아가는**
> 포트폴리오 데모 사이트를 완성한다.
>
> **이 문서의 사용법:** Claude Code가 단계 단위로 집어 실행한다. 각 단계는
> `목표 → 작업 → 완료 기준 → 주의` 순서이며, **완료 기준을 통과하지 못하면 다음 단계로 넘어가지 않는다.**
> 한 단계 = 한 커밋(또는 소수의 커밋)을 원칙으로 한다.
>
> 선행 문서: [`pixel-platform-plan.md`](./pixel-platform-plan.md) (P0~P7 재구성 계획).
> 이 문서는 그 뒤를 잇는다.

---

## 0. 현재 상태 스냅샷 (최종 갱신 2026-07-29, 코드 재확인)

### 완료된 것

| 항목 | 근거 |
|---|---|
| 모노레포 재구성 (P1~P4) | `platform/`, `modules/`, `infra/` 배치 완료 |
| 게이트웨이 | `platform/gateway/src/main/resources/application.yml` — RewritePath, DedupeResponseHeader, 모듈별 `/ws/{module}` 프록시, 중앙 인증 필터 |
| 통합 대시보드 | `platform/dashboard/` — Overview / Factory / Fleet 3탭, `UnifiedMap` |
| **공통 코어 추출 (P5)** | `shared/src/main/java/com/pixelplatform/core/` — common·auth·user |
| **중앙 인증 (P6 = 아래 P12-1)** | `platform/gateway/.../auth/AuthenticationGlobalFilter` — **P12보다 먼저 끝냈다** |
| pixel-fleet 전체 | 배차 정책, 교통정리(leg 단위 예약), Redis 라이브 상태 + Pub/Sub, STOMP push, robot-sim |
| pixel-factory 골격 | 설비/라인 마스터, MQTT 수집, 작업지시 상태머신, 이벤트 영속화, 설비 8대 시뮬레이터 |

### 확인된 결함 (근거 포함)

> 상태는 2026-07-29에 코드로 재확인했다. ✅는 이 로드맵 작성 이후 해결된 것.

| # | 결함 | 상태 | 근거 | 영향 |
|---|---|---|---|---|
| D1 | **대시보드가 factory 데이터를 최초 1회만 조회** | ✅ **해결** | P10: factory STOMP(`/ws/factory`) — 설비·이벤트는 즉시 push, OEE는 5초 주기. `usePlatformSocket`으로 모듈별 연결 | — |
| D2 | **사이클 이벤트가 작업지시 실적에 반영되지 않음** | ✅ **해결** | `MqttMessageHandler.handleCycle()`이 `IN_PROGRESS` 작업지시를 찾아 `workOrder.recordCycle(defect)` 호출, `workOrderId` 기록 | — |
| D3 | **이벤트 발생시각 컬럼 없음** | ✅ **해결** | P8에서 `V4` 마이그레이션 + `FactoryEvent.occurredAt`. 핸들러가 payload `ts`(UTC)를 시스템 시간대로 변환해 저장, 실패 시 적재 시각 폴백+WARN | — |
| D4 | **설비별 기간 조회 인덱스 없음** | ✅ **해결** | P8 `V4`에서 `idx_factory_events_target_type_time`(target_type, target_id, event_type, occurred_at desc) + `idx_factory_events_occurred_at` | — |
| D5 | **계획가동시간 정의 불가** | ✅ **해결** | P9: `shift_calendars`(휴식 시각 포함) + `EquipmentStatus`에 SETUP·PLANNED_STOP 추가. 계획/실가동 판정은 enum 메서드로 | — |
| D6 | **표준CT가 설비 고정값** | ❌ | `equipments.ideal_cycle_time_ms`. `items`/`processes` 테이블 없음, `WorkOrder.itemId`는 FK 없는 raw bigint | 품종 전환 시 Performance 왜곡 |
| D7 | **좌표계 3중 하드코딩** | ✅ **해결** | P11: factory가 `layout_nodes`·`layout_settings`·`equipments.pos_*` 마스터를 소유. 대시보드는 API로만, fleet은 받아 캐시(폴백+WARN), robot-sim은 대조 테스트로 검증 | 부분 잔존(LaneGraph 통로·연결로) |
| D8 | **MQTT 유실 설계** | ✅ **해결** | P8: 브로커 `persistence true` + `max_queued_messages 100000`, 구독자 `cleanSession=false`(factory·fleet). **유한 버퍼** — 약 7시간치 | 부분 잔존(장기 장애) |
| D9 | **LWT / retained 미사용** | ✅ **해결** | P8: 설비별 접속 + 자기 status 토픽에 LWT, status만 retained(cycle은 금지) | — |
| D10 | **모듈별 개별 인증 (P6 미완)** | ✅ **해결** | P6에서 게이트웨이 중앙 인증. 토큰 1개(`pp_token`), `loginAll()` 제거 | — |
| D11 | **게이트웨이 `/ws` 라우트가 fleet 전용** | ✅ **해결** | P10: `/ws/fleet/**`→9002, `/ws/factory/**`→9001. 서버 엔드포인트도 같은 경로로 이동 | — |
| D12 | **미사용 enum 값** | ❌ | `FactoryEventType.NOTIFICATION_SENT`, `INSPECTION_*` 4종 | P14에서 실제로 채운다 |
| D13 | **설비 8대 중 3대만 시뮬레이션됨** | ✅ **해결** | `FactorySimulator.EQUIPMENTS`가 8대 전부 | — |

### 이 로드맵에 없던 항목 (진행 중 발견 — `docs/BACKLOG.md`에 상세)

| 항목 | 상태 |
|---|---|
| AMR 교통정리 (레인 그래프·구간 점유·leg 단위 예약·고아 작업 워치독) | ✅ 완료 |
| 배터리 20~24% 사각지대 — 함대 전체가 멈춤 | ✅ 수정 |
| `/ws/**`가 인증 없이 열려 있음 (SockJS 핸드셰이크 제약) | ❌ |
| 동시 주행이 1~2대 — 가로 구간이 통짜라 뒤차가 못 들어옴 | ❌ |
| 충전 주행이 서버 통제 밖 | ❌ |

### 4종 세트 대비 실제 커버리지

| 표방 | 실제 상태 |
|---|---|
| 로봇관제 | ✅ 완성도 높음 (유일하게 "제품"처럼 보임) |
| MES | ⚠️ **OEE 엔진(P9) + 실시간 대시보드(P10) 완료.** POP 없음, 품목 마스터 없음 |
| QMS | ❌ enum 껍데기만 |
| WMS | ❌ `WAREHOUSE` 노드 좌표 1개뿐 |

---

## 0-A. 실제 운영 시스템에서 가져온 근거 (`skills/` 참조)

동연님이 실제로 운영·유지보수하는 MES/WMS/QMS([CUSTOMER] · [CUSTOMER] · [CUSTOMER] · [CUSTOMER] · [CUSTOMER])의
skill 문서에서 확인한 것들이다. **데모를 그럴듯하게 만드는 게 아니라, 실제 시스템이 그렇게
생긴 이유를 따라가는 것**이 목적이다.

### 도메인 구조 (P13·P14 설계 근거)

| 개념 | 실제 시스템 | 데모에 반영할 것 |
|---|---|---|
| **파렛트** | `pallet_master_table` / `pallet_load_table` / `pallet_no` — **1급 엔티티** | **결정: 엔터티로 만든다.** 운송 작업이 파렛트를 지정하고 지도·목록에 번호가 나온다. 지금의 `laden` 불리언은 1단계였고, LOT 추적(4단 트리)의 기반이 된다 → **P12 직후 착수**(fleet 쪽이라 P12와 독립) |
| **창고 도면** | 선반(rack) × 단(level 1~5) × 자리(bin). 실제 화면은 선반 15개 · 자리 2,631개 | **공장 평면도와 다른 좌표계다.** 별도 뷰여야 한다 |
| **자재 박스** | `barcode_no`, Box 잔량, 역순 차감/복원 | 자재 소비·반납을 이력으로 |
| **LOT 추적** | 작업지시번호/LOT/작업LOT/생산LOT/자재박스바코드/**파렛트번호** 아무거나 스캔 → 우선순위 `UNION ALL`로 해소, 4단 트리 | 스캔 한 번으로 전 계층을 잇는 화면이 이 도메인의 하이라이트 |
| **공정 회차(round)** | 실적을 회차로 쌓고 집계를 롤업 | 작업지시에 실적을 직접 누적하는 현재 구조의 한계 |
| **MRB** | 불량 발생 시 원장 자동 생성(9유형 · 등급 W/Y/R), **부분·반복 처분**(재작업+폐기 동시), 남은=0일 때만 종료 | P14 상태머신을 이걸로 대체 |

### 엔지니어링 규칙 (이미 적용했거나 지금부터 적용)

| 규칙 | 상태 |
|---|---|
| **완료 판정은 보존법칙** — `양품 + 전공정 불량합 ≥ 지시수량`. 최종공정 불량만 보면 상류 불량으로 흐름이 줄 때 영원히 미완료 | ⚠️ 우리 `recordCycle`은 계획수량 캡만 본다 — P13에서 다공정이 생기면 반드시 적용 |
| **부수효과(메일/라벨/외부호출)는 본 트랜잭션 밖에서 try-catch** | ✅ `@TransactionalEventListener(AFTER_COMMIT)` |
| **외부 연계에 타임아웃 필수** — 기본 `RestTemplate`은 무한대기. 무응답 시 그 연계를 쓰지 않는 화면까지 멈춘다 | ✅ `LocationRegistry`가 connect 3s / request 5s |
| **취소·반납은 원본 수정이 아니라 음수 보상 이력** | ✅ 이벤트 소싱 원칙과 동일 |
| **저장 연타 = TOCTOU. 3층 방어**(프론트 재진입 가드 / 요청 내부 중복 제거 / 트랜잭션 안 재확인) | ❌ **P12 POP 착수·실적 입력에 반드시 필요** |
| **권한은 서버가 세션에서 재산출. 화면 잠금은 UX용, 실제 차단은 서버 재검증** | ❌ **P12 역할별 뷰의 핵심** — 프론트에서 탭을 숨기는 것만으로는 안 된다 |
| **채번은 MAX+1 단독 금지** — 동시 저장 시 같은 번호 | ❌ 작업지시/MRB 번호 만들 때 |
| **날짜 포맷 `hh`는 12시간제** — 오후에만 어긋나 발견이 늦다 | 확인 필요 |

---

## 1. 절대 원칙 (전 단계 공통 — 어길 거면 먼저 이 문서를 고칠 것)

각 모듈 `CLAUDE.md`의 원칙을 그대로 승계한다. 이 로드맵에서 특히 자주 걸리는 것:

1. **이벤트가 단일 진실 공급원.** 화면에 그리는 모든 것은 실제 이벤트/마스터에서 파생돼야 한다.
   **없는 데이터를 시각 효과로 지어내지 않는다.** (→ 사람이 지도 위를 걸어다니는 애니메이션 금지)
2. **컴포저블.** 모듈 간 코드/DB 직접 참조 금지. 게이트웨이·REST·MQTT 계약으로만.
3. **DB per module.** 새 모듈은 자기 DB를 갖는다.
4. **스키마는 Flyway 마이그레이션으로.** `ddl-auto` 의존 금지.
5. **게임 메커닉 금지.** 점수·레벨·보상 없음. 단, "조작하면 실시간 반응"은 핵심 경험이므로 유지.
6. **빌드는 PowerShell `.\gradlew.bat`.** bash `./gradlew`는 Windows에서 `-Xmx` 파싱이 깨진다.
7. **파일 삭제·대규모 이동은 사전 계획 제시 후 승인받고 진행.**

### 지도 시각 규칙 (P11 이후 계속 적용)

한 평면도(44 × 24) 위에 여러 시스템이 겹치므로 표현 규칙을 고정한다.

| 대상 | 모양 | 출처 |
|---|---|---|
| 설비 | 사각형, 상태별 색 | `equipments.status` |
| AMR | 원형, 실시간 이동 | Redis 라이브 상태 |
| 하역 지점 | 작은 사각 + 라벨 | 노드 마스터 |
| **POP 단말** | 세로 직사각(키오스크), 사용 중이면 배지 | `pop_terminals` + 최근 조작 이벤트 |
| **작업자/검사원** | **독립 마커 금지.** 단말/설비 마커에 붙는 배지로만 | 최근 POP 조작 이벤트 |
| 물류 흐름 | 파란 점선 (기존 `umap-route`) | 진행 중 운송 작업 |
| **정보 흐름** | 다른 색 점선 (현장 → 사무실) | 부적합 등록 이벤트 |
| 사무실(QMS) | 평면도 **바깥** 별도 박스 + 대기건수 배지 | MRB 대기 건수 |

**이동을 그리지 않는다.** 사람 위치는 "마지막으로 POP를 조작한 단말"이며 그 이상을 주장하지 않는다.

---

## P8. factory 이벤트 정합성 + 실적 반영

> **왜 먼저인가:** D2·D3·D4가 남아 있으면 그 위에 얹는 OEE 숫자가 전부 틀린다.
> 계산기를 만들기 전에 입력을 고친다.

### 작업

1. **`occurred_at` 도입 (D3)**
   - Flyway **`V4__factory_event_occurred_at.sql`** (V3까지 이미 사용 중): `factory_events`에
     `occurred_at timestamp` 추가, 기존 행은 `created_at`으로 백필 후 `not null`.
   - `FactoryEvent`에 필드 추가, `FactoryEventService.record(...)`가 받도록 시그니처 확장.
   - `MqttMessageHandler`가 payload의 `ts`를 파싱해 전달. **파싱 실패 시에만** `now()` 폴백 + WARN 로그.
   - `FactoryEventResponse`/대시보드 `FactoryEventRaw`를 `occurredAt`으로 통일
     → `api.ts`의 `toTimeline()` 변환 함수 제거 가능.

2. **사이클 → 작업지시 실적 반영 (D2)**
   - 시뮬레이터가 어느 작업지시를 수행 중인지 알아야 한다. **간접 방식을 쓴다:**
     `handleCycle()`에서 해당 `equipmentId`의 `IN_PROGRESS` 작업지시를 조회해 붙인다.
     (시뮬레이터에 작업지시 개념을 넣지 않는다 — 시뮬레이터는 설비만 흉내내는 게 맞다.)
   - 찾으면 `producedQty` +1, `defect=true`면 `defectQty` +1, 이벤트에 `workOrderId`/`lotNo` 기록.
   - 못 찾으면 지금처럼 설비 단위로만 기록 (계획 외 생산으로 취급, WARN 없이 조용히).
   - `WorkOrder`에 `recordCycle(boolean defect)` 도메인 메서드 추가 — 서비스에서 setter 남발 금지.

3. **인덱스 추가 (D4)**
   - `create index idx_factory_events_target_type_time on factory_events (target_type, target_id, event_type, occurred_at desc);`

4. **시뮬레이터를 설비 8대로 확장 (D13)**
   - `FactorySimulator.EQUIPMENTS`에 `CNC-03`·`ASM-01`·`ASM-02`·`INS-01`·`PKG-01` 추가.
     표준CT는 V3 시드값과 맞춘다 (30000 / 25000 / 25000 / 20000 / 15000).
   - `lineCode`는 ASM·INS·PKG가 `LINE-2`다. 토픽이 `factory/{lineCode}/{equipmentCode}/{kind}` 이므로 틀리면 안 된다.

5. **MQTT 유실·상태 정합성 (D8, D9)**
   - `mosquitto.conf`: `persistence true` + `persistence_location`, docker volume 연결.
   - `MqttEventSubscriber`: `options.setCleanSession(true)` → `false`.
     (clientId는 이미 `mqtt.client-id: oee-service`로 고정돼 있어 그대로 두면 된다.)
   - `FactorySimulator`: 접속 시 LWT 설정
     → 토픽 `factory/{line}/{eq}/status`, payload `{"status":"DOWN","reason":"DISCONNECTED"}`.
   - `FactorySimulator.publishStatus()`: `message.setRetained(true)`.
     (cycle은 retained 금지 — 재접속마다 유령 사이클이 한 개 더 잡힌다.)

   > **원안이 그대로는 불가능했던 부분 — LWT는 접속당 하나뿐이다.**
   > 시뮬레이터가 클라이언트 하나로 설비 8대를 발행하고 있었으므로, `factory/{line}/{eq}/status`에
   > 유언을 걸면 8대 중 한 대만 걸린다. → **설비마다 별개로 접속**하게 바꿨다
   > (clientId `simulator-{equipmentCode}`). 실제 현장에서도 설비마다 자기 장치가 붙으므로
   > 도메인에도 맞고, 유언이 설계대로 설비 단위로 동작한다.
   >
   > 유언 payload에는 **`ts`를 넣지 않는다.** 접속 시점에 브로커에 맡겨 두는 고정 문구라서
   > 발행 시각을 미리 박으면 실제 죽은 시각과 무관한 값이 된다. 서버가 수신 시각으로 폴백한다.
   >
   > 정상 종료면 유언이 발행되지 않으므로 설비가 마지막 `RUNNING`으로 남는다.
   > 종료 훅에서 `IDLE`(`SIMULATOR_STOPPED`)을 남겨 "고장"과 "멈춤"을 구분한다.

### 완료 기준

- [x] 설비 8대 전부에서 cycle/status 이벤트가 들어온다 — 8개 설비, 72,862건
- [x] 시뮬레이터를 5분 돌린 뒤 `work_orders.produced_qty > 0`, `defect_qty > 0` — 8건 전부 실적/불량 집계됨
- [x] `factory_events.occurred_at`이 payload의 `ts`와 일치 — UTC→KST 변환 후 일치 확인, 적재 지연 17~43ms
- [x] oee-service를 죽였다 살린 뒤, 다운타임 동안 발행된 사이클이 DB에 들어와 있다 —
      70초 다운타임 중 215건 발생, 213건이 재접속 후 적재(최대 94초 지연), 유실 0
- [x] 시뮬레이터를 강제 종료하면 해당 설비가 몇 초 내 `DOWN`으로 바뀐다 — SIGKILL 12초 내 8대 전부 DOWN
- [x] oee-service만 재기동해도 설비 상태가 즉시 복원된다 (retained) —
      DB를 전부 IDLE로 오염시킨 뒤 oee-service만 재기동 → 8대 전부 RUNNING 복원

### 주의

- `occurred_at` 추가는 **기존 이벤트 조회 API의 응답 필드가 바뀌는 변경**이다. 대시보드를 같은 커밋에서 함께 고칠 것.
- 고정 clientId를 쓰면 같은 id로 두 인스턴스를 띄울 수 없다. 로컬 다중 기동 시 환경변수로 suffix를 받게 할 것.

### 검증에서 잡힌 것 두 가지 (둘 다 이 단계의 핵심)

**1. `cleanSession=false` 가 Paho 콜백 교착을 드러냈다.**
`connectComplete()` 안에서 블로킹 `subscribe()`를 호출하고 있었다. SUBACK을 처리해야 할
주체가 바로 그 콜백 스레드라서 서로를 기다린다. `cleanSession=true` 일 때는 접속 직후
밀려올 메시지가 없어 SUBACK이 먼저 도착해 **우연히** 넘어갔는데, `false`로 바꾸자 브로커가
백로그를 동시에 밀어넣어 콜백 큐가 먼저 차고 **수신이 1건 처리 후 완전히 멈췄다.**
스레드 덤프로 확인(`MQTT Rec` → `CommsCallback.messageArrived`, `MQTT Call` →
`Token.waitForResponse`). → 구독을 전용 스레드로 옮겼다. fleet 구독자도 같은 패턴이라 함께 고쳤다.

**2. `max_queued_messages` 기본값 1000이 조용히 버린다.**
7분 다운타임 뒤 백로그가 배출되긴 했으나 417초 중 53초가 비어 있었다. 브로커 로그에
`Outgoing messages are being dropped for client oee-service` 만 남는다 — 앱에는 아무 신호가 없다.
→ 100000으로 올렸다(현재 발행률로 약 7시간치). **유한 버퍼임을 잊지 말 것** —
그보다 긴 장애는 여전히 유실되므로 그때는 이벤트를 다시 받을 다른 경로가 필요하다.

---

## P9. OEE 계산 엔진

> pixel-factory `CLAUDE.md`의 **Phase 2**에 해당. 새 패키지 `com.pixelfactory.oee`.

### 작업

1. **계획가동시간 정의 (D5)**
   - `EquipmentStatus`에 `SETUP`, `PLANNED_STOP` 추가.
     → `mqtt-topics.md`의 status 허용값도 함께 갱신 (계약 문서와 코드가 어긋나면 안 된다).
   - `shift_calendars` 테이블: `line_id`, `shift_code`, `start_time`, `end_time`, `break_minutes`.
     시드는 2교대(주간 08:00–17:00 / 야간 20:00–05:00, 휴식 60분) 정도면 충분.
   - **계획정지 정의를 문서로 못박는다:** `PLANNED_STOP`·휴식은 계획가동시간에서 제외,
     `SETUP`·`DOWN`은 포함(=비계획 정지로 A를 깎음). `IDLE`은 **비계획 유휴**로 간주.

2. **구간(interval) 변환기**
   - `EquipmentStateInterval` — 상태 이벤트 스트림을 `(equipmentId, status, from, to)` 구간으로 변환.
   - **캐리인 필수:** 조회 시작 시각 이전의 마지막 `EQUIPMENT_STATUS_CHANGED`를 1건 끌어와
     첫 구간의 시작으로 삼는다. 이걸 빼먹으면 조회 구간 앞부분이 통째로 비어 A가 부풀려진다.
   - 시프트 경계에 걸친 구간은 잘라서 양쪽에 배분한다.

3. **계산기**
   ```
   A = 실가동시간 / 계획가동시간
   P = (표준CT × 총생산수) / 실가동시간
   Q = 양품수 / 총생산수
   OEE = A × P × Q
   ```
   - `P > 1.0`이면 **클램프하지 말고** 결과에 `performanceAnomaly=true` 플래그를 실어 올린다.
     (표준CT가 틀렸다는 신호를 숨기면 안 된다.)
   - 집계 단위: 설비 / 라인 / 시프트. **라인 OEE는 설비 OEE의 평균이 아니다** —
     라인 단위 카운트로 다시 계산하거나 병목 설비 기준으로 잡고, 어느 쪽인지 코드 주석에 남긴다.

4. **API**
   - `GET /api/oee/equipments/{code}?from=&to=`
   - `GET /api/oee/lines/{code}?from=&to=`
   - `GET /api/oee/current` — 현재 시프트 기준 전 설비 요약 (대시보드용)

### 완료 기준

- [x] 단위 테스트: 손계산과 일치 — A 89.6%, P 92.6%, Q 96.8%, OEE 80.2%
- [x] 캐리인 테스트: 조회 구간 이전에 시작된 DOWN 구간이 A에 반영된다
- [x] 시프트 경계 분할 테스트
- [x] `P > 1.0` 상황에서 값이 잘리지 않고 플래그가 선다

테스트 20개 전부 통과(`OeeCalculatorTest` 5 · `ShiftWindowResolverTest` 10 · `StateIntervalAssemblerTest` 5).
런타임: 설비/라인/current 3개 엔드포인트 동작, 휴식 시간대·교대 밖 조회 → 계획가동 0, 없는 설비 → 404.

### 구현 중 갈라진 것 — 휴식은 "총 분"이 아니라 "시각"이어야 한다

원안의 `shift_calendars.break_minutes`(총 분)로는 **A를 올바르게 계산할 수 없다.**
분모에서는 뺄 수 있어도 분자(RUNNING 구간)에서는 뺄 수가 없다 — 언제가 휴식인지 모르니까.
그대로 계산하니 휴식 중에도 돌아간 설비가 실가동 > 계획가동이 되어 **A가 109%로 나왔다.**

→ `break_start`/`break_end` 시각으로 바꿨다. 교대에서 휴식을 뺀 **생산 창(production window)**
을 만들고, 계획가동시간과 실가동시간을 **모두 그 창 안에서** 잰다. 그러면 실가동이 계획을
넘는 일이 구조적으로 불가능하다(비례 배분 같은 보정도 필요 없다).

### 시뮬레이터와 OEE의 시간 기준을 하나로 (✅ 해결 — V6)

처음엔 **P가 250~650%로 나왔다.** `SIM_SPEED=10`이라 시뮬레이터가 "30초 사이클"이라면서
3초마다 발행했기 때문이다. 실시간(`occurred_at`) 기준으로는 표준CT가 허용하는 양의 10배를
낸 셈이라 P가 그만큼 커진다. 엔진 버그가 아니라 데모의 시계가 두 개였던 것이고,
`performanceAnomaly` 플래그는 **정상 동작**이었다(표준CT와 실제가 안 맞는다고 알림).

**선택: 데모 공장을 "빠른 공장"으로 정의한다.** 표준CT를 1/10로 압축하고(V6) 시뮬레이터도
같은 값을 쓰며 `SIM_SPEED`를 1로 되돌렸다. 시계가 하나가 되어 P가 제자리로 오고, 발행 주기가
전과 같아 지도의 활기도 그대로다. 고장 지속시간(15~45초 → 1.5~4.5초)도 같은 시계로 맞췄다.

- **대가:** 설비 스펙이 "CNC 선반 3초 사이클"로 비현실적이다. 시간을 압축한 데모라는 뜻이며,
  실제 값으로 돌리려면 V6 값과 시뮬레이터 `EQUIPMENTS`를 10배로 되돌리면 된다(사이클 30초).
- **함정:** 마스터와 시뮬레이터 값이 **반드시 같아야** 한다. 어긋나면 P가 그 비율만큼 틀어진다.
  `SIM_SPEED`를 다시 올리면 같은 문제가 재발한다 — 배포 가이드에 넣지 말라고 적어 뒀다.

검증(새 시계 90초 구간): P 82~91%. 시뮬레이터가 사이클타임을 표준의 0.9~1.3배(평균 1.1배)로
흔들므로 기대값은 1/1.1 = **90.9%** — 관측값과 맞는다. 교대 전체로는 A 97%, Q 97%, **OEE 62%**
(제조업 평균대가 60% 근처라 데모 숫자로도 자연스럽다). `P > 1.0` 플래그 0건, `A > 100%` 0건.

### 주의

- **표준CT는 품번 단위가 맞다(D6).** 다만 `items` 테이블 신설은 P13(WMS)에서 품목 마스터를 만들 때 함께 한다.
  P9는 `IdealCycleTimeProvider.idealCycleTimeMs(equipmentId, itemId)` 인터페이스로 받아 뒀으므로
  **구현만 갈아끼우면** 되고 계산기는 손대지 않는다.
  P9에서는 설비 고정값을 쓰되, **계산기 인터페이스는 `idealCycleTimeMs(equipmentId, itemId)` 형태로 받아두어**
  나중에 구현만 갈아끼우면 되게 한다.

---

## P10. factory 실시간 push + 대시보드 연결

> **D1 해소.** 데모에서 가장 먼저 눈에 띄는 결함이 여기서 사라진다.

### 작업

1. **factory에 WebSocket 추가** — fleet의 `realtime` 패키지를 참고하되 **복사하지 말 것.**
   - fleet은 Redis Pub/Sub로 팬아웃한다(다중 인스턴스 대비). factory는 Redis를 안 쓰므로
     `@TransactionalEventListener(AFTER_COMMIT)` + `SimpMessagingTemplate` 직접 발행으로 충분하다.
     **왜 다르게 했는지 클래스 주석에 남긴다.**
   - 토픽: `/topic/factory/equipments`, `/topic/factory/events`, `/topic/factory/oee`

2. **게이트웨이 경로 분리 (D11)** — 지금 `/ws/**`가 통째로 fleet으로 간다.
   ```
   pixel-fleet-ws    : Path=/ws/fleet/**    → 9002
   pixel-factory-ws  : Path=/ws/factory/**  → 9001
   ```
   - fleet `WebSocketConfig`의 엔드포인트를 `/ws/fleet`으로, factory는 `/ws/factory`로.
   - 대시보드 `useFleetSocket`의 `new SockJS('/ws')`도 함께 수정.
   - **SockJS는 `/info`·XHR 폴백까지 같은 prefix로 나가므로 라우트가 하위 경로 전체를 잡아야 한다.**
     `uri`는 계속 `http://`로 둘 것 (기존 주석의 이유 그대로).

3. **대시보드 훅 정리**
   - `useFleetSocket` → `usePlatformSocket`으로 일반화하거나, `useFactorySocket`을 나란히 추가.
     연결 상태 표시(`실시간 연결됨` pill)는 **두 연결을 AND로** 판단하도록 수정.
   - `Dashboard.tsx`에서 설비/이벤트/OEE를 push로 갱신.

4. **Overview KPI를 OEE로 교체**
   - `OverviewView`의 PixelFactory 패널을 `가동 설비 / OEE / A·P·Q / 고장`으로.
   - 품질률 자체 계산 로직 제거하고 P9 API 값을 쓴다.

### 완료 기준

- [x] 시뮬레이터가 고장을 내면 **새로고침 없이** 지도 설비가 빨강으로 바뀐다 —
      브라우저에서 200ms 간격으로 색을 기록해 `#e0392b` 관측(고장이 1.5~4.5초라 띄엄띄엄
      샘플링하면 창을 놓친다. 실제로 처음엔 놓쳤다)
- [x] Overview의 OEE 숫자가 시간에 따라 움직인다 — 같은 페이지 인스턴스에서 P 71.8% → 71.9%,
      타임라인 갱신 확인(리로드 없음을 `window` 상태 생존으로 증명)
- [x] 게이트웨이 경유(9000)로 factory·fleet **두 WebSocket이 동시에** 살아 있다 —
      `/ws/fleet/info` 200, `/ws/factory/info` 200, 옛 경로 `/ws/info` 404
- [x] 한쪽 서비스를 죽여도 다른 쪽 실시간이 유지된다 — factory만 정지 →
      pill이 `일부 연결 (물류만)`으로 바뀌고 로봇 좌표는 계속 갱신, OEE는 `—`.
      factory 복귀 후 새로고침 없이 `실시간 연결됨` + OEE 값 복귀

### 구현 판단

- **OEE만 주기 push(5초), 상태·이벤트는 즉시 push.** OEE는 이벤트 하나로 정해지지 않고 구간
  전체를 재집계해야 나온다 — 사이클이 초당 여러 건 들어오는데 그때마다 8대를 재계산하면 DB만
  두드리고 화면은 사람 눈에 똑같다. 반대로 고장은 즉시 떠야 관제의 의미가 있다.
- **연결 상태는 두 소켓의 AND.** 하나만 살아 있는데 초록 불이면 화면 절반이 멈춘 걸 모른 채
  보게 되므로, 어느 쪽이 끊겼는지(`일부 연결 (물류만)`) 표시한다.
- **대시보드는 OEE를 다시 계산하지 않는다.** 서버가 이벤트에서 계산한 값을 그대로 쓴다.
  공장 전체 값은 설비별 값의 평균이 아니라 합산으로 만든다(서버 `ofLine`과 같은 원칙).
- `useFleetSocket` → `usePlatformSocket(endpoint, topics)`로 일반화하고 옛 훅은 지웠다.

### 주의

- `/ws` 경로를 바꾸는 순간 기존 fleet 대시보드가 조용히 끊긴다. **양쪽을 같은 커밋에서 바꿀 것.**
- 시뮬레이터 파라미터가 지금 그대로면 OEE가 86% 근처에 붙어 거의 안 움직인다
  (`DEFECT_RATE 0.03`, `BREAKDOWN_RATE 0.02`, CT 편차 0.9~1.3배 → A≈98% · P≈91% · Q≈97%).
  P10 검증 시 `BREAKDOWN_RATE`를 일시적으로 올려 변화를 확인하고, 정식 시나리오는 P15에서 잡는다.

---

## P11. 레이아웃 서버화

> **D7 해소.** POP 단말·사무실을 얹기 전에 반드시 끝내야 한다. 안 하면 좌표 중복이 5종류가 된다.

### 작업

1. **좌표를 마스터로 승격**
   - `equipments`에 `pos_x`, `pos_y` 추가 (`types.ts`의 `EQUIPMENT_POSITIONS` 값을 그대로 시드).
   - `layout_nodes` 테이블 (`node_code`, `name`, `node_type`, `pos_x`, `pos_y`) —
     `LocationRegistry`의 12개 노드를 옮긴다.
   - **소유권 결정:** 평면도는 공장의 것이지 물류만의 것이 아니다.
     → **factory가 레이아웃 마스터를 소유**하고, fleet은 필요한 노드 좌표를 REST로 받아 캐시한다.
     (원칙 3 DB per module을 지키면서 중복을 없애는 유일한 방법)

2. **API** — `GET /api/factory/layout`
   ```json
   { "width": 44, "height": 24, "aisleY": 12,
     "nodes": [...], "equipments": [...], "terminals": [] }
   ```

3. **소비처 3곳 정리**
   - dashboard `types.ts`: 하드코딩 상수 제거, 부팅 시 layout API 조회.
   - control-service `LocationRegistry`: 기동 시 factory에서 받아 캐시 + 실패 시 폴백 로그.
   - robot-sim `NodeMap`: **여기는 그대로 둔다.** 시뮬레이터는 물리 세계를 흉내내는 쪽이라
     서버 마스터에 의존하면 안 된다. 대신 **불일치 검증 테스트**를 붙여 값이 어긋나면 빌드가 깨지게 한다.

4. `LocationRegistry` / `types.ts`의 "중복은 BACKLOG" 주석 제거.

### 완료 기준

- [x] `types.ts`에 좌표 상수가 남아 있지 않다 — `MAP_W/H`·`NODES`·`EQUIPMENT_POSITIONS`·
      통로 y 전부 제거. 남은 건 타입 정의와 `routePoints(layout, …)`(규칙은 코드, 값은 서버)
- [x] layout API 응답만 바꿔서 설비 위치를 옮길 수 있다 (프론트 재빌드 없이) —
      DB에서 `CNC-01.pos_x` 11→6 만 바꾸고 지도가 x=6 으로 이동하는 것을 확인(프론트 무변경)
- [x] robot-sim ↔ 서버 좌표 불일치 시 테스트가 실패한다 —
      `STATION-A2`를 18→19 로 틀리게 만들어 **빌드가 실제로 깨지는지** 확인.
      실패 메시지가 어느 노드가 어떻게 다른지 지목한다(`서버 마스터=[18.0, 5.5], robot-sim=[19.0, 5.5]`)

### 소유권 결정 — factory가 평면도를 소유한다

평면도는 공장의 것이지 물류만의 것이 아니다. 설비·하역 지점·(P12의) POP 단말이 모두 같은
바닥 위에 있다. 그래서 `layout_nodes`·`layout_settings`·`equipments.pos_*`를 factory가 갖고,
소비처는 각자 다르게 붙는다.

| 소비처 | 방식 | 왜 |
|---|---|---|
| 대시보드 | API로만 (`GET /api/factory/layout` + 설비 좌표는 `Equipment`에 실려 온다) | 화면은 서버를 따라야 한다 |
| control-service | 기동 시 + 5분 주기로 받아 캐시, 실패 시 폴백 + WARN | factory가 죽어도 배차는 계속돼야 한다 |
| robot-sim | **받지 않는다.** 자기 좌표를 갖고 대조 테스트로 검증 | 시뮬레이터는 물리 세계를 흉내내는 쪽이다 — 실제 설비는 서버가 알려주는 대로 위치를 바꾸지 않는다 |

설비 좌표를 layout 응답이 아니라 `EquipmentResponse`에 실은 이유: 설비는 이미 실시간 채널로
흐르므로 위치를 바꿔도 **같은 경로로 함께** 갱신된다. 대시보드가 두 출처를 조합할 필요가 없다.

### 남은 것 — `LaneGraph`의 통로 y·연결로 x

`traffic/LaneGraph`는 통로 y와 연결로 x를 `static final`로 갖고 그 값으로 구간 이름까지 만든다.
동적으로 바꾸려면 교통 통제 전체를 건드려야 해서 범위에 넣지 않았다. 대신 `LocationRegistry`가
서버 평면도를 받을 때 **통로 y가 다르면 ERROR 로그**를 남긴다 — 조용히 어긋나면 그린 선과 실제
주행이 갈리고 구간 점유가 엉킨다. 연결로 x는 아직 마스터가 없다(레인망은 factory가 모르는
물류 개념이라 fleet이 소유하는 게 맞을 수 있는데, 그러면 소유권 경계가 미묘해진다 — 결정 필요).

### 검증 중 걸린 것 — `numeric` vs `Double`

마이그레이션을 `numeric(6,2)`로 쓰고 엔티티는 `Double`로 뒀더니 `ddl-auto: validate`가
기동을 막았다(`found [numeric], but expecting [float(53)]`). 좌표는 기하값이라
`double precision`이 맞다. `posX`→`posx` 함정과 같은 계열 — **스키마와 엔티티가 정확히
같아야 하고, 어긋나면 컴파일이 아니라 기동에서 터진다.**

> 마이그레이션을 고친 뒤 **jar를 다시 빌드**해야 한다. SQL은 jar 안의 리소스라서, 파일만
> 고치고 재기동하면 예전 SQL이 그대로 돈다(한 번 헛돌았다).

---

## P12. ~~중앙 인증(P6)~~ + POP 단말 + 역할별 뷰

> **MES가 MES다워지는 단계.** D10(인증)은 12-1에서 이미 해소됐다.

### 12-1. 게이트웨이 중앙 인증 (기존 계획서 P6) ✅ 완료 — 순서를 앞당겨 먼저 했다

구현은 `platform/gateway/.../auth/AuthenticationGlobalFilter`. 상세는
[platform/gateway/README.md](../platform/gateway/README.md).

- JWT 검증을 게이트웨이 필터로 이동. 검증 결과를 `X-Auth-User`/`X-Auth-Role`로 전달하고,
  **클라이언트가 보낸 `X-Auth-*`는 항상 제거**한다(스푸핑 차단).
- `api.ts`의 `loginAll()`/토큰 2벌 제거 → 단일 토큰(`pp_token`). 세 서비스가 같은
  `PLATFORM_JWT_SECRET`을 쓴다.

> **원안과 달라진 점 — 모듈의 자체 JWT 필터를 제거하지 않고 유지했다.**
> 원안은 "모듈은 헤더를 신뢰하고 자체 필터 제거 + 네트워크 격리/공유 시크릿으로 우회 차단"이었다.
> 대신 모듈이 계속 JWT를 검증하게 두면(방어 심층) **추가 장치 없이** 우회 차단이 성립한다 —
> 9002에 직접 붙어도, `X-Auth-Role: ADMIN`을 위조해도 401이다(실측 확인).
> 게이트웨이 필터를 붙였다가 모듈 필터를 떼는 2단계 위험(§주의)도 사라진다.
>
> 발급은 여전히 모듈이 한다. 게이트웨이는 사용자 저장소가 없어 토큰을 만들 수 없어서,
> `/api/auth/**`를 `AUTH_MODULE_URI`(현재 pixel-factory)로 넘긴다.
> 전용 인증 서비스를 만들면 이 변수만 돌리면 된다.

**남은 것:** `/ws/**`는 아직 인증 없이 통과한다(SockJS 핸드셰이크에 Authorization 헤더를
실을 수 없어서). 토큰을 STOMP CONNECT 프레임에 담아 검증해야 한다. 권한(Role) 기반
접근 제어도 아직 모듈 몫이다 — 게이트웨이는 "인증됐는가"만 본다.

### 12-2. POP 단말

- `SourceType`·`TargetType`에 `TERMINAL` 추가.
- 테이블:
  ```sql
  create table pop_terminals (
      id bigserial primary key,
      terminal_code varchar(30) not null unique,   -- POP-A1
      name          varchar(50) not null,
      line_id       bigint not null references production_lines (id),
      pos_x numeric(6,2) not null,
      pos_y numeric(6,2) not null,
      created_at timestamp not null,
      updated_at timestamp not null
  );
  ```
  시드: LINE-1에 `POP-A1`, LINE-2에 `POP-B1` (설비 여러 대당 단말 1대가 현실적).
- **POP 화면** — 대시보드 안의 별도 라우트(`/pop/{terminalCode}`), 터치 친화적 큰 버튼.
  기능은 최소로: 로그인 → 내 작업지시 목록 → 착수 → 실적/불량 입력 → 종료.
- 착수 시 `WORK_ORDER_STARTED` 이벤트에 `sourceType=TERMINAL, sourceId=terminalId` 기록.
- **사용자 현재 위치 = 그 사람의 가장 최근 TERMINAL 소스 이벤트의 단말.** 파생값이며 저장하지 않는다.
- **stale 처리 (빼먹지 말 것):**
  - 작업지시 종료 시 배지 제거
  - 마지막 조작 후 N분(기본 30분) 경과 시 흐리게 → 이후 제거
  - `pop.presence.timeout-minutes` 로 설정화

### 12-3. 지도 표현

- 키오스크 마커(세로 직사각) + 사용 중이면 하단에 `담당자명 · WO번호` 배지.
- **사람 독립 마커·이동 애니메이션 금지** (§1 지도 시각 규칙).
- **레이어 토글 추가** — `설비 / AMR / 운송경로 / POP·작업자`. 지도 밀도가 이미 빠듯하다.

### 12-4. 역할별 뷰

`UserRole`에 `ADMIN`/`OPERATOR`/`INSPECTOR`/`DISPATCHER`가 이미 있다(`shared/.../UserRole.java`).

| 역할 | 진입 화면 | 보이는 것 |
|---|---|---|
| ADMIN | 통합 현황 | 전체 |
| OPERATOR | POP | 내 작업지시만 |
| INSPECTOR | 검사 대기 목록 | 내 검사 건만 (P15에서 채워짐) |
| DISPATCHER | Fleet 관제 | 로봇·운송작업 |

### 완료 기준

- [x] 로그인 1회로 factory·fleet 양쪽 API가 통과한다 (localStorage 토큰 1개)
- [x] 게이트웨이를 우회한 모듈 직접 호출이 거부된다
- [ ] POP에서 착수하면 통합 지도의 해당 키오스크에 담당자 배지가 뜬다
- [ ] 작업 종료 또는 타임아웃 후 배지가 사라진다
- [ ] `operator` 계정으로 로그인하면 관제 화면에 접근할 수 없다

### 주의

- ~~12-1은 잘못하면 전 API가 막힌다~~ → 모듈 필터를 유지하는 방식으로 갈아서 이 위험은 사라졌다.
  대신 **`PLATFORM_JWT_SECRET`이 세 서비스에 같은 값이어야 한다.** 다르면 로그인은 되는데
  이후 전부 401이라 증상이 "로그인이 안 된다"로 보여 원인을 찾기 어렵다.

---

## P13. WMS 모듈 (pixel-wms, :9003)

> **목표: 로봇이 왜 움직이는지에 답을 준다.** 지금은 `DemoTaskGenerator`가 랜덤으로 작업을 만든다.

### 작업

1. 모듈 스캐폴딩 — `modules/pixel-wms/services/wms-service/`, `shared/` 의존, 자체 DB `pixelwms`,
   Flyway, 포트 9003. 게이트웨이 라우트 `/api/wms/**`.
2. 도메인
   - `items` — **품목 마스터. 표준CT(품번×공정)를 여기서 관리한다(D6 해소).**
   - `locations` — 창고 로케이션 (`WAREHOUSE`, `SHIPPING` 등 layout_nodes와 코드 정합)
   - `stocks` — 로케이션 × 품목 × 수량
   - `inbound_orders` / `outbound_orders` — 입출고 지시
   - `stock_movements` — 이동 이력 (이벤트 소싱)
3. **fleet 연동** — WMS가 출고지시를 만들면 fleet에 운송 작업을 요청한다.
   `POST /api/fleet/tasks` (게이트웨이 경유, REST). 운송 완료 이벤트를 받아 재고를 차감한다.
   - 완료 통지 수신 방식은 **MQTT 구독**을 권한다 (fleet이 WMS를 몰라야 하므로).
   - `DemoTaskGenerator`에는 이미 `@ConditionalOnProperty("demo.task-generator.enabled")`가 붙어 있다.
     **코드를 지우지 말고** WMS를 함께 띄우는 구성에서만 `false`로 둔다 — fleet 단독 데모를 살려두기 위해서다.
4. **factory 연동** — 작업지시 착수 시 필요한 자재를 WMS에 요청.

### 완료 기준

- [ ] WMS에서 출고지시를 만들면 fleet에 운송 작업이 생기고 AMR이 실제로 움직인다
- [ ] 운송 완료 후 WMS 재고가 차감된다
- [ ] `items` 기반 표준CT가 P9 OEE 계산기에 주입된다 (설비 고정값 제거)
- [ ] WMS를 내려도 fleet·factory는 정상 동작한다 (컴포저블 검증)

---

## P14. QMS 모듈 (pixel-qms, :9004) + MRB + Outbox

> **컴포저블 아키텍처를 화면으로 증명하는 단계.**

### 작업

1. 모듈 스캐폴딩 — 포트 9004, 자체 DB `pixelqms`, 라우트 `/api/qms/**`.
2. 도메인
   - `inspections` — 검사 (수입/공정/최종), 검사원, 로트, 판정
   - `defect_types` — 불량 유형 마스터
   - `nonconformances` (NCR) — 부적합
   - `mrb_reviews` — 심의. **상태머신:**
     ```
     RAISED → UNDER_REVIEW → DECIDED → CLOSED
     판정: USE_AS_IS(특채) / REWORK(재작업) / SCRAP(폐기) / RETURN(반품)
     ```
3. **factory 연동 (여기가 핵심)**
   - factory가 불량 임계 초과 시 `INSPECTION_REQUESTED` 발행 → QMS가 검사 생성
   - QMS가 MRB를 열면 → factory의 해당 설비를 `QUALITY_HOLD`, 작업지시를 `ON_HOLD`로
   - MRB 판정 완료 → 홀드 해제
   - **결과: 지도의 설비가 주황으로 변했다가 돌아온다. 별개 서비스·별개 DB가 계약만으로 연동되는 게 눈에 보인다.**
   - `EquipmentStatus.QUALITY_HOLD`와 `WorkOrderStatus.ON_HOLD`가 이미 있는데 지금 아무도 안 쓴다. 여기서 채운다.
4. **사무실 + 정보 흐름 (지도)**
   - 평면도 바깥 우측 상단에 "품질관리실" 박스, MRB 대기 건수 배지.
   - 부적합 발생 시 현장 설비 → 사무실로 점선(운송 경로선과 **다른 색**). 기존 `umap-route` 표현 재사용.
   - 레이어 토글에 `품질 흐름` 추가.
5. **Outbox (메일 UI)**
   - **실제 SMTP를 붙이지 않는다.** Railway에서 포트가 막히고, 스팸 처리되고, 데모에서 재현이 안 된다.
   - `notifications` 테이블 + 미사용이던 `FactoryEventType.NOTIFICATION_SENT` 를 여기서 사용(D12).
   - 대시보드에 "발송함" 뷰 — 수신자/제목/본문/발송시각을 메일 카드로 렌더.
   - **확장점 설계:** `NotificationSender` 인터페이스 + `OutboxSender`(기본) / `SmtpSender`(프로필 전환).
     실제 발송보다 이 설계가 더 나은 시그널이다.
   - MRB 등록 시 자동 발송 예시:
     ```
     수신: 품질관리팀 <quality@…>
     제목: [MRB] LOT-2026-0712 부적합 심의 요청
     본문: 설비 CNC-01 / 불량 12EA / 유형: 치수불량 / 요청자: …
     ```
6. `INSPECTION_STARTED/PASSED/FAILED` enum 3종을 실제로 사용(D12).

### 완료 기준

- [ ] 불량이 임계를 넘으면 QMS에 검사가 자동 생성된다
- [ ] MRB를 열면 통합 지도의 해당 설비가 `QUALITY_HOLD`(주황)로 바뀐다
- [ ] 판정 완료 시 홀드가 풀리고 색이 복귀한다
- [ ] 발송함에 메일 카드가 쌓이고 클릭해서 본문을 볼 수 있다
- [ ] `inspector` 계정으로 로그인하면 검사 대기 목록이 진입 화면이다
- [ ] QMS를 내려도 factory는 정상 생산한다 (홀드 요청만 안 올 뿐)

---

## P15. 통합 시나리오 + 배포

> **네 시스템을 한 바퀴로 엮어 "공장이 돌아간다"를 보여준다.**

### 목표 사이클

```
WMS 출고지시 → AMR 자재 운송 → POP에서 작업자 착수 → 설비 가공(OEE 집계)
     ↑                                                        ↓
WMS 재고 반영 ← AMR이 출하장으로 ← QMS 합격 판정 ← 불량 감지 → NCR → MRB → 메일 → 설비 홀드
```

### 작업

1. **데모 시나리오 러너** — 위 사이클이 자동으로 순환하도록 오케스트레이션.
   시연 중 특정 이벤트(고장·부적합)를 **버튼으로 주입**할 수 있게 한다(면접 시연용).
2. **시뮬레이터 파라미터 재조정** — 지금은 OEE가 86% 근처에 고정이라 그래프가 평평하다.
   `SETUP`/`PLANNED_STOP` 구간을 시나리오에 넣어 A가 실제로 움직이게 만든다.
   교대 시작 SETUP → RUNNING → 계획정지 → 고장 → 복귀.
3. **랜딩 페이지** — 4개 시스템 소개 + 데모 계정 안내 + 아키텍처 다이어그램.
   포트폴리오 방문자는 로그인 화면부터 만나면 안 된다.
4. **Railway 배포** — gateway + factory + fleet + wms + qms + Postgres + Redis + Mosquitto.
   - `FactoryEvent`/`FleetEvent`는 계속 쌓인다. **이벤트 보존 정책(예: 7일 이후 삭제)을 반드시 넣을 것** — Railway 사용량 이슈.
   - `mosquitto.conf`의 `allow_anonymous true`를 계정 인증으로 교체 (파일의 TODO).
   - 게이트웨이 CORS `allowedOriginPatterns: "*"` 를 실제 오리진으로 제한 (yml의 TODO).
5. **README 갱신** — 구성 표, 포트 규약(9003·9004 추가), 모듈별 문서 링크.

### 완료 기준

- [ ] 배포 URL에서 데모 계정으로 전체 사이클이 관찰된다
- [ ] 시연용 이벤트 주입 버튼이 동작한다
- [ ] 24시간 방치 후에도 DB 용량이 통제된다
- [ ] 익명 MQTT 접속이 거부된다

---

## 부록 A. 단계 의존 관계

```
P12-1 (중앙 인증) ✅ 완료 — 순서를 앞당겨 먼저 했다

P8 (이벤트 정합성)
 └─ P9 (OEE 계산)
     └─ P10 (실시간 push)          ← 여기까지가 "지금 화면 살리기"
P11 (레이아웃 서버화)               ← P12 이전 필수
 └─ P12 (POP + 역할뷰)             ← 모듈 추가 이전 필수
     ├─ P13 (WMS)
     │   └─ P9 표준CT 주입 (D6 최종 해소)
     └─ P14 (QMS + MRB)
         └─ P15 (통합 시나리오 + 배포)
```

P11은 P8~P10과 독립이므로 병행 가능하다. **P12는 반드시 P13·P14보다 먼저.**
인증(12-1)이 이미 끝났으므로 P12에 남은 것은 POP 단말과 역할별 뷰다.

## 부록 B. 자주 밟는 함정

| 함정 | 대응 |
|---|---|
| bash `./gradlew` | Windows에서 `-Xmx` 파싱 깨짐 → PowerShell `.\gradlew.bat` |
| 게이트웨이 WS 라우트 `uri`를 `ws://`로 | 업그레이드 아닌 요청이 400 → `http://` 유지 |
| CORS 헤더 중복 | 모듈이 자체 CORS를 붙이면 브라우저가 거부 → `DedupeResponseHeader` 유지 |
| 상태 구간 계산 시 캐리인 누락 | 조회 시작 이전 마지막 상태 이벤트 1건을 반드시 끌어온다 |
| cycle 메시지에 retained 설정 | 재접속마다 유령 사이클 → status만 retained |
| 모듈 간 DB 직접 조회 | 원칙 2 위반 → REST/MQTT 계약으로만 |
| 지도에 없는 데이터를 그림 | 원칙 1 위반 → 사람 이동 애니메이션 금지, 배지로만 |
| **두 서비스에 나뉜 임계값이 어긋남** | 배차 최소 배터리(서버 25%) > 충전 복귀 기준(로봇 20%)이면 그 사이에 빠진 로봇이 영원히 멈춘다. 임계값이 서비스 경계를 넘으면 **불변식을 양쪽 주석에 박아둘 것** |
| `PLATFORM_JWT_SECRET`이 서비스마다 다름 | 로그인은 되는데 이후 전부 401 → 증상이 "로그인이 안 된다"로 보인다 |
| 함대가 멈췄을 때 원인 혼동 | 교통 교착과 배터리 사각지대는 증상이 같다(전원 IDLE). `GET /api/traffic/reservations`가 **비어 있으면** 교통이 아니라 배터리 쪽 |

## 부록 C. 포트 규약 (갱신)

| 포트 | 서비스 |
|---|---|
| 9000 | API Gateway |
| 9001 | pixel-factory (MES) |
| 9002 | pixel-fleet (로봇관제) |
| 9003 | pixel-wms (WMS) |
| 9004 | pixel-qms (QMS) |
| 9200 | 통합 대시보드 (dev) |
| 5432 / 1883 / 6379 | Postgres / Mosquitto / Redis |
