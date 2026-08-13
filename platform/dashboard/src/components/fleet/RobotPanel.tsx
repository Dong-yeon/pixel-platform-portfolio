import { useState } from 'react'
import { api } from '../../api'
import type { Robot } from '../../types'

export function RobotPanel({ robots }: { robots: Robot[] }) {
  const [busyId, setBusyId] = useState<number | null>(null)

  // 로봇 상태는 /topic/robots가 곧바로 다시 밀어주므로, 여기서는 호출만 하고 별도 새로고침은 안 한다.
  async function run(id: number, action: () => Promise<void>) {
    setBusyId(id)
    try {
      await action()
    } catch {
      // 실패해도 화면은 다음 텔레메트리/이벤트로 정정된다 — 별도 에러 배너는 과하다.
    } finally {
      setBusyId(null)
    }
  }

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
            {r.robotType === 'RACK_FEEDER' && (
              <span className="badge muted" title="창고동 렉 전용 — 자기 존 밖으로 나가지 않는다">
                랙 피더{r.zoneCode ? ` · ${r.zoneCode}` : ''}
              </span>
            )}
            {r.offDuty && <span className="badge muted">휴무</span>}
            {r.disabled && <span className="badge muted">잠김</span>}
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
          <div className="robot-actions">
            <button
              className="ghost small"
              disabled={busyId === r.id}
              onClick={() => run(r.id, () => (r.offDuty ? api.fleet.setOnDuty(r.id) : api.fleet.setOffDuty(r.id)))}
            >
              {r.offDuty ? '복귀' : '휴무'}
            </button>
            <button
              className="ghost small"
              disabled={busyId === r.id}
              onClick={() => run(r.id, () => (r.disabled ? api.fleet.enableRobot(r.id) : api.fleet.disableRobot(r.id)))}
            >
              {r.disabled ? '잠금 해제' : '잠금'}
            </button>
            {r.status === 'ERROR' && (
              <button className="ghost small" disabled={busyId === r.id} onClick={() => run(r.id, () => api.fleet.clearAlarm(r.id))}>
                경보 해제
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}
