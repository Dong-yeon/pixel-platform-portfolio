import { nodeIndex, type Layout, type Robot, type RobotStatus } from '../../types'

const STATUS_COLOR: Record<RobotStatus, string> = {
  IDLE: '#27ae60',
  MOVING: '#2d7ff9',
  CHARGING: '#e08a00',
  ERROR: '#e0392b',
  OFFLINE: '#8a8a8a',
}

/**
 * fleet 전용 지도(로봇만 그린다).
 *
 * <p><b>현재 아무도 쓰지 않는다.</b> FleetView가 통합 현황과 같은 {@code UnifiedMap}을 쓰도록
 * 바꾼 뒤 남은 잔재다 — 같은 공장을 보는 화면이 서로 다르게 그려질 이유가 없어서 통합했다.
 * 지울 후보이며, 모듈 단독 개발용으로만 의미가 있다.
 */
export function FactoryMap({ layout, robots }: { layout: Layout | null; robots: Robot[] }) {
  if (!layout) {
    return <p className="muted small">평면도를 불러오는 중…</p>
  }

  const { width, height } = layout

  return (
    <svg className="map" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="xMidYMid meet">
      <rect x={0} y={0} width={width} height={height} className="map-bg" />

      {Object.entries(nodeIndex(layout)).map(([name, [x, y]]) => (
        <g key={name}>
          <rect x={x - 1} y={y - 1} width={2} height={2} className="node" rx={0.3} />
          <text x={x} y={y - 1.4} className="node-label" textAnchor="middle">
            {name}
          </text>
        </g>
      ))}

      {robots.map((r) => (
        <g key={r.robotCode} className="robot-dot" style={{ transform: `translate(${r.posX}px, ${r.posY}px)` }}>
          <circle r={1} fill={STATUS_COLOR[r.status]} stroke="#fff" strokeWidth={0.15} />
          <text y={0.4} className="robot-label" textAnchor="middle">
            {r.robotCode.slice(-1)}
          </text>
          <text y={-1.4} className="robot-batt" textAnchor="middle">
            {r.batteryPercent}%
          </text>
        </g>
      ))}
    </svg>
  )
}
