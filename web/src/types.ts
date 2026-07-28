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
  assignedAt: string | null
  startedAt: string | null
  finishedAt: string | null
  failureReason: string | null
}

export type EventSeverity = 'INFO' | 'WARNING' | 'ERROR'

export interface FleetEvent {
  id: number
  eventType: string
  severity: EventSeverity
  message: string
  taskId: number | null
  occurredAt: string
}

export interface AuthUser {
  accessToken: string
  tokenType: string
  username: string
  name: string
  role: string
}

// Floor plan — must match control-service LocationRegistry / robot-sim NodeMap.
export const MAP_W = 32
export const MAP_H = 24
export const NODES: Record<string, [number, number]> = {
  'DOCK-1': [2, 2],
  'DOCK-2': [2, 22],
  'STATION-A': [12, 6],
  'STATION-B': [26, 9],
  'STATION-C': [19, 19],
  'WAREHOUSE': [30, 22],
}
