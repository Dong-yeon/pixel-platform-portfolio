import { useState } from 'react'
import { api } from '../../api'
import type { Layout, Robot, Task, TaskPriority } from '../../types'

const PRIORITIES: TaskPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT']

function nextTaskCode(): string {
  return 'T-' + (Math.floor(Date.now() / 1000) % 100000)
}

export function TaskPanel({
  layout,
  tasks,
  robots,
  onChanged,
}: {
  /** 출발·도착 선택지는 서버 평면도의 노드에서 나온다(하드코딩 목록 없음). */
  layout: Layout | null
  tasks: Task[]
  robots: Robot[]
  onChanged: () => void
}) {
  // 도크는 충전 자리이지 운송 목적지가 아니므로 제외한다.
  const nodeNames = (layout?.nodes ?? [])
    .filter((n) => n.nodeType !== 'DOCK')
    .map((n) => n.nodeCode)

  const [taskCode, setTaskCode] = useState(nextTaskCode)
  const [origin, setOrigin] = useState('WAREHOUSE')
  const [destination, setDestination] = useState('SHIPPING')
  const [priority, setPriority] = useState<TaskPriority>('NORMAL')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const robotCodeById = new Map(robots.map((r) => [r.id, r.robotCode]))

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.fleet.createTask({ taskCode, originNode: origin, destinationNode: destination, priority })
      setTaskCode(nextTaskCode())
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : '작업 생성 실패')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <form className="task-form" onSubmit={submit}>
        <input value={taskCode} onChange={(e) => setTaskCode(e.target.value)} aria-label="작업 코드" />
        <select value={origin} onChange={(e) => setOrigin(e.target.value)} aria-label="출발">
          {nodeNames.map((n) => (
            <option key={n}>{n}</option>
          ))}
        </select>
        <span className="arrow">→</span>
        <select value={destination} onChange={(e) => setDestination(e.target.value)} aria-label="도착">
          {nodeNames.map((n) => (
            <option key={n}>{n}</option>
          ))}
        </select>
        <select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} aria-label="우선순위">
          {PRIORITIES.map((p) => (
            <option key={p}>{p}</option>
          ))}
        </select>
        <button type="submit" disabled={busy}>
          작업 생성
        </button>
      </form>
      {error && <div className="error">{error}</div>}

      <div className="task-list">
        {tasks.length === 0 && <p className="muted small">작업이 없습니다.</p>}
        {tasks.map((t) => (
          <div key={t.id} className="task-row">
            <span className="mono">{t.taskCode}</span>
            <span className="route">
              {t.originNode} → {t.destinationNode}
            </span>
            <span className={`badge prio-${t.priority}`}>{t.priority}</span>
            <span className={`badge task-${t.status}`}>{t.status}</span>
            <span className="muted small">
              {t.assignedRobotId ? robotCodeById.get(t.assignedRobotId) ?? `#${t.assignedRobotId}` : '—'}
              {t.retryCount > 0 ? ` · 재시도 ${t.retryCount}` : ''}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
