import {
  EQUIPMENT_POSITIONS, MAP_H, MAP_W, NODES,
  type Equipment, type EquipmentStatus, type Robot, type RobotStatus, type Task,
} from '../types'

const ROBOT_COLOR: Record<RobotStatus, string> = {
  IDLE: '#27ae60',
  MOVING: '#2d7ff9',
  CHARGING: '#e08a00',
  ERROR: '#e0392b',
  OFFLINE: '#8a8a8a',
}

const EQUIP_COLOR: Record<EquipmentStatus, string> = {
  RUNNING: '#27ae60',
  IDLE: '#9aa5b4',
  DOWN: '#e0392b',
  QUALITY_HOLD: '#e08a00',
}

/** 진행 중인 운송만 흐름선으로 그린다(대기/완료는 제외). */
const ACTIVE_TASK = new Set(['ASSIGNED', 'IN_PROGRESS'])

/**
 * 통합 공장 평면도 — 하나의 좌표계 위에 두 모듈을 함께 그린다.
 *
 *   설비(PixelFactory)  사각형, 상태별 색
 *   AMR(PixelFleet)     원형, 실시간 이동
 *   운송 작업            출발→도착 점선 (물류 흐름)
 *
 * "한 공장을 두 시스템이 관제한다"는 플랫폼의 요지가 이 한 화면에 드러난다.
 */
export function UnifiedMap({
  equipments,
  robots,
  tasks,
}: {
  equipments: Equipment[]
  robots: Robot[]
  tasks: Task[]
}) {
  const activeTasks = tasks.filter((t) => ACTIVE_TASK.has(t.status))

  return (
    <svg className="umap" viewBox={`0 0 ${MAP_W} ${MAP_H}`} preserveAspectRatio="xMidYMid meet">
      <rect x={0} y={0} width={MAP_W} height={MAP_H} className="umap-bg" />

      {/* 운송 흐름 — 설비/로봇보다 아래에 깔린다 */}
      {activeTasks.map((t) => {
        const from = NODES[t.originNode]
        const to = NODES[t.destinationNode]
        if (!from || !to) return null
        return (
          <line
            key={`route-${t.id}`}
            x1={from[0]} y1={from[1]} x2={to[0]} y2={to[1]}
            className="umap-route"
          />
        )
      })}

      {/* 하역 지점(AMR 경유지) */}
      {Object.entries(NODES).map(([name, [x, y]]) => (
        <g key={name}>
          <rect x={x - 0.7} y={y - 0.7} width={1.4} height={1.4} className="umap-node" rx={0.25} />
          <text x={x} y={y + 2} className="umap-node-label" textAnchor="middle">
            {name}
          </text>
        </g>
      ))}

      {/* 설비 (PixelFactory) */}
      {equipments.map((e) => {
        const pos = EQUIPMENT_POSITIONS[e.equipmentCode]
        if (!pos) return null
        const [x, y] = pos
        return (
          <g key={e.equipmentCode}>
            <rect
              x={x - 1.9} y={y - 1.3} width={3.8} height={2.6} rx={0.4}
              fill={EQUIP_COLOR[e.status]}
              className={`umap-equip eq-${e.status}`}
            />
            <text x={x} y={y + 0.35} className="umap-equip-label" textAnchor="middle">
              {e.equipmentCode}
            </text>
          </g>
        )
      })}

      {/* AMR (PixelFleet) — 항상 맨 위 */}
      {robots.map((r) => (
        <g
          key={r.robotCode}
          className="umap-robot"
          style={{ transform: `translate(${r.posX}px, ${r.posY}px)` }}
        >
          <circle r={0.95} fill={ROBOT_COLOR[r.status]} stroke="#fff" strokeWidth={0.18} />
          <text y={0.38} className="umap-robot-label" textAnchor="middle">
            {r.robotCode.slice(-1)}
          </text>
          <text y={-1.35} className="umap-robot-batt" textAnchor="middle">
            {r.batteryPercent}%
          </text>
        </g>
      ))}
    </svg>
  )
}
