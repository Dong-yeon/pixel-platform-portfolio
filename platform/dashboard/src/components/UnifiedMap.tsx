import {
  nodeIndex, routePoints,
  type Equipment, type EquipmentStatus, type Layout, type Robot, type RobotStatus, type Task,
  type TerminalPresence,
} from '../types'

/** 지도 레이어 on/off. 밀도가 빠듯해 겹치는 시스템을 끌 수 있게 한다(지도 시각 규칙). */
export interface MapLayers {
  equipment: boolean
  amr: boolean
  routes: boolean
  pop: boolean
}

const ALL_LAYERS: MapLayers = { equipment: true, amr: true, routes: true, pop: true }

// 마지막 조작 후 이만큼 지나면 배지를 흐리게 — "곧 사라짐" 신호(서버는 타임아웃에 목록에서 뺀다).
const STALE_FADE_MINUTES = 15

const ROBOT_COLOR: Record<RobotStatus, string> = {
  IDLE: '#27ae60',
  MOVING: '#2d7ff9',
  CHARGING: '#e08a00',
  ERROR: '#e0392b',
  OFFLINE: '#8a8a8a',
}

// 서버 EquipmentStatus 전부를 다뤄야 한다 — Record<EquipmentStatus, …>라 값이 빠지면
// 타입체크가 잡아 준다(새 상태가 지도에서 조용히 무색이 되는 일을 막는다).
const EQUIP_COLOR: Record<EquipmentStatus, string> = {
  RUNNING: '#27ae60',
  IDLE: '#9aa5b4',
  SETUP: '#2d7ff9',        // 준비·교체 — 비계획 정지지만 고장과는 구분
  DOWN: '#e0392b',
  QUALITY_HOLD: '#e08a00',
  PLANNED_STOP: '#5c6470', // 계획정지 — 애초에 돌릴 계획이 없던 시간(A의 분모에서 빠진다)
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
  layout,
  equipments,
  robots,
  tasks,
  presence = [],
  layers = ALL_LAYERS,
}: {
  /** 서버가 내려준 평면도. 아직 못 받았으면 그릴 좌표계가 없으므로 안내만 띄운다. */
  layout: Layout | null
  equipments: Equipment[]
  robots: Robot[]
  tasks: Task[]
  /** POP 파생 위치 — 사용 중 단말에 담당자 배지를 붙인다(작업자 독립 마커는 그리지 않는다). */
  presence?: TerminalPresence[]
  layers?: MapLayers
}) {
  if (!layout) {
    return <p className="muted small">평면도를 불러오는 중…</p>
  }

  const activeTasks = tasks.filter((t) => ACTIVE_TASK.has(t.status))
  const robotById = new Map(robots.map((r) => [r.id, r]))
  const presenceByTerminal = new Map(presence.map((p) => [p.terminalCode, p]))
  const NODES = nodeIndex(layout)
  const { width, height } = layout

  return (
    <svg className="umap" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="xMidYMid meet">
      <rect x={0} y={0} width={width} height={height} className="umap-bg" />

      {/* AMR 통로 — 라인마다 하나씩. 로봇은 이 위로만 다닌다. */}
      {[layout.upperAisleY, layout.lowerAisleY].map((y) => (
        <line key={`aisle-${y}`} x1={2} y1={y} x2={width - 2} y2={y} className="umap-aisle" />
      ))}

      {/* AMR 이동 경로 — 설비/로봇보다 아래에 깔린다.
          배정된 로봇이 있으면 "그 로봇의 현재 위치 → 목적지"를 그려 실제로 남은 경로를
          보여준다(로봇이 움직이면 선도 따라 줄어든다). 아직 배정 전이면 출발지에서 그린다. */}
      {layers.routes && activeTasks.map((t) => {
        const to = NODES[t.destinationNode]
        if (!to) return null
        const robot = t.assignedRobotId ? robotById.get(t.assignedRobotId) : undefined
        const from: [number, number] | undefined = robot
          ? [robot.posX, robot.posY]
          : NODES[t.originNode]
        if (!from) return null
        // 실제 주행과 같은 통로 경유 경로로 그린다(직선으로 그리면 설비를 관통하는 것처럼 보인다).
        const pts = routePoints(layout, from, to)
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

      {/* 설비 (PixelFactory) — 좌표는 서버가 실어 보낸다(하드코딩 매핑 없음) */}
      {layers.equipment && equipments.map((e) => {
        if (e.posX == null || e.posY == null) return null
        const [x, y] = [e.posX, e.posY]
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

      {/* POP 단말 — 세로 직사각(키오스크). 사용 중이면 하단에 담당자·WO 배지.
          작업자는 독립 마커로 그리지 않는다(지도 시각 규칙) — 단말에 붙는 배지로만. */}
      {layers.pop && layout.terminals.map((t) => {
        const here = presenceByTerminal.get(t.terminalCode)
        const stale = here ? minutesSince(here.lastActivityAt) >= STALE_FADE_MINUTES : false
        return (
          <g key={t.terminalCode} className="umap-terminal-g">
            <rect
              x={t.posX - 0.9} y={t.posY - 1.5} width={1.8} height={3.0} rx={0.3}
              className={`umap-terminal ${here ? 'in-use' : ''}`}
            />
            <text
              x={t.posX} y={t.posY + 0.3} textAnchor="middle"
              className={`umap-terminal-label ${here ? 'in-use' : ''}`}
            >
              {t.terminalCode.replace('POP-', '')}
            </text>
            {here && (
              <g className="umap-operator-badge" opacity={stale ? 0.45 : 1}>
                <rect x={t.posX - 3.4} y={t.posY + 1.7} width={6.8} height={2.2} rx={0.4} />
                <text x={t.posX} y={t.posY + 2.75} textAnchor="middle" className="umap-badge-name">
                  {here.operatorName}
                </text>
                <text x={t.posX} y={t.posY + 3.55} textAnchor="middle" className="umap-badge-wo">
                  {here.workOrderNo}
                </text>
              </g>
            )}
          </g>
        )
      })}

      {/* AMR (PixelFleet) — 항상 맨 위 */}
      {layers.amr && robots.map((r) => (
        <g
          key={r.robotCode}
          className="umap-robot"
          style={{ transform: `translate(${r.posX}px, ${r.posY}px)` }}
        >
          {/* 적재 중이면 파렛트를 얹어 그린다 — "가지러 가는 중"과 "옮기는 중"의 구분이
              물류 화면에서 가장 먼저 읽혀야 하는 정보다. 로봇 뒤에 깔아 원을 가리지 않는다. */}
          {r.laden && <rect x={-1.15} y={-1.15} width={2.3} height={2.3} rx={0.2} className="umap-pallet" />}
          <circle r={0.95} fill={ROBOT_COLOR[r.status]} stroke="#fff" strokeWidth={0.18} />
          <text y={0.38} className="umap-robot-label" textAnchor="middle">
            {r.robotCode.slice(-1)}
          </text>
          <text y={-1.55} className="umap-robot-batt" textAnchor="middle">
            {r.batteryPercent}%
          </text>
        </g>
      ))}
    </svg>
  )
}

/** ISO 시각으로부터 지난 분. 배지 흐리기 판정에 쓴다. */
function minutesSince(iso: string): number {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return 0
  return (Date.now() - then) / 60000
}
