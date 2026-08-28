// ---------- 공통 ----------

export type EventSeverity = 'INFO' | 'WARNING' | 'ERROR'

/** 두 모듈의 이벤트를 타임라인에서 같은 모양으로 다루기 위한 공통 형태. */
export interface TimelineEvent {
  id: number
  eventType: string
  severity: EventSeverity
  message: string
  occurredAt: string
}

export interface AuthUser {
  accessToken: string
  tokenType: string
  username: string
  name: string
  role: string
}

export type ModuleKey = 'overview' | 'factory' | 'fleet' | 'pop' | 'inspection' | 'outbox' | 'master' | 'scenario'

// ---------- pixel-fleet (AMR) ----------

export type RobotStatus = 'IDLE' | 'MOVING' | 'CHARGING' | 'ERROR' | 'OFFLINE'

export interface Robot {
  id: number
  robotCode: string
  name: string
  status: RobotStatus
  batteryPercent: number
  posX: number
  posY: number
  /**
   * 적재(파렛트 있음) / 공차.
   *
   * 물류는 절반이 "가지러 가는 중"(공차), 절반이 "옮기는 중"(적재)이다.
   * 구분하지 않으면 지도에서 그냥 원이 돌아다니는 것으로만 보인다.
   */
  laden: boolean
  /**
   * 일하는 층. 로봇은 층을 오가지 못한다(창고동 엘리베이터는 화물용이라 물건만 태운다).
   * 위층 노드는 아래층과 **좌표가 겹치므로** 지도는 이 값으로 걸러 그린다.
   */
  floorNo: number
  lastHeartbeatAt: string | null
  /** 조작자가 배차 대상에서 뺐다(휴무). 텔레메트리로는 안 바뀐다. */
  offDuty: boolean
  /** 조작자가 완전히 잠갔다(고장/점검 등) — off-duty보다 강한 배제. */
  disabled: boolean
  /**
   * 로봇 종류(P21, P22) — `AMR`은 공장 레인망을 타지만 창고동 1층 안쪽엔 못 들어간다.
   * `AGV`(옛 이름: 랙 피더)는 그 반대 — 창고동 1층 안쪽(렉·입고장·피킹존·출하장)에서만
   * 돌고 AMR의 레인망에는 안 올라간다. 두 로봇이 만나는 유일한 접점은 게이트
   * (`WH-GATE-U`/`WH-GATE-L`)다. 지도가 마커 모양을 다르게 그린다.
   */
  robotType: 'AMR' | 'AGV'
  /** AGV 전용 담당 존(피킹존 노드 코드 — 1층은 전부 `WH-PICK` 하나). AMR은 항상 null. */
  zoneCode: string | null
}

export type TaskStatus = 'PENDING' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

export interface Task {
  id: number
  /**
   * fleet 내부 코드(예: `FO-00000001`) — 조작자 동사(cancel/suspend/complete/retry-failed)는
   * 이 값으로 호출해야 한다. taskCode는 WMS가 알아보는 값이라 서로 다를 수 있다.
   */
  orderCode: string
  taskCode: string
  originNode: string
  destinationNode: string
  priority: TaskPriority
  status: TaskStatus
  assignedRobotId: number | null
  retryCount: number
  /** 이 작업이 벌어지는 층 — 같은 층 로봇에게만 배차된다. */
  floorNo: number
  /** 조작자가 다음 레그를 막았다. */
  suspended: boolean
  /**
   * 채워져 있으면 이 구간이 끝난 뒤 물건이 화물 엘리베이터를 타고 여기로 간다.
   * 층이 다른 이송은 승강장에서 두 구간으로 끊긴다(로봇이 층을 따라갈 수 없어서).
   */
  handoffDestination: string | null
  /** 채워져 있으면 이 구간은 엘리베이터에서 물건을 이어받은 뒷 구간이다(앞 구간의 작업코드). */
  handoffOf: string | null
  finishedAt: string | null
  failureReason: string | null
}

/** fleet 이벤트 원본(occurredAt) */
export interface FleetEvent extends TimelineEvent {
  taskId: number | null
}

// ---------- 공장 평면도 ----------
//
// **좌표를 여기 두지 않는다.** 서버(`GET /api/factory/layout`)가 유일한 출처다.
// 예전엔 이 파일에 MAP_W/NODES/EQUIPMENT_POSITIONS/통로 y를 하드코딩하고 있었는데,
// control-service LocationRegistry · robot-sim NodeMap 과 3중으로 겹쳐서 한 곳만 고치면
// 배차 거리 비교나 화면 표시가 **조용히** 틀어졌다. 설비 좌표는 Equipment 에 실려 온다.

export type LayoutNodeType =
  | 'DOCK' | 'WAREHOUSE' | 'STATION' | 'SHIPPING' | 'INSPECTION'
  /** 엘리베이터 승강장 — 노드 사각형 대신 샤프트로 따로 그린다. */
  | 'ELEVATOR'
  /** 통로 교차점(P20) — 경로 그래프의 분기점일 뿐, 로봇이 서는 목적지가 아니다. 지도에 안 그린다. */
  | 'JUNCTION'
  /** 건물 사이 문(P20-3). P22부터는 AMR↔AGV 구역 경계이기도 하다(창고동 게이트). */
  | 'GATE'

export interface LayoutNode {
  nodeCode: string
  name: string
  nodeType: LayoutNodeType
  posX: number
  posY: number
  buildingCode: string
  /** 몇 층의 자리인가. 위층 노드는 아래층과 **좌표가 겹치므로** 층으로만 구분된다. */
  floorNo: number
}

/** 화물 엘리베이터 — 물건만 오르내린다. AMR은 자기 층에 머물며 승강장에서 싣고 내린다. */
export interface LayoutElevator {
  elevatorCode: string
  buildingCode: string
  name: string
  posX: number
  posY: number
  servesFloors: number[]
}

/** 충전존 — 충전 베이(DOCK 노드)를 감싸는 구역. 렉을 비워 둔 자리다. */
export interface LayoutChargingZone {
  zoneCode: string
  buildingCode: string
  floorNo: number
  name: string
  posX: number
  posY: number
  width: number
  height: number
}

/**
 * 건물 — 생산동 / 창고동(3층) / 품질동.
 *
 * 설비·노드·단말이 어느 건물인지는 서버가 따로 주지 않는다. 건물은 사각형이고 대상은 점이라
 * **좌표가 곧 소속**이며, 지도는 건물을 골랐을 때 그 사각형으로 확대하는 것만으로 충분하다.
 * 소속 필드를 더하면 좌표와 어긋날 수 있는 두 번째 진실이 생긴다.
 */
export interface LayoutBuilding {
  buildingCode: string
  name: string
  posX: number
  posY: number
  width: number
  height: number
  floorCount: number
  floors: LayoutFloor[]
}

export interface LayoutFloor {
  floorNo: number
  name: string
}

/** 렉(선반). `capacityQty`는 만재 수량이고 실제 수량은 WMS 재고에서 온다. */
export interface LayoutRack {
  rackCode: string
  buildingCode: string
  floorNo: number
  posX: number
  posY: number
  orientation: string
  columnsCount: number
  levelsCount: number
  capacityQty: number
}


/** POP 단말 — layout에 실려 오는 마스터(코드·이름·좌표). */
export interface LayoutTerminal {
  terminalCode: string
  name: string
  posX: number
  posY: number
}

/**
 * 파생 위치(presence) — "이 단말에서 지금 누가 작업 중인가".
 *
 * 서버가 최근 TERMINAL 이벤트에서 계산해 내려준다(저장값 아님). 지도가 키오스크 배지로 그린다.
 * `lastActivityAt`으로 오래된 배지를 흐리게 표시한다(타임아웃 임박).
 */
export interface TerminalPresence {
  terminalCode: string
  operatorName: string
  workOrderNo: string
  lastActivityAt: string
}

export interface Layout {
  width: number
  height: number
  /** 상단 가로 통로 y — A열 담당. 건물 3채를 관통한다(벽과 만나는 자리가 출입구). */
  upperAisleY: number
  /** 하단 가로 통로 y — B열 담당. */
  lowerAisleY: number
  buildings: LayoutBuilding[]
  nodes: LayoutNode[]
  terminals: LayoutTerminal[]
  racks: LayoutRack[]
  elevators: LayoutElevator[]
  chargingZones: LayoutChargingZone[]
}

/** 노드 코드 → 좌표. 지도·경로 계산이 쓰기 쉬운 형태로 바꿔 둔다. */
export function nodeIndex(layout: Layout | null): Record<string, [number, number]> {
  if (!layout) return {}
  return Object.fromEntries(layout.nodes.map((n) => [n.nodeCode, [n.posX, n.posY]]))
}

/** 이 x에 가장 가까운 실제 연결로 x(모든 노드의 x — 서버 `LocationRegistry#columns()`와 같은 전제). */
function nearestLaneX(layout: Layout, x: number): number {
  let best = x
  let bestDist = Infinity
  for (const node of layout.nodes) {
    const dist = Math.abs(node.posX - x)
    if (dist < bestDist) {
      bestDist = dist
      best = node.posX
    }
  }
  return best
}

/**
 * 두 지점 사이의 주행 경로(통로 경유)를 폴리라인으로 만든다.
 *
 * **서버 `LaneGraph`·robot-sim `NodeMap#route` 와 같은 규칙이어야** 그린 선과 실제 주행이
 * 일치한다: from·to가 각자 속한 쪽 통로를 타고, 서로 다른 통로면 목적지 쪽 연결로에서
 * 갈아탄다(서버의 `JCT-x-U`↔`JCT-x-L` 직결 엣지와 같은 모양 — 연결로 하나가 위아래 통로를
 * 바로 잇는다).
 *
 * <p>예전엔 목적지가 속한 통로 하나로만 그렸다 — from이 반대쪽 통로 구역에 있으면 그
 * 통로까지 가는 첫 세로선이 <b>가운데 다른 통로를 그대로 관통</b>했다(창고동 확장 뒤
 * 화면에서 실제로 발견 — 세로선이 창고동 중앙을 뚫고 지나가는 것처럼 보였다). from은
 * 자기 쪽 통로까지만 세로로 오르내리고, 통로를 갈아타야 하면 목적지 연결로 위에서
 * 한 번 더 세로로 갈아탄다.
 *
 * <p>from·to가 연결로 x 위에 있지 않을 수 있다 — 로봇의 실좌표(이동 중)나 렉 접근점
 * (서버 `LocationRegistry#rackApproachPoint` — 렉 앞은 레인망 밖의 로컬 이동이라 연결로
 * 개념이 없다, 설계 근거: p21 문서 D2)이 그 예다. 그 경우 그대로 세로선의 통과 x로 쓰면
 * 렉 칸을 관통하는 것처럼 보인다(실제로 겪음) — 가장 가까운 실제 연결로로 스냅하고,
 * 그 x에서 실좌표까지 짧은 곁가지를 하나 더 그린다. 이미 연결로 위에 있으면(일반 노드 간
 * 이동) 스냅해도 자기 자신이라 기존 동작과 같다.
 */
export function routePoints(
  layout: Layout,
  from: [number, number],
  to: [number, number],
): [number, number][] {
  if (Math.abs(from[0] - to[0]) < 0.6) {
    return [from, to]
  }
  const midY = (layout.upperAisleY + layout.lowerAisleY) / 2
  const fromAisleY = from[1] < midY ? layout.upperAisleY : layout.lowerAisleY
  const toAisleY = to[1] < midY ? layout.upperAisleY : layout.lowerAisleY
  const fromLaneX = nearestLaneX(layout, from[0])
  const toLaneX = nearestLaneX(layout, to[0])

  const points: [number, number][] = [from]
  if (Math.abs(fromLaneX - from[0]) > 0.6) points.push([fromLaneX, from[1]])
  points.push([fromLaneX, fromAisleY], [toLaneX, fromAisleY])
  // 통로가 다르면 목적지 연결로에서 갈아탄다 — 안 그러면 from 쪽 통로에서 계속 머문 채
  // to의 y로 바로 꺾여, 그 사이 다른 통로를 세로로 관통한다.
  if (toAisleY !== fromAisleY) points.push([toLaneX, toAisleY])
  if (Math.abs(toLaneX - to[0]) > 0.6) points.push([toLaneX, to[1]])
  points.push(to)
  return points
}

/** 창고동 밴드 진입 노드 — 좌측(`WH-B01-L`)은 {@link agvRoutePoints}가 스파인 x·아이슬 y를 찾는 데,
 * 우측(`WH-B01-R`)은 목적지가 오른쪽(게이트 등)일 때 어느 스파인을 탈지 고르는 데 쓴다. */
const BAND_ENTRY_LEFT_NODE = /^WH-B\d+-L$/
const BAND_ENTRY_RIGHT_NODE = /^WH-B\d+-R$/

/**
 * AGV(랙 피더) 전용 경로 — {@link routePoints}(AMR의 연결로 스냅 로직)를 안 탄다. AGV는
 * `LaneGraph`를 안 타는 존 안 로컬 이동이라(P21 D2) 연결로 개념 자체가 없다 — 그 스냅
 * 로직을 그대로 쓰면 렉 접근점을 엉뚱한 연결로로 당겨 붙여 실제 주행과 다른 선이 그려진다.
 *
 * <p><b>대각선 금지 — fleet `OrderService#agvWaypoints`와 같은 규칙.</b> 두 점을 직선으로
 * 그으면 대각선이 다른 밴드의 렉 열을 가로지른다(대시보드 스크린샷으로 실제 발견). 항상
 * "내 밴드 아이슬로 수직 이동 → 아이슬을 타고 수평 이동 → 목적지로 수직 이동" 순서로만
 * 웨이포인트를 만든다 — 밴드가 다르면 그 사이에 스파인을 세로로 한 번 더 탄다.
 *
 * <p><b>버그 수정 — 목적지가 렉 그리드 밖(게이트 등)이면 실제 y·알맞은 스파인을 쓴다.</b>
 * 원안은 목적지 y도 항상 "가장 가까운 밴드 아이슬 y"로 대체하고 스파인도 항상 좌측만
 * 썼다 — 렉 목적지는 안전하지만, `WH-GATE-U/L`(x=56)처럼 렉 그리드(좌우 스파인 사이)
 * 밖의 노드로 갈 때는 엉뚱한 밴드의 렉 열을 옆으로 가로지르는 선이 그려졌다(실사용 중
 * 발견 — fleet `OrderService#agvWaypoints`와 같은 버그, 같은 방식으로 고친다). 목적지가
 * 스파인 사이가 아니면 그 노드의 실제 y를 그대로 쓰고, 목적지가 오른쪽(x≥우측 스파인)이면
 * 우측 스파인을 탄다 — 스파인은 어느 y로 지나가도 항상 빈 통로라 안전하다.
 *
 * <p>스파인 x·밴드 아이슬 y는 하드코딩하지 않고 {@code layout.nodes}의 밴드 진입 노드
 * (`WH-B01-L/R` 등, factory가 준 실제 좌표)에서 그때그때 찾는다 — 서버 fleet 쪽 상수가
 * 바뀌어도 여기가 따로 어긋나지 않는다.
 */
export function agvRoutePoints(
  layout: Layout,
  from: [number, number],
  to: [number, number],
): [number, number][] {
  const leftEntries = layout.nodes.filter((n) => BAND_ENTRY_LEFT_NODE.test(n.nodeCode))
  const rightEntries = layout.nodes.filter((n) => BAND_ENTRY_RIGHT_NODE.test(n.nodeCode))
  if (leftEntries.length === 0) return [from, to]

  const leftSpineX = leftEntries[0].posX
  const rightSpineX = rightEntries.length > 0 ? rightEntries[0].posX : leftSpineX
  const nearestBandY = (y: number) =>
    leftEntries.reduce((best, n) => (Math.abs(n.posY - y) < Math.abs(best - y) ? n.posY : best), leftEntries[0].posY)

  const fromY = nearestBandY(from[1])
  const toInsideGrid = to[0] > leftSpineX && to[0] < rightSpineX
  const toY = toInsideGrid ? nearestBandY(to[1]) : to[1]

  if (fromY === toY) {
    return [from, [from[0], fromY], [to[0], fromY], to]
  }
  const spineX = to[0] >= rightSpineX ? rightSpineX : leftSpineX
  return [from, [from[0], fromY], [spineX, fromY], [spineX, toY], [to[0], toY], to]
}

/**
 * 생산동 내부 구역(P33) — A(가공)/B(조립·검사)/L(물류)/Q(품질). P33으로 품질동(QC)
 * 건물이 폐지되고 생산동(PROD)에 흡수됐지만("건물 하나 = 사각형 하나"만으로는 예전에
 * 있던 QC 색상·MRB 배지·품질 흐름선이 붙을 자리가 없어진다), 그 안에서 다시 구역을
 * 나눠 보여 준다.
 *
 * <p>**좌표가 곧 소속** 원칙을 그대로 지킨다({@link LayoutBuilding} 문서 참고) — DB에
 * zone 컬럼을 새로 두지 않고, `nodeCode` 접두어로 그때그때 분류해 소속 노드들의
 * 바운딩박스(+패딩)를 사각형으로 계산한다. A/B는 위/아래 통로로 갈리는 기존 절반씩
 * (건물 세로의 절반), L/Q는 건물을 세로로 온전히 가로지르는 슬랩으로 그린다 — 실제
 * 물류·검사 노드가 위아래 통로 둘 다에 걸쳐 있기 때문이다(QC-IN은 하단, QC-OUT은 상단).
 */
export type ProdZoneKey = 'A' | 'B' | 'L' | 'Q'

export interface ProdZoneBox {
  key: ProdZoneKey
  label: string
  minX: number
  maxX: number
  minY: number
  maxY: number
}

const PROD_ZONE_RULES: { prefix: RegExp; key: ProdZoneKey; label: string; spansFullHeight: boolean }[] = [
  { prefix: /^PROD-A/, key: 'A', label: 'A · 가공', spansFullHeight: false },
  { prefix: /^PROD-B/, key: 'B', label: 'B · 조립·검사', spansFullHeight: false },
  { prefix: /^PROD-L/, key: 'L', label: 'L · 물류', spansFullHeight: true },
  { prefix: /^QC-/, key: 'Q', label: 'Q · 품질', spansFullHeight: true },
]

/** 구역 사각형이 벽에 딱 붙지 않도록 두는 여백. */
const PROD_ZONE_PAD = 2.0

export function prodZones(layout: Layout, building: LayoutBuilding): ProdZoneBox[] {
  const midY = (layout.upperAisleY + layout.lowerAisleY) / 2
  const zones: ProdZoneBox[] = []
  for (const rule of PROD_ZONE_RULES) {
    const nodes = layout.nodes.filter(
      (n) => n.buildingCode === building.buildingCode && rule.prefix.test(n.nodeCode),
    )
    if (nodes.length === 0) continue
    const xs = nodes.map((n) => n.posX)
    const minX = Math.max(building.posX, Math.min(...xs) - PROD_ZONE_PAD)
    const maxX = Math.min(building.posX + building.width, Math.max(...xs) + PROD_ZONE_PAD)
    const minY = rule.spansFullHeight || rule.key === 'A' ? building.posY : midY
    const maxY = rule.spansFullHeight || rule.key === 'B' ? building.posY + building.height : midY
    zones.push({ key: rule.key, label: rule.label, minX, maxX, minY, maxY })
  }
  return zones
}

// ---------- pixel-factory (OEE) ----------

// 서버 EquipmentStatus 와 일치해야 한다. SETUP·PLANNED_STOP 은 P9에서 추가됐다
// (계획가동시간 정의 — PLANNED_STOP 만 A의 분모에서 빠진다).
export type EquipmentStatus =
  | 'IDLE' | 'RUNNING' | 'SETUP' | 'DOWN' | 'QUALITY_HOLD' | 'PLANNED_STOP'

export interface Equipment {
  id: number
  equipmentCode: string
  name: string
  lineId: number
  idealCycleTimeMs: number
  status: EquipmentStatus
  /** 평면도 상의 위치 — 서버가 내려준다(대시보드가 하드코딩하지 않는다). */
  posX: number
  posY: number
}

/** 설비 1대의 OEE. 서버가 이벤트에서 계산한 값이며 대시보드는 다시 계산하지 않는다. */
export interface EquipmentOee {
  equipmentCode: string
  name: string
  from: string
  to: string
  availability: number
  performance: number
  quality: number
  oee: number
  /** P > 1.0 — 표준CT가 실제보다 크게 잡혀 있다는 신호. 값은 잘리지 않는다. */
  performanceAnomaly: boolean
  plannedMinutes: number
  operatingMinutes: number
  producedQty: number
  defectQty: number
}

export interface ProductionLine {
  id: number
  lineCode: string
  name: string
}

export type WorkOrderStatus =
  | 'CREATED' | 'ASSIGNED' | 'READY' | 'IN_PROGRESS'
  | 'INSPECTION_WAITING' | 'COMPLETED' | 'ON_HOLD' | 'CANCELLED'

// ---------- 생산 기준정보 (차종 · 품번 · BOM) ----------

export type PartType = 'PRODUCT' | 'SEMI' | 'MATERIAL'

export interface VehicleModel {
  id: number
  modelCode: string
  name: string
  inProduction: boolean
}

export interface Part {
  id: number
  partCode: string
  name: string
  partType: PartType
  unit: string
  /** 공용 부품이면 null. */
  modelCode: string | null
}

/** BOM 트리 한 노드 — **서버가 조립해서** 중첩으로 내려준다(화면이 부모를 추측하지 않는다). */
export interface BomNode {
  partCode: string
  name: string
  partType: PartType
  unit: string
  /** 상위 1개당 소요량. 루트는 null. */
  qtyPer: string | null
  level: number
  children: BomNode[]
}

export interface BomRevision {
  revNo: number
  lineCount: number
  latest: boolean
}

export interface WorkOrder {
  id: number
  workOrderNo: string
  /** 무엇을 만드는가 — 기준정보가 생기기 전엔 없던 정보다. */
  partCode: string | null
  partName: string | null
  modelCode: string | null
  equipmentId: number
  lotNo: string
  plannedQty: number
  producedQty: number
  defectQty: number
  status: WorkOrderStatus
  startedAt: string | null
  completedAt: string | null
  holdReason: string | null
}

// factory 이벤트도 occurredAt(발생시각)을 내려주므로 fleet과 같은 TimelineEvent를 쓴다.
// 예전엔 createdAt(적재 시각)만 있어서 대시보드에서 변환(toTimeline)해야 했다.

// ---------- pixel-qms (품질) ----------

export type InspectionResult = 'PENDING' | 'PASSED' | 'FAILED'

export interface Inspection {
  id: number
  inspectionNo: string
  inspectionType: 'INCOMING' | 'IN_PROCESS' | 'FINAL'
  equipmentCode: string | null
  workOrderNo: string | null
  lotNo: string | null
  result: InspectionResult
  inspectedQty: number
  defectQty: number
  note: string | null
  completedAt: string | null
  createdAt: string
}

export type MrbStatus = 'RAISED' | 'UNDER_REVIEW' | 'DECIDED' | 'CLOSED'
export type MrbDecision = 'USE_AS_IS' | 'REWORK' | 'SCRAP' | 'RETURN'

export interface MrbReview {
  id: number
  mrbNo: string
  nonconformanceId: number
  equipmentCode: string | null
  workOrderNo: string | null
  lotNo: string | null
  status: MrbStatus
  decision: MrbDecision | null
  decisionNote: string | null
  holdApplied: boolean
  decidedAt: string | null
  closedAt: string | null
  createdAt: string
}

export interface Nonconformance {
  id: number
  ncrNo: string
  equipmentCode: string | null
  workOrderNo: string | null
  lotNo: string | null
  defectQty: number
  description: string | null
}

/** 발송함의 메일 한 통 — 실제 SMTP가 아니라 Outbox에 쌓인 것. */
export interface OutboxMail {
  id: number
  recipient: string
  subject: string
  body: string
  channel: string
  referenceNo: string | null
  sentAt: string
}

/** 지도 품질관리실 배지 — 열려 있는 MRB. */
export interface MrbOpenSummary {
  count: number
  reviews: MrbReview[]
}
