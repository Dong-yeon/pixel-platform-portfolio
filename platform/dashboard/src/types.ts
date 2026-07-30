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

export type ModuleKey = 'overview' | 'factory' | 'fleet'

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
  lastHeartbeatAt: string | null
}

export type TaskStatus = 'PENDING' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

export interface Task {
  id: number
  taskCode: string
  originNode: string
  destinationNode: string
  priority: TaskPriority
  status: TaskStatus
  assignedRobotId: number | null
  retryCount: number
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

export type LayoutNodeType = 'DOCK' | 'WAREHOUSE' | 'STATION' | 'SHIPPING'

export interface LayoutNode {
  nodeCode: string
  name: string
  nodeType: LayoutNodeType
  posX: number
  posY: number
}

/** POP 단말 — P12에서 채워진다(지금은 빈 목록). */
export interface LayoutTerminal {
  terminalCode: string
  name: string
  posX: number
  posY: number
}

export interface Layout {
  width: number
  height: number
  /** 상단 가로 통로 y — LINE-1(A열) 담당. */
  upperAisleY: number
  /** 하단 가로 통로 y — LINE-2(B열) 담당. */
  lowerAisleY: number
  nodes: LayoutNode[]
  terminals: LayoutTerminal[]
}

/** 노드 코드 → 좌표. 지도·경로 계산이 쓰기 쉬운 형태로 바꿔 둔다. */
export function nodeIndex(layout: Layout | null): Record<string, [number, number]> {
  if (!layout) return {}
  return Object.fromEntries(layout.nodes.map((n) => [n.nodeCode, [n.posX, n.posY]]))
}

/**
 * 두 지점 사이의 주행 경로(통로 경유)를 폴리라인으로 만든다.
 *
 * **서버 `LaneGraph`·robot-sim `NodeMap#route` 와 같은 규칙이어야** 그린 선과 실제 주행이
 * 일치한다: 목적지가 속한 쪽 통로를 탄다.
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
  const aisleY = to[1] < midY ? layout.upperAisleY : layout.lowerAisleY
  return [from, [from[0], aisleY], [to[0], aisleY], to]
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

export interface WorkOrder {
  id: number
  workOrderNo: string
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
