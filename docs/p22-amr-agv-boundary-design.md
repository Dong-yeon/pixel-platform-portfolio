# P22 설계 문서 — AMR/AGV 경계 확정 (창고동 1층 AMR 진입 차단 + 랙 피더의 AGV 확장)

> 상태: **구현 + 로컬 검증(Gradle 테스트, `tsc`/`vite build`) 완료, 배포 검증 대기.**
> `docs/p21-warehouse-rack-feeder-design.md`와 같은 형식. 실행 단계(5절)에 진행 상황을 남긴다.
>
> 선행 문서: P20(그래프 라우팅, `LaneGraph`/`TrafficController`) · P21(창고동 렉 취출 로봇 —
> 여기서 "랙 피더"라는 이름과 `RACK_FEEDER` 로봇 종류가 처음 생겼다). 이 문서는 P21이 만든
> "렉 전용, 존 밖으로 못 나가는 로봇"이라는 개념을 **창고동 1층 전체**로 넓히고, 이름을
> `AGV`로 일반화한다.

---

## 0. 요청 원문과 범위

> "AMR이 창고동 까지는 들어오지 않는 구조", "창고동에서는 AMR모다는 AGV가 움직여야할꺼같아"

P21은 "렉 → 피킹존" 구간만 랙 피더에게 맡겼다 — 창고동 1층의 나머지(입고장·출하장·도크·
엘리베이터 승강장)는 여전히 AMR이 `LaneGraph`를 타고 자유롭게 드나들었다. 이번 요청은 그
경계를 창고동 **건물 전체**로 넓혀 달라는 것이다:

1. **AMR은 창고동(WH) 1층 안쪽에 아예 들어가지 않는다** — 지금까지처럼 렉만이 아니라
   입고장(`WH-RECV`)·출하장(`WH-SHIP`)·도크(`WH-DOCK-*`)·엘리베이터 승강장(`WH-ELEV-1F`)도
   포함한다.
2. **AGV**(P21의 "랙 피더"를 여기서부터 이 이름으로 부른다)가 그 안쪽을 전부 맡는다 — 로컬
   직선 이동으로, `LaneGraph`에는 여전히 올라가지 않는다(P21 D2 그대로 유지).
3. **AMR↔AGV 경계는 게이트 노드로 물리적으로 고정한다** — WH 건물과 PROD 건물 사이, 지금
   두 구역을 잇던 직통 연결로를 게이트에서 한 번 끊는다.
4. **2층/3층은 범위 밖**이다 — P21의 좁은 피킹존 단위 존(zone)은 그대로 유지한다(P21 D10).
   1층만 "창고동 전체가 하나의 넓은 존"으로 확장한다.

---

## 1. 왜 렉만으로는 부족한가

P21 완료 후에도 AMR은 여전히 `WH-RECV`(입고장)·`WH-SHIP`(출하장)·`WH-DOCK-1~4`(도크)·
`WH-ELEV-1F`(엘리베이터 승강장)를 `LaneGraph` 경로로 자유롭게 오갔다 — 이 노드들은 렉이
아니므로 P21의 `isRackCode` 분기에 걸리지 않는다. 창고동은 도면상 "AGV만 다니는 건물"이
아니라 "렉만 AGV, 나머지는 AMR도 드나드는 건물"이었다.

사용자가 원하는 것은 **건물 단위의 물리적 분리**다 — 창고동은 AGV의 영역이고, AMR은 PROD/QC
건물과 그 사이 연결로까지만 다닌다. 지금 구조는 그 경계가 "렉이냐 아니냐"라는 노드 단위
판정으로 흩어져 있어 창고동 안에서도 AMR과 AGV가 같은 통로를 지나갈 수 있었다 — 사용자가
말한 "아예 다니는 곳이 다르다"는 전제가 지켜지지 않았다.

---

## 2. 핵심 원칙 재확인

| 원칙 | 이 설계가 지키는 방법 |
|---|---|
| 컴포저블 — 모듈 간 직접 참조 금지 | factory는 여전히 좌표·노드 종류(`GATE` 포함)만 안다. AMR/AGV 구분은 fleet에만 존재한다 |
| DB per module | 변경은 fleet(`robots`/`fleet_orders`)과 factory(`layout_nodes`/`layout_edges`)에 한정. WMS는 무변경(D9, P21에서 확정된 경계 유지) |
| 없는 데이터를 시각효과로 지어내지 않는다 | 게이트는 실제 그래프 노드(`GATE` 타입, 실좌표)로 만든다 — 장식이 아니라 라우팅에 실제로 쓰이는 노드다 |
| 전례 있는 패턴 재사용 | 로봇 풀 경계 일반화(P21 D5의 `requiredPool`/`handoffNodeFor`)를 그대로 확장한다 — 새 상태 기계를 만들지 않는다 |

---

## 3. 목표 구조

```
   WH 건물 (x: 1~41, AGV 전용 1층 내부)              PROD 건물 (x: 45~, AMR)
   ┌─────────────────────────────────┐            ┌─────────────────────
   │ WH-RECV  WH-1F-R01..R09  WH-SHIP │            │
   │ WH-DOCK-1..4   WH-ELEV-1F        │  게이트     │  PROD-DOCK-1..4
   │        (AGV 로컬 이동, D2)        │  x=43      │   (AMR, LaneGraph)
   │                             JCT-14 ─ WH-GATE-U ─ JCT-27 │
   │                             JCT-14 ─ WH-GATE-L ─ JCT-27 │
   └─────────────────────────────────┘            └─────────────────────
```

- **`WH-GATE-U`/`WH-GATE-L`**: 새 `GATE` 타입 노드. `LaneGraph` 상에서는 평범한 그래프 노드
  (AMR 경로가 여기까지는 들어온다) — 하지만 `requiredPool`은 게이트를 **항상 AMR 풀**로
  판정한다(4절 D2). AGV는 게이트까지 로컬 이동으로 나와서 여기서 AMR에게 인계한다.
- **1층 전체가 하나의 AGV 존**: P21의 좁은 피킹존(`WH-2F-P1` 등) 대신, 1층은 기존에 이미
  쓰이던 `WH-PICK` 존 코드 하나로 통일한다(새 상수를 만들지 않고 기존 관례 재사용 — 9기 전
  1층 렉이 이미 `nearestPickNode`로 `WH-PICK`을 반환하고 있었다).
- **엘리베이터 예외**: `WH-ELEV-1F`는 1층 AGV 존 안쪽이지만, 화물 엘리베이터는 층 경계이지
  건물 경계가 아니다 — 1층 AGV가 엘리베이터까지 접근하는 것은 허용한다(D3). 반대로 2·3층
  AGV(좁은 존)는 P21 그대로 층 경계 이동이 계속 금지된다.

---

## 4. 확정 결정 (D1 ~ D6)

### D1. 이름 변경 — `RACK_FEEDER` → `AGV`, "랙 피더" → "AGV"

로봇 종류가 더 이상 "렉 전용"이 아니라 "창고동 1층 전체 전용"으로 넓어졌으므로, P21에서
쓰던 이름 `RACK_FEEDER`/"랙 피더"는 더 이상 실제 역할을 정확히 설명하지 못한다. `AGV`
(Automated Guided Vehicle)로 바꾼다 — 업계에서 AMR(자율주행, 동적 경로탐색)과 대비되는
표준 용어이기도 하다.

- `RobotType` enum: `AMR` | `AGV` (fleet `robots.robot_type`/`fleet_orders.robot_type`,
  Flyway V11로 기존 `RACK_FEEDER` 로우를 `AGV`로 일괄 변경 — 스키마 컬럼 자체는 무변경).
- 로봇 코드 `RF-01..06` → `AGV-01..06`(표시명도 "N층 AGV n호"로 갱신).
- 코드 주석·문서에서 "랙 피더"는 첫 등장에 `AGV(옛 이름: 랙 피더)`로 한 번만 밝히고, 이후는
  `AGV`로 통일 — 과거 이력(P21 문서, 이미 적용된 마이그레이션 파일명)은 그대로 둔다(마이그
  레이션은 실행된 역사적 사실이라 이름을 바꾸지 않는다, `docs/` P21 문서도 "그 시점의 이름"
  이므로 무수정).

### D2. 게이트 노드 — WH↔PROD 경계를 물리적으로 고정

```sql
-- 기존: JCT-14-U ↔ JCT-27-U (cost 19) 직통 연결
-- 이후: JCT-14-U ↔ WH-GATE-U (cost 13) + WH-GATE-U ↔ JCT-27-U (cost 6)
```

- 게이트는 WH 건물(x≤41)과 PROD 건물(x≥45) 사이 중립지대(x=43)에 둔다. 비용 분할(13+6=19)은
  기존 직통 비용과 정확히 같다 — 게이트 도입이 **다른 라우팅 비용에 아무 영향을 주지 않는다.**
- `layout_nodes.node_type = 'GATE'`로 새 타입을 추가한다(기존 `DOCK`/`JUNCTION`/... 옆).
  `LaneGraph`에게는 평범한 노드다 — 특별 취급이 필요 없다(D2가 라우팅 엔진을 안 건드린다는
  P21 원칙을 그대로 지킨다).
- `requiredPool`이 게이트 노드를 만나면 **항상 AMR 풀**을 반환한다 — AGV가 창고동 밖으로
  나가는 경로 자체가 아예 생기지 않는다(AGV는 게이트까지만 로컬 이동하고, 그 다음은 항상
  handoff로 AMR에게 넘긴다).
- **AMR의 창고동 진입 자체를 막는 장치는 두지 않는다** — 대신 `requiredPool`이 창고동
  1층 내부 노드 전부를 AGV 풀로 판정하므로(D4), AMR에게 그 안쪽으로 가는 주문 자체가
  배차되지 않는다. `LaneGraph`가 물리적으로 "AMR 진입 금지"를 강제하는 게 아니라, **주문
  배차 단계에서 AMR에게 그 목적지가 아예 주어지지 않는다** — P21부터 이어지는 "그래프는
  안 건드리고 배차 필터로 경계를 만든다"는 패턴 그대로다.

### D3. 1층은 넓은 단일 존, 2·3층은 P21 그대로 좁은 존

```java
private static final String WH_1F_ZONE = "WH-PICK";  // 1층 전체를 가리키는 존 코드
```

- 1층 렉·입고장·출하장·도크·엘리베이터 승강장 — 전부 `zoneCode = WH-PICK`인 AGV 풀로
  판정한다. 2·3층은 P21 그대로 `nearestPickNode`가 계산한 좁은 피킹존(`WH-2F-P1` 등)을
  쓴다 — 이번 확장은 **1층에 한정**한다(0절 범위).
- **층 넘는 경계 처리가 갈린다**: 2·3층 AGV는 P21 D5의 제약(자기 존 밖인 엘리베이터로 못
  간다 → `INVALID_REQUEST`)을 그대로 물려받는다. 1층 AGV는 `WH-ELEV-1F`가 이미 자기 존
  안쪽 노드이므로 엘리베이터까지 가는 것 자체는 허용된다 — 다만 그 다음(엘리베이터를 타고
  다른 층으로 실제 이동)은 여전히 화물 전용이지 AGV가 층을 넘는 게 아니다. 판정 로직은
  "출발 풀이 AGV이고 1층이 아니면 거부, 1층이면 허용"이라는 층 기반 분기로 구현한다
  (`origin.robotType()==AGV && origin.floorNo()!=1` → 거부).

### D4. `LocationRegistry`에 "창고동 1층 내부 노드" 판정 추가

```java
private static final Pattern WH_1F_INTERIOR_PATTERN =
        Pattern.compile("^WH-(?:DOCK-[1-4]|RECV|PICK|SHIP|ELEV-1F)$");

public boolean isWarehouseFloor1Node(String code) { ... }
```

`WH-GATE-*`는 **의도적으로 뺐다** — 게이트는 D2대로 항상 AMR 풀이다. 2·3층 노드도 뺐다 —
범위 밖이라 계속 AMR이 못 들어가는 게 아니라 애초에 이 판정과 무관하다(2·3층은 렉만
AGV, P21 그대로).

### D5. AMR 도크 이전 — `WH-DOCK-*` → `PROD-DOCK-1..4`(신설)

AMR이 창고동에 못 들어가면 지금까지 쓰던 `WH-DOCK-1..4`(창고동 안)로 충전하러 갈 수 없다.
PROD 건물 첫 커넥터 옆에 `PROD-DOCK-1..4`를 새로 만들어(기존 `WH-DOCK`의 y-오프셋
3/5/21/23 그대로 재사용) AMR의 홈 도크를 옮긴다. `WH-DOCK-1..4`는 이제 AGV 전용이다.

### D6. robot-sim — 게이트·이원 도크 반영

- `NodeMap`: 게이트·`PROD-DOCK-*` 노드 추가, 도크를 `DOCKS_AGV`/`DOCKS_AMR`로 분리,
  `nearestDock(x, y, agv)`/`randomRoamNode(rng, floor, agv)`로 시그니처에 `agv` 플래그 추가.
  1층 AMR의 로밍 후보 노드에서 `WH-RECV/PICK/SHIP/ELEV-1F`를 제거(P22 이후로는 AMR이 갈 수
  없는 자리이므로).
- `Simulator`/`ObstacleSimulator`: `isRackFeeder` → `isAgv`, 문자열 비교 `"RACK_FEEDER"` →
  `"AGV"`. `BLOCKABLE_EDGES`의 옛 `JCT-14↔JCT-27` 직통 항목을 게이트를 낀 두 구간으로 교체.

---

## 5. 실행 단계

### P22-1. 데이터 모델
- [x] factory Flyway V17(전체 재작성, `layout_nodes`/`layout_edges`만 — V9→V12→V15→V16→V17
      관행 그대로) — 게이트 2개 + `PROD-DOCK-1..4` 4개 추가, `JCT-14↔JCT-27` 직통 간선을
      게이트를 통한 두 구간으로 교체(비용 합 19 그대로 보존)
- [x] fleet Flyway V11 — `RACK_FEEDER` → `AGV` 로봇 종류·주문 일괄 변경, `RF-0N` →
      `AGV-0N` 로봇 코드 변경
- [x] `RobotType` enum 값 변경(`RACK_FEEDER` → `AGV`), 관련 자바독 갱신
- [ ] 실기동 확인 — Railway 배포 후 `GET /api/robots`로 AGV 6대가 올바른 zoneCode로
      조회되는지, Flyway V17/V11이 실제로 적용됐는지 로그 확인 (배포 전이라 아직 미검증)

### P22-2. 주문 엔진 확장
- [x] `requiredPool`에 `isWarehouseFloor1Node` 분기 추가 — 1층 창고동 내부 노드는 렉이
      아니어도 AGV 풀(D4)
- [x] `handoffNodeFor`에 게이트 판정 추가(`nearestGate` — y좌표로 상/하 게이트 중 가까운
      쪽 선택) + 층 기반 1층/2·3층 AGV 구분(D3)
- [x] `createHandoffOrder`의 도착 레그 로봇 풀을 `requiredPool(startNode)`로 다시 계산하도록
      수정 — 기존엔 하드코딩된 AMR 5-인자 생성자를 썼는데, 1층 AGV가 엘리베이터까지 오는
      새 케이스에서 도착 레그가 AGV여야 하는 경우를 놓치는 버그였다(D3 처리 중 발견)
- [x] 회귀 검증 — 로컬 Gradle 테스트(Java 17→21 임시 전환) 실행, control-service 전체 통과.
      **검증 중 실제로 발견한 회귀**: `LaneGraphTest`의 두 테스트가 게이트 분할 전 좌표를
      가정하고 있었다 — `AU:30-49` 세그먼트를 기대했는데 실제로는 `AU:30-43`+`AU:43-49`로
      쪼개졌고(비용 합은 54.0으로 동일 — 6절 리스크가 우려한 "동일 비용" 자체는 확인됨),
      장애물 회피 테스트가 막던 엣지(`JCT-14-U`↔`JCT-27-U`)가 더 이상 존재하지 않아 차단이
      아무 효과가 없었다. 두 테스트를 게이트 분할 이후 구조에 맞게 수정 후 재실행, 전체 통과

### P22-3. robot-sim
- [x] `NodeMap`/`Simulator`/`ObstacleSimulator`/`VirtualRobot`/`SimProperties`/
      `application.yml` — 게이트·이원 도크·이름 변경 반영(D6)
- [x] `NodeMapLayoutConsistencyTest`(V17 대조)/`RackMapLayoutConsistencyTest`(무변경 대상,
      영향 없음 확인) 로컬 실행 — 전부 통과

### P22-4. 대시보드
- [x] `types.ts` — `robotType: 'AMR' | 'AGV'`, `LayoutNodeType`에 `JUNCTION`/`GATE` 추가
- [x] `UnifiedMap.tsx` — 로봇 마커 클래스 `umap-robot-agv-mark`로 이름 변경, 노드 렌더링에서
      `JUNCTION`/`GATE` 제외(따로 그리므로 일반 노드 박스로 중복 렌더 안 함), 게이트 전용
      "문" 마커 추가("AMR⇄AGV" 라벨)
- [x] `RobotPanel.tsx`/`OverviewView.tsx`/`FleetView.tsx` — 배지·레이어 토글 이름 변경
- [x] `TaskPanel.tsx` — 수동 작업 생성 드롭다운에서 `JUNCTION`/`GATE`도 제외(도크와 같은
      이유 — 실제 운송 목적지가 아니다)
- [x] `styles.css` — `.umap-robot-agv-mark` 이름 일치, 게이트 마커 스타일 추가
- [x] `tsc --noEmit`/`vite build` 로컬 검증 — 둘 다 에러 없이 통과

### P22-5. 배포 검증
- [ ] Railway 배포 후 V17/V11 Flyway 적용 로그 확인
- [ ] 대시보드에서 AGV가 창고동 1층 밖으로 못 나가는지, AMR이 창고동 1층 안쪽에 배차되지
      않는지 실측
- [ ] 1층 AGV의 엘리베이터 접근(D3 예외) 케이스, 2·3층 AGV의 층 경계 거부(D3 유지) 케이스
      각각 실기동 확인

---

## 6. 리스크 & 롤백

- **가장 위험한 지점은 D3(1층 예외 처리)다** — 2·3층의 기존 P21 제약(층 못 넘음)을 깨지
  않으면서 1층만 예외를 두는 분기라, 조건을 잘못 짜면 2·3층 AGV가 층을 넘거나 1층 AGV가
  여전히 엘리베이터를 못 쓰는 회귀가 생긴다. 로컬 테스트(P22-2 체크리스트)로 반드시 양쪽
  다 확인해야 한다.
- **게이트 비용 분할(D2)이 실제 라우팅 결과를 바꾸지 않는지**가 두 번째 위험 — 13+6=19로
  합은 보존했지만, 다익스트라 중간 경유지가 하나 늘어난 것 자체가 동점 경로 처리 등에서
  미세한 차이를 낼 가능성은 완전히 배제되지 않는다. `NodeMapLayoutConsistencyTest` 외에
  실제 라우팅 결과 비교는 배포 후 관찰 항목으로 남는다.
- **롤백 수단**: D1(이름 변경)은 P21 상태로 되돌리는 게 사실상 무의미(순수 리네임이므로
  기능 롤백이 아니다). D2~D5(게이트·1층 확장)를 되돌리려면 V17을 다시 V16으로, 또는 V17
  위에 원복 마이그레이션을 얹어야 한다 — `layout_nodes`/`layout_edges` 전체 재작성 관행상
  "V16으로 되돌리는 V18"을 만드는 방식이 된다.

---

## 7. 완료 기준

- [ ] AMR은 창고동 1층 내부(렉·입고장·출하장·도크·엘리베이터 승강장) 어디에도 배차되지
      않는다 — 항상 게이트에서 AGV에게 인계한다
- [ ] AGV는 창고동 1층 밖(게이트 너머)으로 나가지 않는다
- [ ] 1층 AGV는 엘리베이터를 이용한 층간 handoff를 할 수 있다(D3 예외)
- [ ] 2·3층 AGV는 여전히 층을 넘지 못한다(P21 D5/D10 그대로, 회귀 없음)
- [ ] 기존 P21 흐름(렉 → 피킹존 → AMR)이 이름만 바뀐 채(AGV) 그대로 동작한다
- [ ] 대시보드에서 게이트가 시각적으로 구분되고, AGV/AMR 마커가 올바로 렌더링된다

위 항목은 전부 로컬 구현·검증(Gradle 테스트, `tsc --noEmit`, `vite build`) 완료 상태이고,
실기동 확인은 아직 남아 있다(5절 P22-5 참고).

---

## 8. 이번엔 안 하는 것 (범위 밖)

- **2·3층 AGV의 존 확장** — 1층만 이번 범위. 2·3층을 넓히려면 별도 설계(층마다 물리적
  경계가 다를 수 있어 게이트 위치도 다시 잡아야 한다)가 필요하다.
- **게이트 혼잡 제어** — 게이트는 평범한 그래프 노드이므로 `TrafficController`의 구간 점유
  통제를 그대로 받는다(AMR 쪽). AGV가 게이트 앞에서 AMR을 기다리는 대기열 로직 같은 별도
  개선은 다루지 않는다.
- **AMR↔AGV 자동 적재 이관 애니메이션** — P21 D7과 같은 원칙(없는 데이터를 지어내지 않는다)
  으로, 게이트에서의 handoff도 기존 handoff 이벤트 로그로만 표현한다.
