import {
  EQUIPMENT_POSITIONS, MAP_H, MAP_W, NODES, routePoints,
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
  const robotById = new Map(robots.map((r) => [r.id, r]))

  return (
    <svg className="umap" viewBox={`0 0 ${MAP_W} ${MAP_H}`} preserveAspectRatio="xMidYMid meet">
      <rect x={0} y={0} width={MAP_W} height={MAP_H} className="umap-bg" />

      {/* AMR 이동 경로 — 설비/로봇보다 아래에 깔린다.
          배정된 로봇이 있으면 "그 로봇의 현재 위치 → 목적지"를 그려 실제로 남은 경로를
          보여준다(로봇이 움직이면 선도 따라 줄어든다). 아직 배정 전이면 출발지에서 그린다. */}
      {activeTasks.map((t) => {
        const to = NODES[t.destinationNode]
        if (!to) return null
        const robot = t.assignedRobotId ? robotById.get(t.assignedRobotId) : undefined
        const from: [number, number] | undefined = robot
          ? [robot.posX, robot.posY]
          : NODES[t.originNode]
        if (!from) return null
        // 실제 주행과 같은 통로 경유 경로로 그린다(직선으로 그리면 설비를 관통하는 것처럼 보인다).
        const pts = routePoints(from, to)
        return (
          <g key={`route-${t.id}`}>
            <polyline points={pts.map((p) => `${p[0]},${p[1]}`).join(' ')} className="umap-route" />
            {/* 웨이포인트 — 경로가 꺾이는 지점(중간 점들). 출발점·도착점은 제외한다. */}
            {pts.slice(1, -1).map((p, i) => (
              <circle key={`wp-${t.id}-${i}`} cx={p[0]} cy={p[1]} r={0.35} className="umap-waypoint" />
            ))}
            {/* 목적지 표시 — 여러 경로가 겹쳐도 어디로 가는지 구분된다 */}
            <circle cx={to[0]} cy={to[1]} r={1.5} className="umap-route-target" />
          </g>
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
