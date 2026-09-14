# P35 설계 문서 — 다공정 라우팅: 공정 순서 · 공정별 작업 · 회차 단위 WIP 이송

> 상태: **설계 초안 (2026-09-07). 승인 후 WP0부터 착수.** `docs/p20~p34-*.md`와 같은 형식.
>
> 선행 문서: `pixel-platform-roadmap.md` 0-A("공정 회차", "완료 판정은 보존법칙") ·
> P33(생산동 구역화 — 가공 A / 조립 B / 품질 Q / 물류 L) · P24(M4형 `orders/create`) ·
> P16(M2M 서비스 토큰, MQTT ACL).
>
> **왜 지금인가.** 창고동은 P28~P32로 렉 912기까지 채워졌는데, MES 회사가 정작 보고 싶은
> 생산동은 "설비 8대가 각자 500개를 누적하는" 단일 공정 그대로다. 작업지시가 **품번의 공정
> 순서를 따라 가공→조립→검사→포장으로 흐르고, 공정 사이의 반제품(WIP)을 로봇이 실제로
> 나르는** 구조로 바꾼다. 로드맵 0-A에 "P13에서 다공정이 생기면 반드시 적용"이라고 적어
> 두고 아직 못 지킨 보존법칙 완료판정과 회차 롤업이 이 문서에서 코드가 된다.

---

## 0. 지금 구조가 왜 다공정을 못 담는가

| 지금 | 근거 | 다공정에서 문제가 되는 이유 |
|---|---|---|
| 작업지시 1건 = 설비 1대 + 품번 1개 | `work_orders.equipment_id`, `WorkOrderDataInitializer`가 설비마다 1건씩 IN_PROGRESS로 깔아 둠 | "ASSY-2001 500개"라는 지시가 CNC-01 것과 ASM-01 것으로 **서로 무관한 두 건**이 된다. 조립 실적이 가공 실적과 이어지지 않는다 |
| `process_id`는 값 1 고정 | `create table processes` 없음, 시더가 `1L` | 공정이라는 개념이 DB에 없다 |
| 실적은 사이클마다 헤더에 `+1` | `WorkOrder.recordCycle` — `producedQty >= plannedQty`면 무시 | 상류 불량으로 흐름이 줄어도 하류는 여전히 계획수량을 기준으로 본다 → 0-A가 경고한 "영원히 미완료" |
| 공정 간 이송은 fleet이 무작위 생성 | `DemoTaskGenerator.FLOWS`의 `PROD-A1→PROD-B1` 등 | 로봇이 움직이는 이유가 실적이 아니라 난수다. P13이 WMS로 "출고가 로봇을 움직이는 이유"를 만든 것과 같은 일을 생산 쪽에서 아직 안 했다 |
| 표준CT는 설비 고정값 | `EquipmentFixedIdealCycleTime`이 유일한 `IdealCycleTimeProvider` (D6) | 같은 설비에서 품번이 바뀌면 P가 틀어진다. 품번×공정이라는 자연스러운 자리가 없어서 못 고쳤다 |

**요약: 지시(무엇을 얼마나) · 공정(어디서 어떤 순서로) · 회차(얼마씩 묶어 넘기나)가 전부
한 행에 뭉쳐 있다.** 이 셋을 분리하는 것이 이번 설계의 전부다.

---

## 1. 목표 구조

```
factory (pixel-factory)                                  fleet (pixel-fleet)
┌───────────────────────────────────────────┐
│ processes           공정 마스터            │
│ part_routings       품번 × 공정 순서 × 설비 │   ← 마스터(Flyway 시드)
│ equipments.station_node_code  설비의 하역 노드 │
├───────────────────────────────────────────┤
│ work_orders          지시 헤더 (품번·수량·LOT)│
│ work_order_operations 공정별 작업 (seq, 설비,│
│                       투입/양품/불량, 상태)  │   ← 실적
│ work_order_rounds     회차 (묶음 단위 WIP)   │
└──────────────┬────────────────────────────┘
               │ 회차 마감(good_qty == round_qty)  → AFTER_COMMIT
               │ POST /api/orders (M4형, svc-factory 토큰)  ──────────▶  운송 주문
               │                                                        (PROD-A1 → PROD-B1)
               │ ◀──── MQTT fleet/tasks/{roundCode}/completed ──────────  완료 통지
               ▼
         회차 ARRIVED → 다음 공정 투입량 += good_qty → 다음 공정 READY
```

세 가지가 새로 생기고, 나머지는 **기존 조각의 재사용**이다.

| 새로 만드는 것 | 재사용하는 것 |
|---|---|
| 공정·라우팅 마스터(WP0) | 품번 마스터 `parts`(V10) — 라우팅은 여기 매달린다 |
| 작업지시 분해 + 회차 + 보존법칙(WP1) | `EquipmentTelemetryService.applyCycle` — 진입점 그대로, 안에서 찾는 대상이 헤더→공정 작업으로 바뀐다 |
| factory→fleet 이송 클라이언트(WP2) | WMS의 `FleetTaskClient` + `ServiceTokenProvider` + `MqttEventSubscriber`(`fleet/tasks/#`) — 패턴을 그대로 옮긴다 |

---

## 2. 핵심 원칙 재확인

| 원칙 | 출처 | 이 설계가 지키는 방법 |
|---|---|---|
| 이벤트가 단일 진실 공급원 | 양쪽 CLAUDE.md | 공정 착수·회차 마감·WIP 발송·도착이 전부 `factory_events`에 남는다(4절 D9). 실적 컬럼은 이벤트에서 파생된 캐시다 |
| 컴포저블 — 모듈 간 코드/DB 직접 참조 금지 | 루트 CLAUDE.md | factory는 fleet DB를 모른다. 운송은 `POST /api/orders` 계약, 완료는 MQTT 토픽. **fleet은 factory의 존재를 계속 모른다**(WMS와 동일) |
| DB per module | 루트 CLAUDE.md | 라우팅·회차는 전부 factory DB. fleet은 `externalId`(회차 코드)만 저장 |
| 시뮬레이터는 설비만 흉내낸다 | 로드맵 P8 결정 | `FactorySimulator`는 **한 줄도 안 바뀐다.** 어떤 작업지시를 만드는지는 서버가 정한다(D7) |
| 게임 메커닉 금지, 없는 데이터를 그리지 않는다 | 로드맵 1절 | 지도의 WIP 흐름선은 실제 fleet 주문에서만 나온다. 회차 코드 라벨도 실제 주문의 `externalId` |
| 부수효과는 본 트랜잭션 밖 | 로드맵 0-A | fleet 호출은 `@TransactionalEventListener(AFTER_COMMIT)` + try-catch. 실패해도 사이클 적재는 커밋된다(D8) |
| 외부 연계 타임아웃 필수 | 로드맵 0-A | connect 3s / read 5s — `LocationRegistry`와 같은 값 |
| 완료 판정은 보존법칙 | 로드맵 0-A | `양품 + 불량 ≥ 투입`. 계획수량이 아니다(D4) |
| 채번은 MAX+1 단독 금지 | 로드맵 0-A | 회차 번호는 공정 작업 행을 잠근 상태에서 `round_count + 1`(D10) |

---

## 3. 확정 결정 (D1 ~ D12 — 이유 포함)

### D1. 라우팅 마스터는 factory가 소유한다 (`processes` + `part_routings`)

"무엇을 어디서 어떤 순서로 만드는가"는 MES 기준정보다. 대안으로 검토한 두 곳은 안 된다:

- **WMS `items`에 두는 안** — P13 당시 factory에 품번 마스터가 없어서 표준CT를 WMS에 뒀지만,
  V10으로 factory가 `parts`를 갖게 된 지금은 근거가 사라졌다. 공정 순서는 재고 단위 품목의
  속성이 아니다.
- **fleet에 두는 안** — fleet은 품번을 모른다. 알게 하면 P20 D-표의 "factory 코드에 로봇
  개념이 스며들지 않는다"의 역방향 위반이다.

```sql
create table processes (
    id            bigserial primary key,
    process_code  varchar(20) not null unique,   -- MACHINING / FINISHING / ASSEMBLY / INSPECTION / PACKING
    name          varchar(50) not null,
    zone_code     char(1) not null,               -- A(가공) B(조립) Q(품질) L(물류) — P33 D4의 구역 코드
    created_at    timestamp not null,
    updated_at    timestamp not null
);

create table part_routings (
    id                   bigserial primary key,
    part_id              bigint not null references parts (id),
    seq                  smallint not null,          -- 10, 15, 20, 30, 40 — 중간 삽입 여지
    process_id           bigint not null references processes (id),
    equipment_id         bigint not null references equipments (id),   -- 이 데모는 설비 고정(대체설비 없음, 8절)
    ideal_cycle_time_ms  integer,                    -- 품번×공정 표준CT. null이면 설비 고정값 폴백(D12)
    round_qty            integer not null default 50, -- 회차(파렛트) 크기(D3)
    created_at           timestamp not null,
    updated_at           timestamp not null,
    constraint uq_part_routing unique (part_id, seq)
);
```

`equipments`에 **`station_node_code varchar(30)`**(하역 노드)를 추가한다. 지금 설비 좌표와
하역 노드는 같은 열에 있지만 어디에도 "이 설비의 하역 자리는 PROD-A1이다"가 적혀 있지 않다
— fleet 주문의 `location`을 만들려면 이 매핑이 마스터에 있어야 한다.

| 설비 | 하역 노드 | 근거(좌표, V21/V24) |
|---|---|---|
| CNC-01 / CNC-02 / CNC-03 / MCT-01 | PROD-A1 / A2 / A3 / A4 | 설비 x=62/69/76/83, y=3 · 노드 y=6 같은 열 |
| ASM-01 / ASM-02 / INS-01 / PKG-01 | PROD-B1 / B2 / B3 / B4 | 설비 y=24 · 노드 y=21 같은 열 |

**데모 라우팅 시드** — 완제품 3종, 설비를 공유해서 실제 라인처럼 대기가 생기게 한다:

| 품번 | 10 | 15 | 20 | 30 | 40 |
|---|---|---|---|---|---|
| ASSY-2001 전륜 허브 | 가공 CNC-01 | — | 조립 ASM-01 | 검사 INS-01 | 포장 PKG-01 |
| ASSY-2002 후륜 허브 | 가공 CNC-02 | — | 조립 ASM-02 | 검사 INS-01 | 포장 PKG-01 |
| ASSY-2101 조향 기어 | 가공 CNC-03 | 정삭 MCT-01 | 조립 ASM-02 | 검사 INS-01 | 포장 PKG-01 |

INS-01·PKG-01은 세 품번이 공유한다 — 사이클 2.0s/1.5s로 가장 빠르니 병목은 아니지만,
회차 도착 순서에 따라 **한 설비에 READY 공정이 여럿 줄 서는** 상황이 생긴다(D5의 FIFO가
이걸 다룬다). 이게 "라인 밸런싱이 보인다"의 실체다.

> **`processes.zone_code`와 INS-01의 위치.** INS-01은 물리적으로 B행(조립 구역)에 있는
> 인라인 자동검사기다. Q 구역(QC-IN/QC-OUT, x=97)은 QMS의 MRB·샘플링 대기 자리이지
> 라우팅상의 검사 공정 자리가 아니다 — 둘을 섞지 않는다. 라우팅 "검사"는 `zone_code='B'`가
> 아니라 `'Q'`로 두되 설비는 INS-01(B3)이다. 구역 코드는 QMS·화면이 쓰는 논리 분류(P33 D4)이고
> 좌표는 설비 마스터가 쥔다 — 이미 P33이 "논리와 물리를 분리"한 그 방식이다.

### D2. 작업지시 = 헤더 + 공정별 작업(operation). 공정마다 작업지시를 따로 만들지 않는다

두 안을 비교했다.

| | (a) 공정마다 별개 작업지시 + 선행 지시 링크 | (b) 헤더 1건 + `work_order_operations` N행 |
|---|---|---|
| "ASSY-2001 500개"를 한 화면에서 보기 | 링크를 따라가야 한다 | 헤더 1건 |
| 보존법칙(상류 양품 = 하류 투입) | 지시 간 조인 | 같은 헤더 안의 이웃 행 |
| P18 LOT 4단 트리 | 트리 루트가 N개 | 헤더가 루트 — 4단 트리와 그대로 맞물린다 |
| 기존 POP·화면 호환 | 리스트에 지시가 4배로 불어난다 | 헤더 목록은 그대로, 공정 행이 안에 접힌다 |

**(b)로 간다.** 실 운영 MES가 지시-공정-회차 3단인 것과 같다(로드맵 0-A "공정 회차").

```sql
create table work_order_operations (
    id             bigserial primary key,
    work_order_id  bigint not null references work_orders (id),
    seq            smallint not null,
    process_id     bigint not null references processes (id),
    equipment_id   bigint not null references equipments (id),
    input_qty      integer not null default 0,   -- 이 공정에 실제로 도착한 수량(첫 공정은 planned_qty)
    good_qty       integer not null default 0,
    defect_qty     integer not null default 0,
    round_count    integer not null default 0,   -- 채번용(D10)
    status         varchar(20) not null,         -- WAITING / READY / IN_PROGRESS / COMPLETED / ON_HOLD
    started_at     timestamp,
    completed_at   timestamp,
    created_at     timestamp not null,
    updated_at     timestamp not null,
    constraint uq_wo_operation unique (work_order_id, seq)
);
create index idx_wo_operations_equipment_status on work_order_operations (equipment_id, status, id);
```

**기존 `work_orders` 컬럼 처리 (하위호환).**

| 컬럼 | 앞으로의 의미 | 이유 |
|---|---|---|
| `equipment_id` | **현재 진행 중(또는 다음) 공정의 설비** — 공정이 넘어갈 때 같은 트랜잭션에서 갱신 | 지도·POP·검사요청(`equipmentCode`)이 이 컬럼을 읽는다. 첫 단계에서 전부 고치지 않기 위한 파생 캐시 |
| `process_id` | 현재 공정의 `processes.id` — 드디어 실체를 가리킨다 | V10이 `item_id`에 한 것과 같은 정리 |
| `produced_qty` / `defect_qty` | **마지막 공정의** good+defect / defect — 롤업 | OEE·화면이 "완제품이 몇 개 나왔나"를 기대하는 자리. 공정별 값은 `work_order_operations`에서 본다 |
| `status` | 공정 상태의 롤업(D6) | 기존 상태머신·POP 버튼 유지 |

**마이그레이션(V25)이 기존 지시를 "공정 1개짜리 라우팅"으로 백필한다.** 기존 작업지시 =
seq 10 하나만 있는 특수 케이스가 되므로 **플래그로 두 경로를 유지할 필요가 없다.** 롤백은
마이그레이션 한 장이다(7절).

### D3. 공정 간 이송 단위는 회차(round)다 — 사이클마다 로봇을 부르지 않는다

사이클마다 fleet 주문을 내면 500개 × 공정 4개 = 지시 하나에 운송 2,000건이다. 실제 현장도
파렛트·대차 단위로 넘긴다(P23 "파렛트 1급 엔티티", EMMA 600K는 파렛트를 통째로만 옮긴다).

```sql
create table work_order_rounds (
    id               bigserial primary key,
    operation_id     bigint not null references work_order_operations (id),
    round_no         integer not null,
    round_code       varchar(60) not null unique,   -- WO-260907-001-OP10-R03 → fleet externalId
    good_qty         integer not null default 0,
    defect_qty       integer not null default 0,
    status           varchar(20) not null,          -- OPEN / CLOSED / IN_TRANSIT / ARRIVED / CONSUMED / TRANSPORT_FAILED
    from_node_code   varchar(30),
    to_node_code     varchar(30),
    closed_at        timestamp,
    dispatched_at    timestamp,
    arrived_at       timestamp,
    created_at       timestamp not null,
    updated_at       timestamp not null,
    constraint uq_round unique (operation_id, round_no)
);
```

- 공정 작업에는 항상 **OPEN 회차가 최대 하나**다. 양품 사이클이 들어오면 OPEN 회차의
  `good_qty`가 오르고, `round_qty`(라우팅 마스터, 기본 50)에 닿으면 **CLOSED**로 마감하고
  다음 OPEN 회차를 연다. 불량은 회차에 세지만(추적용) 넘어가지는 않는다.
- 공정이 완료되면(D4) 마지막 OPEN 회차는 수량 미달이어도 마감한다 — "끝물 파렛트".
- CLOSED → fleet 주문 생성 → IN_TRANSIT → 완료 통지 → ARRIVED → 다음 공정이 `input_qty +=
  good_qty` 하며 CONSUMED. **마지막 공정의 회차는 이송 대상이 완제품 입고장(`WH-RECV`)이다**
  — 창고동 진입은 fleet이 게이트에서 AGV로 넘긴다(P22, 기존 `QC-OUT→WH-RECV` 데모 흐름과
  같은 경로). WMS 입고 전표 자동 생성은 이번 범위 밖(8절).

회차는 로드맵 0-A의 "실적을 회차로 쌓고 집계를 롤업" 그 자체다 — 공정 작업의
`good_qty`는 회차 합이어야 하고, 이 불변식을 테스트가 지킨다(6절).

### D4. 완료 판정은 보존법칙이다 — 계획수량이 아니다

```
공정 N 완료  ⇔  good(N) + defect(N) ≥ input(N)   그리고   선행 공정 N-1이 COMPLETED이고 그 회차가 전부 CONSUMED
input(10)   =  planned_qty
input(N>10) =  Σ ARRIVED·CONSUMED 회차의 good_qty (선행 공정에서 실제로 넘어온 것)
헤더 완료    ⇔  마지막 공정 COMPLETED  그리고  IN_TRANSIT 회차 0건
```

- 상류에서 불량 17개가 나면 하류 투입은 483이고, 483을 다 처리하면 완료다. `recordCycle`이
  지금 보는 "produced ≥ planned" 조건으로는 하류가 영원히 IN_PROGRESS로 남는다 — 0-A가
  실 운영에서 겪었다고 적어 둔 그 사고다.
- **투입 없는 사이클은 실적이 아니다.** 하류 설비가 도착 회차 없이 사이클을 발행하면
  `CYCLE_COMPLETED` 이벤트는 남기되 실적에 넣지 않는다(지금도 IN_PROGRESS 지시가 없는
  설비는 그렇게 동작한다 — "설비만 돌고 실적은 안 잡힘, 실제 현장과 같다"). 그래서 데모
  초반 몇 분은 하류가 RUNNING인데 실적이 0인 구간이 생기는데, 이건 정직한 상태다. 첫
  회차가 도착하는 시간(가공 3s × 50 = 150s + 운송 ~1분)을 줄이고 싶으면 `round_qty`를
  낮추지 시뮬레이터를 속이지 않는다.
- 최종 양품이 계획보다 적게 끝나는 것이 **정상**이다("계획 500 / 최종 양품 471"). 화면은
  그 차이를 공정별 불량 합으로 설명할 수 있어야 한다(WP3).

### D5. 설비의 작업 선택은 FIFO 자동 착수. POP 착수 버튼은 남는다

`applyCycle(equipmentCode)`가 그 설비의 IN_PROGRESS 공정 작업을 찾는 것은 지금과 같다.
없으면 **그 설비의 READY 공정 작업 중 가장 오래된 것**(id 오름차순)을 IN_PROGRESS로 올리고
`OPERATION_STARTED`(source `AUTO`)를 남긴 뒤 그 사이클을 실적에 넣는다. 자동 설비(CNC·조립기)가
자재가 오면 바로 도는 것과 같고, 라이브 데모가 POP 클릭 없이 계속 흘러야 하기 때문이다.

- POP의 착수 버튼은 그대로 — 사람이 먼저 누르면 그 작업이 우선한다(READY→IN_PROGRESS를
  사람이 먼저 한 것뿐). `factory.routing.auto-start=false`로 끄면 완전 수동 현장이 된다.
- 한 설비에 IN_PROGRESS는 **항상 최대 하나**다 — 인덱스 `(equipment_id, status, id)`와 서비스
  단의 재확인으로 지킨다(D10의 잠금과 같은 트랜잭션).

### D6. 헤더 상태는 공정 상태의 롤업이다 — 기존 상태머신을 깨지 않는다

| 공정 작업 상태 | 헤더 `status` |
|---|---|
| 전부 WAITING/READY(아직 아무 공정도 안 돎) | ASSIGNED (지금 초기값과 같음) |
| 하나라도 IN_PROGRESS이거나, 완료된 공정 뒤에 READY/WAITING이 남음 | IN_PROGRESS |
| 마지막 공정 COMPLETED, IN_TRANSIT 회차 0 | INSPECTION_WAITING → (POP 마감) COMPLETED — **기존 흐름 그대로** |
| 헤더 `hold()` | ON_HOLD — 모든 공정 작업도 ON_HOLD(아래) |

**홀드는 지시(LOT) 단위다.** QMS MRB가 열리면 지금처럼 헤더가 ON_HOLD가 되고 설비가
QUALITY_HOLD가 된다 — 이때 그 지시의 **모든 공정 작업**이 ON_HOLD로 내려가 사이클을 실적에
넣지 않는다. 상류만 멈추고 하류는 계속 돌리는 것은 실제 MRB 관행과 다르다(같은 LOT이다).
이미 이송 중인 회차는 도착까지 가되(로봇을 중간에 세우지 않는다) ARRIVED에서 멈추고,
릴리즈되면 CONSUMED로 넘어간다. `MrbReview.holdApplied`·`QualityHoldService`는 안 바뀐다.

QMS로 나가는 검사요청 신호(`factory/quality/inspection-requested`)는 **공정 작업 단위**로
임계를 본다(공정마다 `defect_qty ≥ threshold` 한 번). 페이로드에 `operationSeq`를 추가한다 —
QMS는 모르는 필드를 무시하므로(`json.path(...)`) 호환된다. `inspectionRequested` 집합의 키는
`workOrderNo`에서 `workOrderNo + "#" + seq`로 바뀐다.

### D7. 시뮬레이터는 한 줄도 안 바뀐다

P8에서 정한 그대로 — 시뮬레이터는 설비만 흉내내고 작업지시를 모른다. 라우팅이 "어느 설비의
사이클이 어느 지시의 어느 공정 실적인가"를 서버 쪽에서 결정하는 구조이므로, 발행 측은
그대로 두는 것이 옳다. 데모 시나리오 러너(P15-1, `ScenarioService.injectDefectBurst`)도
같은 `applyCycle` 경로를 타므로 **상류 불량 주입 → 하류 투입 감소 → 그래도 완료**를 버튼
하나로 재현할 수 있다(6절의 시연 시나리오).

### D8. WIP 이송은 factory→fleet 직접 호출 — WMS 패턴을 그대로 옮긴다

M2M 다섯 번째 방향이다. `docs/auth-boundaries.md`의 표에 한 줄이 늘어난다.

| 방향 | 호출자 `ServiceTokenProvider` | 대상 | 완료 통지 |
|---|---|---|---|
| **factory → fleet** | `com.pixelfactory.transport.ServiceTokenProvider`(`svc-factory`) | `POST /api/orders` (2스텝: `station(N)` load → `station(N+1)` unload, `externalId=round_code`, `materialId=round_code`, priority NORMAL) | MQTT `fleet/tasks/{round_code}/completed|failed` 구독 |

- **트랜잭션 밖에서.** 회차 마감은 `applyCycle` 트랜잭션 안에서 일어나고, fleet 호출은
  `@TransactionalEventListener(AFTER_COMMIT)`에서 try-catch로 한다(QMS 검사요청과 같은 구조).
  실패하면 회차는 CLOSED로 남고 **`TransportRetryJob`(30초 주기)**이 CLOSED 회차를 다시 시도한다
  — fleet이 내려가 있어도 사이클 적재·OEE는 멈추지 않는다(컴포저블 검증 항목).
- **MQTT ACL** — `oee-service`에 `topic read fleet/tasks/#` 한 줄 추가. P16 ACL의 원칙("기존
  네임스페이스 관례를 경계로")에 맞다 — WMS가 이미 같은 토픽을 같은 이유로 읽는다.
- **구독 클라이언트** — `oee-service`의 기존 `MqttEventSubscriber`는 `factory/#`만 구독한다.
  필터를 하나 더 추가하는 것이 아니라 **별개 client id(`oee-service-transport`)로 두 번째
  구독자**를 둔다 — `cleanSession=false` 세션이 토픽 필터 집합에 묶여 있어서 기존 세션의
  필터를 바꾸면 재접속 시 브로커의 큐 상태와 어긋난다(P8의 유한 버퍼 설계와 충돌). WMS
  구독자의 `connectComplete` 교착 회피(전용 스레드에서 subscribe)를 그대로 가져온다.
- **환경변수** — Railway factory 서비스에 `FLEET_BASE_URL=http://pixel-fleet.railway.internal:9002`,
  MQTT 계정에 이미 있는 `oee-service` 그대로. P22 배포 검증에서 `LAYOUT_URL` 누락으로 한 번
  깨진 적이 있으니 `deploy-railway.md` 체크리스트에 추가한다.

### D9. 이벤트

`FactoryEventType`에 5개 추가. 전부 `target_type=WORK_ORDER`, `lot_no` 채움(P18 트리의 재료).

| 이벤트 | 시점 | payload |
|---|---|---|
| `OPERATION_STARTED` | 공정 작업 READY→IN_PROGRESS (AUTO/POP 구분) | `seq, processCode, equipmentCode, source` |
| `OPERATION_COMPLETED` | 보존법칙 충족 | `seq, inputQty, goodQty, defectQty` |
| `ROUND_CLOSED` | 회차 마감 | `roundCode, goodQty, defectQty` |
| `WIP_DISPATCHED` | fleet 주문 생성 성공 | `roundCode, from, to` |
| `WIP_ARRIVED` | 완료 통지 수신 | `roundCode, nextSeq` |

`NOTIFICATION_SENT`(미사용, D12 잔존)는 이번에도 안 건드린다.

### D10. 동시성 — 회차 채번과 단일 진행 작업

- `applyCycle`은 설비별로 **`select ... for update`로 공정 작업 행을 잠근다.** 회차 번호는
  잠긴 행의 `round_count + 1`이라 MAX+1 경쟁이 없다(0-A "채번은 MAX+1 단독 금지").
- MQTT 콜백은 단일 스레드지만 `ScenarioService`(REST)가 같은 설비에 동시에 들어올 수 있다 —
  그래서 잠금은 "혹시"가 아니라 실제 경로다.
- POP 실적 보고(`completeProduction`)는 지금도 요청 단위 재검증을 한다. 공정 작업 단위로
  옮기면서 "프론트 재진입 가드 / 요청 중복 제거 / 트랜잭션 안 재확인" 3층(0-A)을 그대로
  적용한다 — 2층(요청 중복 제거)은 `Idempotency-Key` 헤더가 아니라 **회차 코드**를 키로 쓴다.

### D11. 화면 — 새 탭을 만들지 않는다

| 화면 | 바뀌는 것 |
|---|---|
| Factory 탭 작업지시 목록 | 행을 펼치면 공정 진행 바: `10 가공 500/500 · 20 조립 137/483 · 30 검사 대기 · 40 포장 대기`. 회차 IN_TRANSIT 개수 배지 |
| 통합 지도 | 이미 그리는 물류 흐름선(진행 중 fleet 주문)에 **회차 코드 라벨**. 새 도형 없음 — 0-A "없는 데이터를 그리지 않는다" |
| POP | 단말의 라인에 속한 설비의 **공정 작업**을 나열(헤더가 아니라). 착수/실적/마감 버튼 의미 동일 |
| QMS 검사 목록 | `operationSeq` 표시(선택) |

### D12. 표준CT(D6)는 `part_routings.ideal_cycle_time_ms`로 해소한다 — 단, 마지막 WP

- `IdealCycleTimeProvider`의 두 번째 구현체 `RoutingIdealCycleTime`: 설비의 현재 IN_PROGRESS
  공정 작업 → 라우팅 행의 표준CT. null이면 `EquipmentFixedIdealCycleTime`으로 폴백. 인터페이스가
  P9에서 이미 이걸 위해 준비돼 있다.
- WMS `items.ItemStandardCycleTime`은 **은퇴**(테이블은 두고 쓰지 않음, 후속 마이그레이션에서
  삭제). factory OEE가 WMS 기동 여부에 묶이면 "WMS를 내려도 factory는 정상"(P13 완료 기준)이
  깨지므로, WMS에서 읽어오는 안은 버린다.
- 그러면 `auth-boundaries.md`의 "알려진 구멍" 첫 줄(WMS `GET /api/items/**` permitAll)을 **닫을
  근거가 생긴다** — D6 작업 시 다시 본다고 적어 둔 그 줄이다.

---

## 4. 데이터 흐름 — 사이클 하나가 겪는 일

```
MQTT factory/LINE-1/CNC-01/cycle {defect:false}
  └ MqttMessageHandler → EquipmentTelemetryService.applyCycle("CNC-01", false)
      ├ 설비 CNC-01의 IN_PROGRESS 공정 작업을 for update로 조회
      │   └ 없으면: 가장 오래된 READY 작업 → IN_PROGRESS, OPERATION_STARTED(AUTO)
      │   └ 그래도 없으면: CYCLE_COMPLETED만 기록하고 종료(실적 아님)
      ├ 작업.good_qty += 1, OPEN 회차.good_qty += 1
      ├ 회차.good_qty == round_qty → CLOSED, ROUND_CLOSED, 다음 OPEN 회차 생성
      │   └ (AFTER_COMMIT) FleetTransportClient.create(round) → IN_TRANSIT, WIP_DISPATCHED
      │                                                     실패 → CLOSED 유지, RetryJob이 재시도
      ├ good+defect ≥ input 이고 선행 공정 완료 → COMPLETED, OPERATION_COMPLETED,
      │   끝물 회차 마감, 헤더 equipment_id/process_id를 다음 공정으로, 헤더 status 롤업
      └ CYCLE_COMPLETED 기록(workOrderId 채움)

MQTT fleet/tasks/WO-260907-001-OP10-R03/completed
  └ TransportEventHandler → RoundService.arrive(roundCode)
      ├ 회차 ARRIVED, WIP_ARRIVED
      ├ 다음 공정 작업.input_qty += good_qty, 회차 CONSUMED(헤더가 ON_HOLD면 ARRIVED에서 대기)
      └ 다음 공정 작업이 WAITING이면 READY로 (다음 사이클에서 D5가 착수)
```

---

## 5. 실행 단계 (WP0 → WP4, 각 WP = 브랜치 1개 = 커밋 소수)

### WP0 — 마스터 (`V25__process_routing.sql`)
- [ ] `processes` 5행 시드, `part_routings` 위 표대로 13행, `equipments.station_node_code` 8행
- [ ] `RoutingSeedConsistencyTest` — 시드의 설비 코드·노드 코드가 `equipments`·최신 layout
      마이그레이션에 실재하는지(`NodeMapLayoutConsistencyTest`와 같은 방식, 틀리면 빌드 실패)
- [ ] `GET /api/parts/{partCode}/routing` — 화면·검증용 조회

### WP1 — 작업지시 분해 + 회차 + 보존법칙
- [ ] `work_order_operations` / `work_order_rounds` + 기존 지시 백필(공정 1개짜리)
- [ ] `WorkOrderDataInitializer` — 설비당 1건이 아니라 **품번당 1건**(ASSY-2001/2002/2101, 500개).
      첫 공정만 READY. 하류는 WAITING
- [ ] `EquipmentTelemetryService.applyCycle` — 4절 흐름. `WorkOrder.recordCycle`은 삭제하지 않고
      "공정 1개짜리 지시"에서만 동작하도록 두지 않는다 — **삭제한다.** 두 경로를 남기면 보존법칙이
      한쪽에만 적용된다
- [ ] 헤더 롤업(D6), 홀드 전파, 검사요청 키 변경
- [ ] 단위 테스트 6절 ①②③

### WP2 — WIP 이송 (factory → fleet)
- [ ] `transport/` 패키지: `FleetTransportClient`, `ServiceTokenProvider`(svc-factory),
      `TransportEventSubscriber`(client id 분리, D8), `TransportRetryJob`
- [ ] `infra/mosquitto/acl.conf` — `oee-service`에 `read fleet/tasks/#`
- [ ] `docs/auth-boundaries.md` M2M 표 5번째 줄, `deploy-railway.md` 환경변수 체크리스트
- [ ] `scripts/dev-up.ps1`·README — 난수 생성기는 이미 `-Demo` 스위치(`DEMO_TASK_GENERATOR_ENABLED`)
      뒤에 있고 기본은 꺼짐이다. WP2 이후엔 `-Stack full`이 `-Demo` 없이도 화면이 흐르므로 README의
      권장 명령에서 `-Demo`를 뺀다(실제 흐름과 난수 흐름이 겹치면 레인 용량을 두 배로 먹는다).
      fleet 단독 스택(`dev-up.ps1` 기본)에서는 `-Demo`가 여전히 유일한 흐름 공급원이라 그대로 둔다
- [ ] Testcontainers 종단 테스트 6절 ④

### WP3 — 화면
- [ ] Factory 탭 공정 진행 바 + 회차 배지, 지도 흐름선 라벨, POP 공정 단위
- [ ] `api.ts` — `GET /api/work-orders/{id}/operations`, `GET /api/work-orders/{id}/rounds`

### WP4 — 표준CT (선택, D12)
- [ ] `RoutingIdealCycleTime` + 라우팅 시드에 표준CT 채움(시뮬레이터 값과 동일: 3000/4500/2500/2000/1500)
- [ ] WMS `GET /api/items/**` permitAll 제거 + `auth-boundaries.md` 갱신

---

## 6. 검증 — "구현했다"가 아니라 숫자로

| # | 검증 | 방법 | 통과 기준 |
|---|---|---|---|
| ① | 보존법칙 | `OperationTest`(순수 단위) — 투입 500, 상류 불량 17 → 하류 투입 483, 483 처리 시 COMPLETED | `good+defect ≥ input`에서만 완료. 계획 500을 못 채워도 완료 |
| ② | 회차 불변식 | `RoundTest` — 양품 137개 → 회차 50/50/37(마지막은 공정 완료 시 마감) | `Σ round.good == operation.good` 항상 |
| ③ | 동시 착수 | `EquipmentTelemetryServiceTest` — 같은 설비에 READY 2건, 사이클 1회 | IN_PROGRESS는 오래된 1건만 |
| ④ | 종단 | `WipTransportIntegrationTest`(Testcontainers Postgres+Mosquitto) — 사이클 50회 발행 → `fleet/tasks/{round}/completed` 발행 | 회차 ARRIVED, 다음 공정 READY, `WIP_ARRIVED` 이벤트 1건 |
| ⑤ | 컴포저블 | 로컬 `-Stack full`에서 fleet 프로세스 kill | 사이클 적재·OEE 계속, 회차 CLOSED에 쌓임, fleet 복구 후 RetryJob이 전부 발송 |
| ⑥ | 레인 용량 | Prometheus `fleet_traffic_segments_occupied`, `fleet_orders_pending`(`FleetMetrics`) — WP2 전/후 10분 시계열 | `fleet_orders_pending`이 단조 증가하지 않는다. 증가하면 `round_qty`를 올린다(50→100), 코드가 아니라 마스터로 조정 |
| ⑦ | 시연 시나리오 | 시나리오 러너에서 CNC-01 불량 20회 주입 | ASM-01 진행 바의 분모가 500→480으로 줄고, 최종 포장 양품 < 500으로 **완료**된다. QMS 검사요청은 공정 10에 1건 |

⑥의 숫자는 이 문서에 실측값으로 추가한다(P20~P27 문서의 관례).

---

## 7. 리스크 & 롤백

- **레인 용량(가장 불확실).** 지금 실제 동시 주행은 1~2대이고 처리량 약 2건/분(BACKLOG "남은
  처리량 개선")이다. 가공 3라인이 각각 150초마다 회차를 내고 하류 3공정이 이어받으면
  **분당 약 2~3건**으로 지금 한계 근처다. 대응 순서: (1) `DemoTaskGenerator` 끄기(WP2),
  (2) `round_qty` 마스터 조정, (3) 그래도 부족하면 BACKLOG의 "구간 쪼개기"를 P36으로 — 이번
  범위에 넣지 않는다.
- **INS-01 공유 병목.** 세 품번이 한 검사기를 쓴다. 2.0s 사이클이라 계산상 여유가 있지만
  회차 도착이 몰리면 READY가 줄 선다 — 이건 버그가 아니라 보여주려는 현상이다. 다만 헤더
  `equipment_id`가 "다음 공정 설비"를 가리키는 순간 지도의 설비 하이라이트가 미리 옮겨 가는
  것처럼 보일 수 있다 → 헤더 갱신은 READY가 아니라 **IN_PROGRESS 전환 시점**에 한다.
- **기존 데이터.** 라이브 DB의 진행 중 지시 8건은 백필로 공정 1개짜리가 된다. 그 지시는
  라우팅이 없으므로 이송도 없다 — 재기동 후 `WorkOrderDataInitializer`는 count>0이라 안
  돈다. **라이브에서는 기존 8건을 COMPLETED로 마감하는 데이터 스크립트를 한 번 돌린 뒤 품번당
  지시 3건을 REST로 만든다**(운영 절차, `deploy-railway.md`에 기록).
- **롤백.** V25는 테이블 추가 + 백필뿐이라 되돌림은 `V26__revert_routing.sql`(테이블 3개
  drop, `equipments.station_node_code` drop) 한 장. 단, WP1이 `recordCycle`을 삭제하므로 코드
  롤백은 브랜치 단위 — WP1은 **한 브랜치로 묶어** 반쯤 머지된 상태가 없게 한다.

---

## 8. 이번엔 안 하는 것 (다음 문서 후보)

- **BOM 자재 소비·불출** — 공정 착수 시 BOM 자식 품목을 WMS에 출고 요청. WMS→fleet 자재
  투입 흐름(`WH-PICK → PROD-A1`)이 지금 DemoTaskGenerator에만 있는 이유가 이것. 라우팅이
  먼저 있어야 "어느 공정에 무엇을"이 정해지므로 이 문서 뒤다.
- **완제품 WMS 자동 입고** — 마지막 회차가 `WH-RECV`에 도착하면 WMS 입고 전표를 만드는 것.
  factory→WMS 방향의 첫 연동이라 별도 결정이 필요하다(REST인지 토픽인지).
- **LOT 4단 트리(P18)** — 헤더·공정·회차·(P23) 파렛트가 생기면 재료가 다 갖춰진다. 이 문서
  직후가 적기.
- **대체 설비·설비군** — 라우팅에 설비를 고정했다. 설비 DOWN 시 같은 공정의 다른 설비로
  넘기는 것은 배차 정책 문제이고, 8대 규모에서 과하다.
- **공정 검사 샘플링·Q 구역 물리 이송** — MRB 홀드 시 회차를 `QC-IN`으로 실제 옮기는 것.
  D6의 "ARRIVED에서 대기"로 충분하다고 봤다. 지도에서 Q 구역이 계속 비어 보이면 그때.
- **회차 병합·분할** — 회차는 항상 `round_qty` 또는 끝물 하나다.
