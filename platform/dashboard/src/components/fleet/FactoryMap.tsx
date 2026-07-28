import { MAP_H, MAP_W, NODES, type Robot, type RobotStatus } from '../../types'

const STATUS_COLOR: Record<RobotStatus, string> = {
  IDLE: '#27ae60',
  MOVING: '#2d7ff9',
  CHARGING: '#e08a00',
  ERROR: '#e0392b',
  OFFLINE: '#8a8a8a',
}

export function FactoryMap({ robots }: { robots: Robot[] }) {
  return (
    <svg className="map" viewBox={`0 0 ${MAP_W} ${MAP_H}`} preserveAspectRatio="xMidYMid meet">
      <rect x={0} y={0} width={MAP_W} height={MAP_H} className="map-bg" />

      {Object.entries(NODES).map(([name, [x, y]]) => (
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
