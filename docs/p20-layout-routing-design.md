# P20 설계 문서 — 컴포저블 레이아웃 + 그래프 기반 라우팅

> 상태: **승인됨 (2026-08-06), P20-1 완료.** `docs/BACKLOG.md`의 P20 항목이 요구하는
> "세부 설계를 먼저 문서로 확정하고 승인받은 뒤 착수한다"의 그 문서다. 실행 단계(5절)를
> 순서대로 진행 중 — 각 단계 완료 시 체크박스와 검증 근거를 남긴다.
>
> 선행 문서: `docs/pixel-platform-roadmap.md`의 **P11. 레이아웃 서버화** — "factory가 평면도를
> 소유하고 fleet은 캐시한다"는 소유권 결정과, 그 문서가 남긴 미해결 질문
> ("연결로 x는 아직 마스터가 없다 ... 결정 필요")이 이 문서의 출발점이다.

---

## 1. 목표 구조

```
                       ┌─────────────────────────────┐
                       │   layout_edges (신설)        │
                       │   layout_nodes (+ GATE 타입) │   factory 소유 (Flyway, 정적 토폴로지)
                       │   layout_buildings           │   "무엇이 어디 있고 무엇과 연결되는가"
                       └──────────────┬────────────────┘
                                      │ GET /api/layout (기존 계약 확장)
                                      ▼
                       ┌─────────────────────────────┐
                       │   LocationRegistry (캐시)     │   fleet 소유
                       │   RouteGraph (A*/Dijkstra)   │   "그 연결을 어떻게 쓰고 얼마가 드는가"
                       │   Redis: 임시 장애물 상태     │   (라이브, 비영속)
                       └──────────────┬────────────────┘
                                      │ MQTT: fleet/layout/{buildingCode}/obstacle
                                      ▲
                       ┌──────────────┴────────────────┐
                       │   시뮬레이터 (obstacle 발행)   │
                       └─────────────────────────────────┘

Building-WH ──(GATE: WH-ELEV-1F)──┐
Building-PROD ─────────────────────┼── 하나의 그래프 (기존처럼 겹치지 않는 좌표 구획)
Building-QC ────────────────────────┘
Building-A(가공동, 신설) ──(GATE: 신규 도어 노드)── 기존 그래프에 데이터만 추가해 편입
Building-B(조립/물류동, 신설) ──(GATE: 신규 도어 노드)── 〃
```

**핵심 전환**: `LaneGraph`가 "통로 2개 + 커넥터 x 8개"를 컴파일 타임 상수로 아는 라우터에서,
DB에 있는 노드-엣지 그래프를 읽어 최단경로를 계산하는 라우터로 바뀐다. 건물을 추가하는 일이
**코드 변경이 아니라 데이터(노드·엣지 insert)가 되는 것**이 이번 설계의 목표다.

---

## 2. 핵심 원칙 (기존 CLAUDE.md 준수 확인)

이 설계가 건드리는 3개 모듈(`pixel-factory`, `pixel-fleet`, `robot-sim`)의 절대 원칙과 충돌하지
않는지 먼저 확인한다.

| 원칙 | 출처 | 이 설계가 지키는 방법 |
|---|---|---|
| 이벤트가 단일 진실 공급원 | 양쪽 CLAUDE.md | 장애물 발생/해제를 `FleetEventType` 이벤트로 남긴다(4절 D6). 그래프 캐시를 몰래 바꾸지 않는다 |
| 컴포저블 — 모듈 간 직접 코드/DB 참조 금지 | 루트 CLAUDE.md | fleet은 factory DB를 절대 JOIN하지 않는다. `GET /api/layout` REST 계약만 확장(4절 D1) |
| DB per module | 루트 CLAUDE.md | `layout_edges`는 factory DB에, 장애물 라이브 상태는 fleet의 Redis에 — 별도 절대 안 섞는다 |
| robot-sim은 fleet DB에 런타임 의존 안 함 | pixel-fleet CLAUDE.md | robot-sim은 여전히 자기 `NodeMap` 사본을 유지, 정합성 테스트로만 검증(4절 D7) |
| 스키마는 마이그레이션으로 | 양쪽 CLAUDE.md | `layout_edges`/버전 컬럼은 factory Flyway V13, gate 관련 fleet 변경은 fleet Flyway V8 |
| pixel-factory는 가공 라인 OEE가 범위, 물류 확장은 v2 백로그 | pixel-factory CLAUDE.md | factory는 **좌표·연결의 기하학적 사실만** 소유한다(어디 있고 무엇과 이어지는가). 라우팅 알고리즘·배차 비용·장애물 해석 같은 "AMR이 그 사실을 어떻게 쓰는가"는 전부 fleet 소관 — factory 쪽 코드에 로봇/AMR 개념이 스며들지 않는다 |

---

## 3. 확정 결정 (설계자가 정함 — 이유 포함)

### D1. 좌표계는 지금처럼 **하나의 공유 캔버스**를 유지한다 (완전 독립 로컬 좌표계는 안 감)

Building-A/B를 "완전히 독립된 로컬 좌표계 + 합성 시점에 오프셋 변환"으로 만드는 안도 검토했다.
하지만 그러려면 로봇 텔레메트리(`{x,y}`)에 건물 식별자를 추가하고, 대시보드 렌더링에 좌표 변환
계층을 넣어야 한다 — 이번 백로그가 명시한 범위("엣지 단위 차단, 자유형태 회피는 안 함")보다 넓다.

**결정**: 지금처럼 모든 건물이 겹치지 않는 사각형으로 하나의 캔버스(`layout_settings.width/height`)
위에 배치되는 방식을 유지한다. 대신 **그래프 데이터 모델에서 건물을 1급 개념으로 다룬다** —
모든 노드/엣지가 `building_code`를 갖고, 새 건물을 추가하는 일은 "빈 좌표 구획에 새 노드·엣지
행을 insert하고, 기존 그래프와 GATE 노드로 잇는 것"으로 끝난다. 코드는 안 바뀐다.

완전 독립 좌표계(진짜 오프셋 합성)는 **범위 밖**으로 남기고 8절에 기록한다 — 나중에 "다른 팀이
좌표를 몰라도 건물을 설계할 수 있어야 한다"는 요구가 생기면 그때 별도 항목으로 판다.

### D2. 그래프 소유권: factory가 토폴로지(정적), fleet이 실행(동적)을 가진다

P11 로드맵 문서가 미해결로 남긴 질문("연결로는 누가 소유하나")에 대한 답이다.

- **factory 소유 (Flyway, `layout_edges` 테이블)**: 어떤 노드와 어떤 노드가 물리적으로 이어져
  있는가, 기본 통행 비용(거리/속도 가정), 통로 폭 같은 "바뀌면 도면이 바뀌는" 정적 사실.
- **fleet 소유 (런타임, Redis)**: 지금 이 엣지가 막혀 있는가(장애물), 지금 이 엣지를 누가 점유
  중인가(교통정리 — 기존 `TrafficController` 그대로), 배차 비용 계산. "5분 뒤엔 다를 수 있는" 사실.

이렇게 나누면 pixel-factory CLAUDE.md의 "AGV/로봇 개념이 스며들면 안 된다"는 원칙과, 로드맵의
"factory가 평면도를 소유한다" 결정을 동시에 만족한다 — factory는 로봇이 그 통로를 어떻게 쓰는지
전혀 모른다.

### D3. `layout_edges`는 노드 단위 그래프다 (연속 좌표계 위 자유 경로가 아니다)

엣지는 `(from_node_code, to_node_code, base_cost, bidirectional)`. 로봇은 항상 노드에서
노드로 이동한다 — 지금 `LaneGraph.plan()`이 만드는 waypoint 리스트도 사실 노드들의 나열이었다
(통로 진입점도 암묵적 노드였을 뿐). 이번에도 같은 수준을 유지하고, "두 노드 사이 임의의 점"
같은 연속 공간 개념은 넣지 않는다 — 그게 8절에서 범위 밖으로 뺀 자유형태 회피다.

### D4. 장애물은 **factory DB에 쓰지 않는다** — fleet의 라이브 캐시에만 존재

장애물은 로봇 위치(`RobotLiveState`)와 성격이 같다 — 지금 순간의 사실이고, 자주 바뀌고,
재부팅하면 사라져도 되는 정보다. `RobotLiveState`가 Postgres가 아니라 Redis에 사는 것과 같은
이유로, "엣지 X가 지금 막혔다"도 factory의 `layout_edges`(정적 토폴로지)에 쓰지 않고 fleet의
Redis에 `blocked:{edgeId} → {reason, until}` 형태로만 둔다.

**장점**: DB per module을 어기지 않는다(factory 테이블에 fleet이 쓰기 접근할 필요가 없다).
**결과**: `RouteGraph`가 최단경로를 계산할 때 "기본 비용(factory) + 지금 막혔는가(fleet 캐시)"를
합쳐서 엣지 비용을 정한다 — 막혔으면 비용 무한대.

### D5. "장애물로 인한 재탐색"과 "교통정리로 인한 대기"는 다르게 다룬다

지금 `tryReserve` 실패 시 로봇은 같은 경로를 2초마다 재시도한다(1절 리서치에서 확인). 이 동작을
**교통정리(다른 로봇이 지금 그 구간에 있다) 케이스에는 그대로 둔다** — 매번 다른 로봇이 앞에
있다고 재탐색하면 경로가 계속 바뀌는 flapping이 생긴다. 대신 **장애물(엣지 자체가 막힘) 케이스는
새로 도입한다** — 엣지 비용이 무한대가 되는 순간은 "지나갈 수 없다"는 토폴로지 변화이므로,
현재 미실행 구간에 대해 `RouteGraph`로 경로를 다시 계산한다.

구분 기준: `TrafficController`가 "누가 점유 중"이라 막았나(대기) vs `RouteGraph`가 "비용 무한대"라
아예 그 엣지를 못 쓰나(재탐색). 두 경로가 섞이지 않게 `OrderService`에서 실패 사유를 구분해서
받는다(지금은 `tryReserve`가 boolean만 반환 — D2 계열 실패 사유 enum으로 확장 필요, 6절 참고).

### D6. 장애물 이벤트 — 새 `FleetEventType` 2개 + 새 MQTT 토픽

기존 `FleetEventType`(`ROBOT_*`, `TASK_*`)에 `LAYOUT_OBSTACLE_ADDED`, `LAYOUT_OBSTACLE_CLEARED`를
추가한다. 토픽은 기존 `fleet/{robotCode}/{kind}` 컨벤션이 로봇 스코프라 그대로 못 쓰므로,
**`fleet/layout/{buildingCode}/obstacle`**로 새 계열을 연다 — factory의
`factory/{lineCode}/{equipmentCode}/{kind}`처럼 계층형 이름을 따르되, 로봇이 아니라 레이아웃이
스코프라는 걸 경로에 드러낸다.

페이로드: `{"kind":"OBSTACLE_ADDED","edgeId":"...","reason":"...","validUntil":"2026-08-05T10:00:00Z"}`

`modules/pixel-fleet/docs/mqtt-topics.md`에 기존 4개 kind 옆에 같은 서식으로 추가한다.

### D7. 좌표 4곳 사본 — DB가 정본, 나머지는 캐시/사본 역할을 유지하되 대상만 넓힌다

- `LocationRegistry`: `/api/layout` 응답에 `edges`가 추가되면 그걸 캐시해서 `RouteGraph`를 만든다.
  기존 5분 주기 refresh, 실패 시 fallback 유지(구조 안 바꿈).
- `LaneGraph`: 컴파일 타임 상수(`CONNECTOR_X`, `AISLE_UPPER_Y/LOWER_Y`) 제거. `RouteGraph`로
  이름을 바꾸거나 내부 구현만 교체 — `OrderService`/`RobotService`가 보는 `plan()`/`planByNode()`/
  `segmentAt()` 시그니처는 그대로 유지해서 호출부 변경을 최소화한다(6절 참고).
- `DemoTaskGenerator.FLOWS`: **이번 단계에서는 안 건드린다.** 하드코딩된 흐름 목록을 그래프에서
  자동 도출하는 건 범위 밖(8절) — 새 건물을 추가하면 그 건물을 오가는 흐름도 수동으로 몇 줄
  추가해야 한다. 지금 방식과 동일한 수준의 수작업이라 회귀는 아니다.
- robot-sim `NodeMap`: 여전히 fleet DB에 런타임 의존하지 않는다(원칙 유지). 다만
  `NodeMapLayoutConsistencyTest`의 비교 대상을 **V12 SQL 텍스트 파싱에서 `/api/layout` 응답
  파싱(또는 최신 마이그레이션 파일)으로 변경** — 마이그레이션 버전이 올라갈 때마다 테스트
  파일 경로를 손으로 고치던 문제가 없어진다.

### D8. 레이아웃 버전은 "기록만" — 과거 시점 재생 UI는 이번 범위 밖

`layout_settings`에 `layout_version`(정수), `effective_from`(timestamp) 컬럼을 추가하고,
평면도가 바뀌는 마이그레이션마다 버전을 올린다. **이번 단계에서 만드는 건 이 기록뿐이다** —
"과거 이벤트를 그 시점 평면도로 다시 렌더링"하는 기능은 만들지 않는다(8절). 지금 당장 그 기능을
쓸 화면이 없는데 먼저 만들면 추측성 설계가 된다 — 필요해지면 그때 버전 테이블은 이미 있으니
소비하는 쪽만 만들면 된다.

---

## 4. 아키텍처 변경 상세

### D1 — factory: `layout_edges` 신설 (Flyway V13, 초안)

```sql
create table layout_edges (
    id            bigserial primary key,
    from_node     varchar(30) not null references layout_nodes(node_code),
    to_node       varchar(30) not null references layout_nodes(node_code),
    base_cost     double precision not null,   -- 기본 통행 비용(대략 거리)
    bidirectional boolean not null default true,
    created_at    timestamp not null,
    updated_at    timestamp not null,
    constraint uq_layout_edge unique (from_node, to_node)
);

-- 기존 3개 건물의 통로·연결로를 엣지로 흡수 (LaneGraph의 CONNECTOR_X/AISLE_Y가 암묵적으로
-- 표현하던 연결을 명시적 행으로 만든다 — 값은 동일, 표현만 코드에서 데이터로 옮긴다)
```

`layout_nodes.node_type`에 `GATE` 추가(건물 간 연결점 — 예: 신설 Building-A/B를 잇는 도어 노드.
`ELEVATOR`는 이미 층간 연결점 역할을 하므로 사실상 "같은 건물 내 GATE"의 특수형이다).

`LayoutResponse`(및 `LayoutService.get()`)에 `List<Edge> edges` 필드 추가 — 새 서브 리소스를
만들지 않고 기존 "하나의 GET으로 전체를 준다" 패턴을 유지한다.

### D2/D4 — fleet: `RouteGraph` 신설, `LaneGraph` 대체

```java
// LaneGraph의 plan()/planByNode()/segmentAt() 시그니처는 유지 (호출부 무변경)
// 내부만 컴파일 상수 → LocationRegistry가 캐시한 그래프 탐색으로 교체
class RouteGraph {
    RoutePlan plan(double[] from, double[] to);          // 내부: A* over 캐시된 노드-엣지
    RoutePlan planByNode(double[] from, String toNode);
    String segmentAt(double x, double y);                 // 세그먼트 id = 엣지 id 기준으로 재정의
    double edgeCost(String edgeId, Instant now);           // base_cost, 장애물이면 Double.POSITIVE_INFINITY
}
```

`TrafficController`의 자료구조(`Map<String,Long> reservations`, `progress`/`release`)는 **그대로
둔다** — 세그먼트 ID의 "의미"만 통로 밴드(`V:34:top`)에서 엣지 ID로 바뀔 뿐, 점유·해제·진행성
반납 로직은 손대지 않는다.

### D5 — `OrderService`: 실패 사유 구분

```java
enum ReserveFailure { OCCUPIED, BLOCKED }  // 신설
```
`tryReserve`가 boolean 대신 이 enum(or Optional)을 반환하도록 확장. `OCCUPIED`면 지금처럼 같은
경로로 재시도, `BLOCKED`면 `nextLegCache`를 무효화하고 `RouteGraph.plan()`을 다시 호출해서 새
경로를 받는다.

### D6 — 장애물 이벤트 소비

새 MQTT 구독 핸들러(`MqttMessageHandler.handleObstacle()` 계열) → `FleetEventType.LAYOUT_OBSTACLE_*`
로 `fleet_events`에 기록 → Redis에 `blocked:{edgeId}` 키 set/delete(TTL = `validUntil` 계산값) →
`RouteGraph.edgeCost()`가 이 키를 읽는다.

### D9 — 배차 정책 그래프 비용화 (신설 항목, `NearestBatteryAwareAssignmentPolicy` 대체)

현재 직선거리(`distanceSquared`)로 후보를 고르는 걸 그래프 비용으로 바꾼다. `AssignmentPolicy`가
이미 인터페이스로 분리돼 있어(1절 리서치 확인) `OrderService` 변경 없이 구현체만 교체 가능하다.

```java
class GraphCostAwareAssignmentPolicy implements AssignmentPolicy {
    // distanceSquared(robot, origin) 대신 routeGraph.plan(robot.pos, originNode).cost() 사용
    // 후보 로봇 수가 적어(수십 대 수준) 후보별 A* 호출 비용은 무시할 만하다
}
```

기존 정책은 그대로 남겨 설정으로 전환 가능하게 한다(회귀 시 즉시 롤백 수단).

---

## 5. 실행 단계 (각 단계 검증 후 커밋 — P19 선례를 따름)

### P20-1. 데이터 모델 ✅ (2026-08-06)
- [x] factory Flyway V13 — `layout_edges`, `layout_nodes.node_type`에 `GATE`/`JUNCTION` 추가,
      `layout_settings.layout_version/effective_from` — `V13__layout_edges.sql` 적용 확인
      (버전은 물리 배치 변경이 아니므로 그대로 1 유지, D8 그대로)
- [x] 기존 3개 건물의 통로·연결로를 `layout_edges` 행으로 이관 (값 동일, 표현만 이동) —
      연결로 8개 × 통로 2개 = 교차점(JUNCTION) 노드 16개 신설, 엣지 54개(교차점 간 8+14,
      명명 노드→교차점 32). `GET /api/layout` 검증: WH-DOCK-1→PROD-A1 경로 비용이
      기존 `LaneGraph.plan()` 계산값(6+23+3=32)과 정확히 일치(6+5+5+13+3=32)
- [x] `LayoutResponse`/`LayoutService`에 `edges` 필드 추가, `GET /api/layout` 응답 확인 —
      노드 42개(기존 26 + 신규 JUNCTION 16), 엣지 54개로 응답됨을 실행 후 확인
- [x] 회귀 확인 — `oee-service` 전체 테스트 통과, `robot-sim`의
      `NodeMapLayoutConsistencyTest`는 V12 SQL만 비교 대상이라 영향 없음(통과 확인)

> 노드-교차점 분해 방식(수직 밴드를 JUNCTION 노드로 명시화)은 설계 문서 초안에는 없던
> 구현 결정이다 — `layout_edges`가 노드-노드 그래프(D3)이려면 통로·연결로 교차점도
> 실제 노드 행이 있어야 A*가 지나갈 자리가 생긴다. 상세 근거는 `V13__layout_edges.sql`
> 파일 상단 주석 참고.

### P20-2. 그래프 라우팅 교체 (기존 3개 건물 범위 안에서 회귀 없이) ✅ (2026-08-06)
- [x] `LocationRegistry`가 `edges`까지 캐시 — 폴백(42 노드+42 엣지, factory 미기동 대비)도 함께 갖춤
- [x] `LaneGraph`를 다익스트라 그래프 탐색으로 교체, 호출부(`OrderService`, `RobotService`) 무변경 —
      `RouteGraph`로 새로 만들지 않고 클래스명은 그대로 유지(시그니처 동일: `plan`/`planByNode`/
      `segmentAt`/`describe`)
- [x] `TrafficController` 자료구조는 무변경 — 구간 ID를 기하학적으로 계산(연결로 x + 통로 기준
      top/mid/bot, AU/AL:구간)해서 옛 문자열 형식과 완전히 같게 재현 (P20-1 엣지가 대역 하나씩과
      정확히 대응하도록 만들어졌기 때문에 가능)
- [x] 단위 테스트(`LaneGraphTest`, 6케이스) — 옛 `LaneGraph.plan()` 손계산값과 비용·웨이포인트·
      구간 ID 일치 확인. 이 모듈 최초의 테스트(리서치 확인: 이전엔 하나도 없었다)
- [x] 실사용 회귀 확인 — factory+fleet+robot-sim(로봇 8대) 실제 기동, `LocationRegistry`가
      `GET /api/layout`에서 42노드/42엣지 소스 로드 확인, `DemoTaskGenerator`가 만든 실제 주문을
      배차→경로계산→GOTO 전송까지 수행. 로그에 찍힌 구간 ID(`V:4:top`, `AU:14-27` 등)가 옛 형식과
      동일, 교통정리 충돌("approach segments busy")도 정상 동작. 예외/에러 없음

> **진입점(anchor) 설계 중 발견해 고친 문제 2건** (설계 문서엔 없던 구현 결정):
> 1. 로봇의 실시간 좌표가 그래프 노드가 아닐 때, 가장 가까운 연결로의 <b>교차점에만</b> 잇게
>    하면 같은 연결로의 다른 명명 노드(예: WH-DOCK-1↔WH-DOCK-2)로 가는 짧은 이동도 교차점을
>    거치는 먼 길로 계산됐다 — 같은 연결로 위 <b>가장 가까운 이웃 노드</b>(교차점이든 명명
>    노드든)에 잇도록 고쳤다.
> 2. 구간 ID 계산에 로봇의 실좌표를 그대로 쓰면(스냅 전) 대각선이 돼 분류가 깨졌다 — 웨이포인트는
>    실좌표를, 구간 ID는 스냅된 연결로 좌표를 쓰도록 둘을 분리했다(`PathStep.pos` vs
>    `PathStep.segmentPos`).

### P20-3. Building-A/B 신설 (데이터만으로) ✅ (2026-08-06)
- [x] 신규 건물 좌표 구획 확정 — 기존 건물 아래·옆이 아니라 <b>x를 멀리 떼어(85~)</b> 오른쪽에
      배치. 캔버스 확장(width 68→150). 이유: `LaneGraph`의 가상 진입점 탐색이 로봇의 실시간
      좌표를 처리할 때 "가장 가까운 연결로"를 <b>x만 보고</b> 전역에서 찾는다(y는 안 봄) —
      새 건물을 바로 옆(y만 다르게)에 붙이면 옛 건물 로봇의 진입점 탐색이 멀리 있는 새
      연결로를 더 가깝다고 잘못 고를 위험이 있다(P20-2 회귀). x를 충분히 떼면(기존 최대
      62, 신관 시작 85, 간격 23) 코드를 안 건드리고 데이터 배치만으로 이 위험을 없앤다 —
      진짜 자유로운 x·y 배치를 하려면 진입점 탐색을 건물 소속 기준으로 바꿔야 한다(범위 밖)
- [x] `layout_buildings`(BLDG-A/B) · `layout_floors` · `layout_nodes`(GATE 2개 + STATION/
      WAREHOUSE 4개) · `layout_edges`(6개, QC-OUT→GATE-WH-A→MACH-1→MACH-2→GATE-A-B→
      ASM-1→LOGI-1 체인) — factory Flyway V14 하나로 끝. **fleet 코드(LaneGraph,
      LocationRegistry) 무변경** — 이번 phase의 핵심 주장을 실제로 증명
- [x] `layout_settings.layout_version` 2로 상승(물리 배치가 실제로 바뀐 첫 마이그레이션,
      D8 그대로: V13은 표현만 옮겨서 안 올렸고 이번엔 진짜 바뀌어서 올림)
- [x] 로봇이 게이트 2개를 거쳐 건물 간 이동하는 경로가 실제로 계산됨 — factory+fleet+
      robot-sim 실기동 후 `POST /api/tasks`로 `QC-OUT → LOGI-1` 주문을 직접 생성해 확인:
      `LocationRegistry`가 48노드/48엣지 로드, 배차→AMR-03 할당→로봇이 실제로 QC-OUT까지
      이동 후 LOGI-1까지의 다음 스텝(1 waypoints — 전 구간이 일직선이라 꺾인점이 없어서
      1개로 압축됨, 의도한 동작)을 받아 완료, 예외 없음
- [x] 회귀 확인 — `oee-service`·`control-service` 전체 테스트 스위트 통과(`LaneGraphTest`
      포함, P20-2 검증 케이스 무변경으로 계속 통과)

> `DemoTaskGenerator.FLOWS`에 `QC-OUT → LOGI-1` 한 줄을 추가했다 — 이건 P20-1에서 이미
> "새 건물 추가 시 흐름 몇 줄을 손으로 더하는 정도는 회귀가 아니다"로 정리한 항목이라,
> 라우팅 코드 무변경 원칙과 별개다(데모 시나리오 데이터일 뿐, 라우팅 알고리즘이 아니다).

### P20-4. 동적 장애물 ✅ (2026-08-06)
- [x] `fleet/layout/{buildingCode}/obstacle` 토픽(`MqttMessageHandler`, 세그먼트 4개로 로봇
      스코프와 구분) / `FleetEventType.LAYOUT_OBSTACLE_ADDED`·`_CLEARED` / `ObstacleStore`
      (Redis, TTL 기반 — CLEARED 유실돼도 자연 만료). 문서: `modules/pixel-fleet/docs/mqtt-topics.md`
- [x] `ObstacleSimulator`(robot-sim) — 10초마다 확률적으로 하드코딩된 엣지 목록(`DemoTaskGenerator.
      FLOWS`와 같은 원리로 손으로 든 실재 엣지 목록, P20-3 신관 엣지 포함) 중 하나를 20~40초
      막았다 푼다. 수동 트리거 대신 주기적 랜덤을 택함(사용자 원 요청: "시뮬레이터가 동적으로
      반영")
- [x] `LaneGraph`의 다익스트라가 막힌 엣지를 완화 단계에서 건너뜀(비용 무한대와 동치, 코드는
      더 단순). `OrderService.nextLegCache`는 애초에 "순수 캐시 — 잃어도 다시 계산" 전제로
      만들어져 있어서(코드 주석 확인), 설계 문서가 제안한 `ReserveFailure`(OCCUPIED/BLOCKED)
      enum 없이 **장애물 이벤트 시 캐시 전체를 비우는 것만으로 충분** — 다음 재시도에서
      자연히 새로 계산되어 막힌 엣지를 피한다. 설계보다 단순하게 끝난 부분
- [x] 실사용 검증(factory+fleet+robot-sim 실기동, `mosquitto_pub`으로 결정론적 장애물 주입):
      WH-DOCK-1→WH-DOCK-3(같은 연결로, 중간 대역 통과 필요) 경로의 중간 대역 엣지를 막자
      실제로 인접 연결로(9번)를 거치는 5-웨이포인트 우회 경로로 재계산됨을 확인(막히기 전엔
      직선이라 1개였을 경로). 일반 교통 정체("Traffic: ... busy")와 장애물 차단("Layout: edge
      ... blocked")이 로그에서 분명히 구분됨. 예외 없음
- [x] 단위 테스트 3건 추가(`LaneGraphTest`) — 막힌 엣지 우회, 무관한 엣지는 영향 없음,
      `canonicalEdgeId` 방향 무관성. `ObstacleStore`는 Mockito로 대체(Redis 불필요)
- [x] 실사용 중 발견한 별도 결함 하나 고침 — robot-sim의 하트비트(로봇 8대 × 텔레메트리 3종
      = 24건 동시 발행)가 `ObstacleSimulator`의 주기적 발행과 겹치며 Paho MQTT 클라이언트의
      기본 `maxInflight`(10)를 넘겨 "Too many publishes in progress"로 조용히 드롭되는 걸
      실측(자가 복구되므로 크래시는 없었지만 근본 원인을 남겨두지 않음) — `maxInflight`를
      50으로 올려 해결
- [x] `NodeMapLayoutConsistencyTest` 재검토 — **비교 대상을 옮길 필요가 없다고 판정.** 클래스
      문서가 이미 "평면도를 **다시 그리는**(재정의) 마이그레이션에서만 경로를 옮긴다"고
      명시하는데, V13(그래프 데이터 모델)·V14(신관 추가)는 둘 다 기존 V12 노드를 **삭제·재정의
      하지 않고 순수 추가만** 했다 — robot-sim의 NodeMap이 원래도 몰랐던 JUNCTION/GATE/
      새 건물 노드들이라 subset 검사가 실패할 이유가 없다. 실제로 P20-1/2/3/4 내내 무변경으로
      계속 통과 확인. 장애물은 애초에 factory DB(`layout_edges`)를 건드리지 않으므로(D4)
      이 항목과 아예 무관하기도 하다

### P20-5. 배차 비용 그래프화
- [ ] `GraphCostAwareAssignmentPolicy` 구현, 설정으로 기존 정책과 전환 가능
- [ ] 장애물 존재 시 "직선 최근접"과 다른 로봇이 선택되는 걸 확인하는 케이스 추가

---

## 6. 리스크 & 롤백

- **데드락 규율 훼손 위험** — `TrafficController`/`OrderService`의 "release-before-request" 규율은
  로봇 4대가 동시에 멈춘 실제 장애를 겪고 나서 잡은 것(1절 리서치 확인). 세그먼트 ID 체계를
  바꾸는 P20-2에서 이 규율을 실수로 깨지 않도록, 기존 통합 테스트(있다면)·데모 24시간 관찰을
  P20-2 완료 기준에 추가한다.
- **A* 재계산 비용** — 그래프가 작아(노드 수십 개) 매 호출 비용은 미미하지만, `grantPendingNextLegs`가
  2초마다 미해결 leg를 전부 재계산하면 로봇 수 × 대기 leg 수만큼 호출이 늘어난다. 필요시
  `nextLegCache`처럼 "장애물 이벤트가 없으면 재계산 안 함" 캐시를 유지한다.
- **robot-sim 조용한 이탈** — `NodeMap`이 fleet DB에 의존하지 않는 원칙은 유지하되, 정합성 테스트
  대상을 못 바꾸면 새 건물/엣지에 대해 아무 것도 검증 안 하는 죽은 안전망이 된다. P20-1 완료
  기준에 테스트 갱신을 반드시 포함한다.
- **롤백 수단**: `RouteGraph`/`LaneGraph`는 같은 시그니처를 유지하므로 문제 시 구현체만
  되돌리면 된다. `GraphCostAwareAssignmentPolicy`도 설정 토글로 즉시 되돌린다.

---

## 7. 완료 기준 (전체)

- [ ] 기존 3개 건물(WH/PROD/QC) 데모 시나리오가 회귀 없이 그대로 동작한다
- [ ] Building-A/B가 코드 변경 없이 데이터 추가만으로 편입되고, 게이트를 거쳐 실제로 로봇이 이동한다
- [ ] 시뮬레이터가 임의 엣지를 막으면 진행 중 로봇이 그 엣지를 피해 재경로하고, 이는 단순 교통
      대기(다른 로봇이 앞에 있음)와 구분된다
- [ ] 장애물 상태는 factory DB에 쓰이지 않고 fleet Redis에만 존재한다(DB per module 확인)
- [ ] 배차가 장애물 상황에서 "직선 최근접"이 아니라 실제 경로 비용 기준으로 로봇을 고른다

---

## 8. 이번에 안 하는 것 (범위 밖 → 후속 항목)

- **완전 독립 로컬 좌표계(오프셋 합성)** — D1에서 기각. 다른 팀이 좌표를 몰라도 건물을 설계할
  필요가 생기면 별도 항목으로.
- **자유형태 장애물 회피(연속 좌표계 다각형)** — 백로그 P20 원문에 이미 명시된 비목표. 엣지
  단위 차단으로 충분히 "동적으로 반영된다"는 요구를 만족한다.
- **`DemoTaskGenerator.FLOWS`의 데이터 기반 자동 도출** — 지금처럼 수동 목록 유지. 새 건물 추가
  시 흐름 몇 줄을 손으로 더하는 정도는 회귀가 아니다.
- **과거 시점 평면도 재생 UI** — `layout_version`은 기록만 하고 소비하는 화면은 안 만든다.
- **M4(P19) API 봉투와의 통합** — 이 문서는 라우팅/토폴로지만 다룬다. P19의 스텝/봉인 개념과는
  독립적으로 진행 가능하다(서로 다른 계층).

---

## 9. 예상 난이도

체감 난이도는 P19(스텝 일반화)보다 높다 — 배차·교통통제·robot-sim 세 곳 모두에 손이 닿고,
"교통 대기 vs 장애물 재탐색"을 헷갈리면 조용히 잘못 동작하는 종류의 버그(둘 다 "로봇이 안
움직인다"로 보임)가 나올 수 있다. P20-2(그래프 교체, 기존 3건물 범위)까지가 가장 위험한
구간이니 그 단계에서 회귀 검증에 가장 시간을 쓴다. P20-3(건물 추가)은 설계가 맞으면 실제로는
데이터 작업이라 상대적으로 가볍다.
