# P25 설계 문서 — 로봇 규격을 라우팅에 강제한다 (통로폭 + 정격 적재량)

> 상태: **구현 + 실기동 검증 완료(2026-08-25).** 통로폭 반영 범위는 사용자 승인
> ("라우팅에 실제 강제")대로 진행했다. `docs/p20~p24-*.md`와 같은 형식.
>
> 선행 문서: P20(그래프 라우팅) · P21/P22(AGV는 `LaneGraph`에 안 올라간다) · P23(파렛트
> 단위 재고, 파렛트 총중량 500kg 검증) · P24(M4형 `orders/create`).
>
> 근거 자료: `amr 사양요구서.docx` §"AMR 통로 및 팔레트 요구사항"(§4.3) — 공차(空車)
> 단일 통로 ≥950mm/양방향 ≥1900mm, 적재 시 단일 ≥1400mm/양방향 ≥2800mm. §"AMR基础参数" —
> 정격 적재 600kg.

---

## 0. 요청 원문과 범위

`docs/BACKLOG.md`의 원래 P25 후보("로봇 규격 마스터")를 대화에서 확정: **통로폭을
`LaneGraph`의 실제 라우팅(다익스트라)에 강제 반영한다** — 데이터만 들고 있다가 대시보드에
표시만 하는 안(위험 낮음)과, 경로 탐색 자체가 좁은 구간을 피해 가게 만드는 안(위험 높음,
`LaneGraph`/`GraphCostAwareAssignmentPolicy` 동작이 실제로 바뀐다) 중 **후자로 결정됐다.**

P21/P22가 일관되게 "`LaneGraph`/`TrafficController`에는 손대지 않는다"를 반복해 온 이유는
그 부분이 P20에서 가장 검증에 공들인 곳이기 때문이다. 이번 작업은 그 원칙을 처음으로
깬다 — 대신 **최소 침습**으로 한다: 새 시그니처는 전부 하위호환 오버로드로 추가하고,
막는 조건 하나만 장애물 판정 옆에 나란히 추가한다(새 다익스트라를 만들지 않는다).

---

## 1. 핵심 관찰 — 왜 이게 생각보다 작은 변경인가

**`TrafficController`가 이미 구간을 배타적으로 잠근다.** 한 구간(segment)에는 로봇이
항상 하나만 있다(`tryReserve`가 "하나라도 다른 로봇이 쥐고 있으면 아무것도 안 잡고
실패"). 즉 이 그래프에서 "양방향 동시 통행"은 애초에 발생하지 않는다 — 사양서의
"단일 통로"/"양방향 통로" 구분 중 **단일 통로 폭 하나만** 실질적으로 의미가 있다.
그래서 임계값은 로딩 상태 2가지({@code 공차 950mm} / {@code 적재 1400mm})만 있으면 된다,
4가지(단일/양방향 × 공차/적재)를 전부 모델링할 필요가 없다.

**로딩 상태는 이미 `FleetOrder.loaded`로 추적되고 있다**(P19). 새 상태를 만들 필요가
없다 — 경로 계산 시점에 `order.isLoaded()`를 넘기기만 하면 된다.

**로봇 규격은 상수로 충분하다.** 이 포트폴리오의 AMR은 전부 EMMA 600K 한 모델이다(P23의
전제와 동일 — "벤더는 하나로 고른다"). `GraphCostAwareAssignmentPolicy`가 이미
`MIN_BATTERY_PERCENT = 25`를 로봇별 필드가 아니라 정책 상수로 두고 있다 — 같은 패턴을
그대로 따른다. 로봇마다 다른 폭을 갖는 이기종 함대를 모델링하는 건 지금 이 포트폴리오가
증명하려는 것 밖이다.

---

## 2. 핵심 원칙 재확인

| 원칙 | 이 설계가 지키는 방법 |
|---|---|
| factory는 좌표·기하학적 사실만 | 엣지 폭(`width_mm`)은 통로의 물리적 사실이다 — factory가 갖는 게 맞다. 로봇 개념은 여전히 안 스며든다 |
| 컴포저블 | `LaneGraph.plan()`/`planByNode()`는 **오버로드 추가**로만 확장한다 — 기존 시그니처·기존 테스트 6건 무변경 |
| 없는 데이터를 지어내지 않는다 | 실측 통로폭이 없다 — 전부 2000mm(공차 950/적재 1400 둘 다 넉넉히 웃도는 값)로 시드하고, 이유를 마이그레이션에 명시한다. 강제 로직의 정확성은 실측 데이터가 아니라 전용 테스트로 증명한다 |
| DB per module | `layout_edges.width_mm`(factory) 신설. fleet DB 스키마 변경 없음 — `LocationRegistry`(인메모리 캐시)만 확장 |

---

## 3. 목표 구조

```
factory                         fleet

layout_edges                    LocationRegistry.Edge(to, cost, widthMm)
├─ width_mm (신규)                 │  refresh()가 GET /api/layout에서 파싱
└─ (기존 필드 무변경)                │  (없으면 무제한 — 구버전 factory 호환)
        │                          ▼
   GET /api/layout            LaneGraph.plan(from, to, loaded)  ← 오버로드 추가
   {edges: [..., widthMm]}       │
                                  ▼
                             dijkstra() 완화 단계:
                               장애물 판정(기존) 옆에
                               폭 판정 한 줄 추가
                               (edge.widthMm() < requiredWidth(loaded) → skip)
                                  │
                                  ▼
                             OrderService.planLeg/grantNextLeg/tryDispatch
                               order.isLoaded()를 그대로 넘긴다(신규 상태 없음)
                             GraphCostAwareAssignmentPolicy.routeCost
                               항상 미배차 주문이라 loaded=false
```

---

## 4. 확정 결정 (D1 ~ D7)

### D1. factory `layout_edges.width_mm` 신규 — 전부 2000mm로 시드

```sql
alter table layout_edges add column width_mm integer;
update layout_edges set width_mm = 2000;
alter table layout_edges alter column width_mm set not null;
```

- 2000mm는 사양서의 두 임계값(공차 950 / 적재 1400)을 **둘 다 넉넉히 웃돈다** — 지금
  데모 레이아웃의 모든 통로가 "AMR이 다니기 충분히 넓다"는, 실측 없이도 방어 가능한
  가정이다. **의도적으로 좁은 구간을 인위로 만들지 않는다** — 만들면 기존 데모 트래픽
  (배경 22개 흐름 등)이 예기치 않게 막힐 위험이 있고, 그건 "라우팅 검증"이 아니라
  "회귀 유발"이다. 강제 로직이 실제로 동작하는지는 5절의 전용 테스트로 증명한다.
- `not null`로 잠근다 — DB에 값이 없는 엣지가 조용히 "무제한 통과"로 해석되면 나중에
  누군가 실수로 폭을 안 채운 엣지를 심어도 아무도 못 알아챈다. 대신 **와이어 프로토콜
  (JSON 응답)에서는 필드가 없어도 되게** 한다(D3) — 구버전 factory와의 호환을 위해서다.

### D2. `LayoutResponse.Edge`에 `widthMm` 노출

```java
public record Edge(String fromNode, String toNode, double baseCost, boolean bidirectional, int widthMm) {
    public static Edge from(LayoutEdge edge) {
        return new Edge(edge.getFromNode(), edge.getToNode(), edge.getBaseCost(),
                edge.getBidirectional(), edge.getWidthMm());
    }
}
```

### D3. fleet `LocationRegistry.Edge`에 `widthMm` 추가 — 없으면 무제한

```java
public record Edge(String to, double cost, double widthMm) {}
```

- `refresh()`가 `edge.path("widthMm").asDouble(Double.MAX_VALUE)`로 파싱한다 — **필드가
  없으면(구버전 factory) 무제한 취급**, 최근 `racks` 필드를 안 주는 구버전 factory를
  폴백으로 봐주는 것과 같은 패턴(P21 D3).
- `FALLBACK_EDGES`(factory가 죽었을 때 쓰는 마지막 안전망, D1 참고)도 **무제한**으로 둔다
  — factory가 죽은 상황에서 폭 데이터까지 없다고 로봇을 세우는 건 과하다("factory가
  죽어도 마지막으로 받은 좌표로 계속 동작해야 한다", P21 D3와 같은 원칙).
- `LaneGraph`의 가상 노드(anchor) 접근 엣지(로봇의 실시간 좌표 → 가장 가까운 연결로)도
  **무제한**이다 — 실제 DB 엣지가 아니라 매 호출 임시로 만드는 국소 연결이라, 장애물
  판정이 이 엣지를 건너뛰는 것과 같은 이유(P20-4 계약, `LaneGraph` 클래스 문서 그대로).

### D4. `LaneGraph`에 로딩 상태별 최소폭 상수 — 로봇별 필드 아님

```java
/** 사양서 §4.3 — 공차(空車) 단일 통로. TrafficController가 구간을 배타적으로 잠그므로
 *  "단일" 값만 의미 있다(1절 근거) — 양방향 값(1900/2800)은 안 쓴다. */
static final double EMPTY_MIN_WIDTH_MM = 950;
static final double LOADED_MIN_WIDTH_MM = 1400;

private static double requiredWidthMm(boolean loaded) {
    return loaded ? LOADED_MIN_WIDTH_MM : EMPTY_MIN_WIDTH_MM;
}
```

- `GraphCostAwareAssignmentPolicy.MIN_BATTERY_PERCENT`와 같은 자리, 같은 이유(로봇 종류가
  하나뿐인 지금은 상수가 정직하다, 3절 근거).

### D5. `LaneGraph.plan()`/`planByNode()`에 `loaded` 오버로드 추가 (하위호환)

```java
public RoutePlan plan(double[] from, double[] to) {
    return plan(from, to, false);   // 기존 호출부·테스트 6건 무변경
}
public RoutePlan plan(double[] from, double[] to, boolean loaded) { ... }

public RoutePlan planByNode(double[] from, String toNode) {
    return planByNode(from, toNode, false);
}
public RoutePlan planByNode(double[] from, String toNode, boolean loaded) { ... }
```

다익스트라 완화 단계, 장애물 판정 바로 옆에 한 줄:

```java
if (obstacles.isBlocked(canonicalEdgeId(current, edge.to()))) continue;
if (edge.widthMm() < requiredWidthMm(loaded)) continue;   // 신규 — 폭 미달, 존재하지 않는 것처럼
```

### D6. 호출부 — `order.isLoaded()`를 그대로 넘긴다(새 상태 없음)

- `OrderService.planLeg(fromPos, order, toNode)` → `laneGraph.planByNode(fromPos, toNode,
  order.isLoaded())`.
- `grantNextLeg`/`tryDispatch`는 이미 `order`를 갖고 `planLeg`를 부르므로 별도 배선이
  필요 없다 — `planLeg` 내부에서 한 번만 고치면 두 호출부 모두 적용된다.
- `GraphCostAwareAssignmentPolicy.routeCost`도 `laneGraph.plan(pos, origin,
  order.isLoaded())`로 바꾼다 — 이 시점(`TO_BE_ALLOCATED`) 주문은 항상 `loaded=false`라
  실질적 동작 변화는 없지만, 하드코딩된 `false`보다 "왜 항상 false인지"가 코드로 드러난다.

### D7. 정격 적재량(600kg) — 주문 생성 시점에 얕게 검증

```java
// CreateOrderRequest(fleet, P24)에 필드 추가
Double weightKg;   // 선택, WMS가 파렛트 총중량을 실어 보낼 때만

// OrderController.create() 또는 OrderService.create()에서
if (request.weightKg() != null && request.weightKg() > AMR_RATED_PAYLOAD_KG) {  // 600
    throw new BusinessException(INVALID_REQUEST, "...");
}
```

- **로봇별 필터가 아니라 생성 시점 거부다** — 지금 모든 AMR이 같은 모델(600kg 정격)이라
  "이 로봇은 못 든다"를 배차 단계에서 판정할 이유가 없다(D4와 같은 논리). WMS의 파렛트
  상한(500kg 미만, P23 D4)이 로봇 정격(600kg)보다 항상 낮으므로 **정상 경로에서는 이
  검증이 걸릴 일이 없다** — 방어 심층(defense-in-depth)이지, 실제로 자주 걸리는 게이트가
  아니다. WMS가 무게를 안 보내면(지금 실제로 안 보낸다 — 범위 밖, 8절) 검증을 건너뛴다.
- **이번 범위에서는 WMS가 이 필드를 채우지 않는다.** 자리만 만들어 둔다 — WMS의
  `FleetTaskClient`가 파렛트 무게를 계산할 수는 있지만(P23 `Item.unitWeightKg`), 그걸
  fleet에 보내는 배선은 별도 항목으로 남긴다(8절). 지금은 fleet 쪽 검증 로직과 자리만
  완성한다.

---

## 5. 실행 단계

- [ ] factory: V?? 마이그레이션(D1), `LayoutResponse.Edge`(D2)
- [ ] fleet: `LocationRegistry.Edge`에 `widthMm`(D3) — `refresh()`/`FALLBACK_EDGES`/`buildAdjacency` 반영
- [ ] fleet: `LaneGraph`에 폭 상수(D4) + `loaded` 오버로드(D5) + 다익스트라 한 줄
- [ ] fleet: `OrderService.planLeg`/`GraphCostAwareAssignmentPolicy.routeCost`(D6)
- [ ] fleet: `CreateOrderRequest.weightKg` + 생성 시 정격 검증(D7)
- [ ] **신규 테스트**: 폭 판정 함수 단위 테스트(1200mm 엣지 — loaded=true 차단/loaded=false
      통과) + 기존 폴백 그래프로 `loaded=true` 라우팅이 `loaded=false`와 동일한 비용·경로를
      냄을 확인(2000mm 시드가 실제로 안 막는다는 회귀 방지)
- [ ] 기존 `LaneGraphTest` 6건 + `GraphCostAwareAssignmentPolicyTest` 무변경 통과 확인
- [ ] e2e: factory+fleet+wms 재기동, 출고지시 → 배차·라우팅이 이전과 동일하게 동작함을
      실기동 확인(2000mm 시드에서는 아무것도 막히지 않아야 정상)

---

## 6. 리스크 & 롤백

- **가장 위험한 지점은 D5(다익스트라 변경)다** — `LaneGraph`는 P20이 가장 공들여 검증한
  부분이라, 오버로드 추가만으로 기존 6개 테스트를 건드리지 않는 것이 최우선 방어선이다.
  추가로 "2000mm 시드에서는 `loaded=true`/`false` 결과가 동일하다"를 명시적으로 테스트해
  **새 매개변수가 실질적으로 아무것도 안 바꾼다는 것 자체**를 증명한다(이번 범위에서는).
- **좁은 구간이 실제로 없으므로 이번 배포에서 강제 로직이 "한 번도 발동하지 않는다"** —
  이건 버그가 아니라 D1의 의도된 결과다(안전 우선). 발동 여부는 단위 테스트로만
  증명되고, 실기동에서는 "아무것도 안 바뀐다"가 성공 기준이다.
- **롤백 수단**: D5의 오버로드 추가는 기존 시그니처를 안 건드리므로, 문제가 생기면
  호출부(D6)만 예전 2-인자 호출로 되돌리면 즉시 원복된다 — `LaneGraph` 내부(D4/D5)는
  그대로 둬도 무해하다(아무도 `loaded=true`로 안 부르면 상수는 죽은 코드일 뿐).

---

## 7. 완료 기준

- [ ] 폭 미달 엣지(테스트 전용)는 `loaded=true`일 때 경로에서 제외되고, `loaded=false`일
      때는 포함된다(단위 테스트)
- [ ] 실제 레이아웃(2000mm 시드)에서는 `loaded` 값과 무관하게 기존 경로·비용이 그대로다
- [ ] 600kg 초과 `weightKg`로 주문 생성 시 거부된다(값을 보낼 때만)
- [ ] 기존 `LaneGraphTest`/`GraphCostAwareAssignmentPolicyTest`/e2e 흐름 회귀 없음

---

## 8. 이번엔 안 하는 것 (범위 밖)

- **WMS가 파렛트 무게를 fleet에 실어 보내기** — D7은 fleet 쪽 검증 자리만 만든다. WMS
  `FleetTaskClient`가 `Item.unitWeightKg × quantity`를 계산해 `weightKg`로 보내는 배선은
  별도 항목(WMS 쪽 변경이라 P23의 연장선에 가깝다).
- **이기종 로봇 함대(로봇별 다른 폭·정격)** — 지금 상수 하나로 충분하다(3절 근거). 다른
  로봇 모델이 실제로 추가될 때 로봇 마스터에 필드를 얹는 게 정직한 순서다.
- **회전 반경(사양서 §"AMR팔레트 랙 사양", 대각선 ≥1155mm) 판정** — 통로 "직진" 폭과는
  다른 기하학적 조건(교차점에서의 회전 여유)이라 이번 다익스트라 엣지 판정에 자연스럽게
  안 얹힌다. 필요해지면 교차점(JUNCTION) 노드 자체에 회전 여유 플래그를 얹는 별도 설계.
- **양방향 통로 값(1900/2800mm)** — 1절 근거대로 `TrafficController`의 배타적 잠금 때문에
  지금 구조에서 의미가 없다. 여러 로봇이 한 구간을 동시에 쓰는 정책이 생기면(8절 범위
  밖 — P21 D2/P22가 이미 미룬 "다중 로봇 교통정리") 그때 다시 연다.
- **좁은 구간을 실제로 만들어 데모에서 우회를 보여주기** — D1에서 의도적으로 뺐다(5절
  리스크 근거). 보여주고 싶으면 나중에 별도 승인을 받아 특정 구간 하나만 좁히는 걸
  검토한다(그때는 어떤 데모 흐름이 영향받는지 먼저 확인해야 한다).
