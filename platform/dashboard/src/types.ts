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

/**
 * 설비가 놓인 자리. 같은 평면도에 설비(PixelFactory)와 AMR(PixelFleet)을 함께 그리기 위한
 * 매핑이다 — 각 설비는 자기 자재 하역 지점(스테이션) 옆에 선다.
 *
 * 지금은 대시보드가 좌표를 들고 있다. 쓸 만하다고 판단되면 equipments 테이블에
 * pos_x/pos_y를 추가해 서버가 내려주도록 정식화한다(docs/BACKLOG.md).
 */
export const EQUIPMENT_POSITIONS: Record<string, [number, number]> = {
  'CNC-01': [10, 3.5], // STATION-A 옆
  'CNC-02': [28.5, 6], // STATION-B 옆
  'MCT-01': [17, 16.5], // STATION-C 옆
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
