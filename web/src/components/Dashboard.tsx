import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api'
import { useFleetSocket } from '../useFleetSocket'
import type { AuthUser, FleetEvent, Robot, Task } from '../types'
import { EventTimeline } from './EventTimeline'
import { FactoryMap } from './FactoryMap'
import { RobotPanel } from './RobotPanel'
import { TaskPanel } from './TaskPanel'

const MAX_EVENTS = 200

export function Dashboard({ user, onLogout }: { user: AuthUser; onLogout: () => void }) {
  const [robots, setRobots] = useState<Robot[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [events, setEvents] = useState<FleetEvent[]>([])
  const refetchTimer = useRef<number | undefined>(undefined)

  const refetchTasks = useCallback(() => {
    api.tasks().then(setTasks).catch(() => {})
  }, [])

  // Debounce task refetches: a burst of TASK_* events should trigger a single reload.
  const scheduleTaskRefetch = useCallback(() => {
    window.clearTimeout(refetchTimer.current)
    refetchTimer.current = window.setTimeout(refetchTasks, 300)
  }, [refetchTasks])

  useEffect(() => {
    api.robots().then(setRobots).catch(() => {})
    api.tasks().then(setTasks).catch(() => {})
    api.events().then(setEvents).catch(() => {})
  }, [])

  const connected = useFleetSocket({
    onRobot: (robot) =>
      setRobots((prev) => {
        const next = prev.some((r) => r.id === robot.id)
          ? prev.map((r) => (r.id === robot.id ? robot : r))
          : [...prev, robot]
        return next.sort((a, b) => a.robotCode.localeCompare(b.robotCode))
      }),
    onEvent: (event) => {
      setEvents((prev) => [event, ...prev].slice(0, MAX_EVENTS))
      if (event.eventType.startsWith('TASK_')) scheduleTaskRefetch()
    },
  })

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          PixelFleet <span className="muted">관제</span>
          <span className={connected ? 'pill up' : 'pill down'}>
            {connected ? '실시간 연결됨' : '연결 끊김'}
          </span>
        </div>
        <div className="user">
          <span>
            {user.name} <span className="muted">({user.role})</span>
          </span>
          <button className="ghost" onClick={onLogout}>
            로그아웃
          </button>
        </div>
      </header>

      <main className="grid">
        <section className="card map-card">
          <h2>공장 지도</h2>
          <FactoryMap robots={robots} />
          <RobotPanel robots={robots} />
        </section>

        <section className="card">
          <h2>작업</h2>
          <TaskPanel tasks={tasks} robots={robots} onChanged={refetchTasks} />
        </section>

        <section className="card">
          <h2>이벤트 타임라인</h2>
          <EventTimeline events={events} />
        </section>
      </main>
    </div>
  )
}
