import type { Robot } from '../../types'

export function RobotPanel({ robots }: { robots: Robot[] }) {
  if (robots.length === 0) {
    return <p className="muted small">로봇 텔레메트리 대기 중… (시뮬레이터를 실행하세요)</p>
  }
  return (
    <div className="robot-grid">
      {robots.map((r) => (
        <div key={r.robotCode} className="robot-card">
          <div className="robot-head">
            <strong>{r.robotCode}</strong>
            <span className={`badge status-${r.status}`}>{r.status}</span>
          </div>
          <div className="battery">
            <div
              className="battery-fill"
              style={{ width: `${r.batteryPercent}%`, background: r.batteryPercent < 20 ? '#e0392b' : '#27ae60' }}
            />
            <span className="battery-text">{r.batteryPercent}%</span>
          </div>
          <div className="muted small">
            ({r.posX.toFixed(1)}, {r.posY.toFixed(1)})
          </div>
        </div>
      ))}
    </div>
  )
}
