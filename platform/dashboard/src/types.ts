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

// 공장 지도 좌표 — control-service LocationRegistry / robot-sim NodeMap과 일치해야 한다.
export const MAP_W = 32
export const MAP_H = 24
export const NODES: Record<string, [number, number]> = {
  'DOCK-1': [2, 2],
  'DOCK-2': [2, 22],
  'STATION-A': [12, 6],
  'STATION-B': [26, 9],
  'STATION-C': [19, 19],
  WAREHOUSE: [30, 22],
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
