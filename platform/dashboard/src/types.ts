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

// 공장 평면도(44 × 24) — control-service LocationRegistry / robot-sim NodeMap과 일치해야 한다.
// 상단 LINE-1 가공, 하단 LINE-2 조립·검사, 가운데는 AMR 통로.
export const MAP_W = 44
export const MAP_H = 24
export const NODES: Record<string, [number, number]> = {
  'DOCK-1': [3, 3],
  'DOCK-2': [3, 21],
  WAREHOUSE: [3, 12],
  'STATION-A1': [11, 5.5],
  'STATION-A2': [18, 5.5],
  'STATION-A3': [25, 5.5],
  'STATION-A4': [32, 5.5],
  'STATION-B1': [11, 18.5],
  'STATION-B2': [18, 18.5],
  'STATION-B3': [25, 18.5],
  'STATION-B4': [32, 18.5],
  SHIPPING: [41, 12],
}

/**
 * AMR이 다니는 가로 주통로의 y좌표. robot-sim NodeMap.AISLE_Y와 같아야 한다.
 * 로봇은 열린 바닥을 대각선으로 가로지르지 않고 이 통로를 따라 이동하므로,
 * 지도에 그리는 경로선도 같은 규칙을 따라야 실제 주행과 일치한다.
 */
export const AISLE_Y = 12

/** 두 지점 사이의 주행 경로(통로 경유)를 폴리라인 좌표로 만든다. */
export function routePoints(
  from: [number, number],
  to: [number, number],
): [number, number][] {
  if (Math.abs(from[0] - to[0]) < 0.6) {
    return [from, to]
  }
  return [from, [from[0], AISLE_Y], [to[0], AISLE_Y], to]
}

/**
 * 설비가 놓인 자리. 같은 평면도에 설비(PixelFactory)와 AMR(PixelFleet)을 함께 그리기 위한
 * 매핑이다 — 각 설비는 자기 자재 하역 지점(스테이션) 옆에 선다.
 *
 * 지금은 대시보드가 좌표를 들고 있다. 쓸 만하다고 판단되면 equipments 테이블에
 * pos_x/pos_y를 추가해 서버가 내려주도록 정식화한다(docs/BACKLOG.md).
 */
export const EQUIPMENT_POSITIONS: Record<string, [number, number]> = {
  // LINE-1 가공 — 각 설비가 자기 하역 지점(STATION-A*) 바로 위에 선다
  'CNC-01': [11, 2.4],
  'CNC-02': [18, 2.4],
  'CNC-03': [25, 2.4],
  'MCT-01': [32, 2.4],
  // LINE-2 조립·검사 — 하역 지점(STATION-B*) 바로 아래
  'ASM-01': [11, 21.6],
  'ASM-02': [18, 21.6],
  'INS-01': [25, 21.6],
  'PKG-01': [32, 21.6],
}

// ---------- pixel-factory (OEE) ----------

export type EquipmentStatus = 'IDLE' | 'RUNNING' | 'DOWN' | 'QUALITY_HOLD'

export interface Equipment {
  id: number
  equipmentCode: string
  name: string
  lineId: number
  idealCycleTimeMs: number
  status: EquipmentStatus
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

/** factory 이벤트 원본은 createdAt을 쓴다 → TimelineEvent로 변환해서 사용. */
export interface FactoryEventRaw {
  id: number
  eventType: string
  severity: EventSeverity
  message: string
  workOrderId: number | null
  createdAt: string
}
