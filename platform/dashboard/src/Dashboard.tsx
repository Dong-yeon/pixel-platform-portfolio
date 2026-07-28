import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from './api'
import { FactoryView } from './components/factory/FactoryView'
import { FleetView } from './components/fleet/FleetView'
import { OverviewView } from './components/OverviewView'
import { useFleetSocket } from './useFleetSocket'
import type {
  AuthUser, Equipment, FleetEvent, ModuleKey, ProductionLine,
  Robot, Task, TimelineEvent, WorkOrder,
} from './types'

const MAX_EVENTS = 200
const TABS: { key: ModuleKey; label: string }[] = [
  { key: 'overview', label: '통합 현황' },
  { key: 'factory', label: 'PixelFactory' },
  { key: 'fleet', label: 'PixelFleet' },
]

export function Dashboard({ user, onLogout }: { user: AuthUser; onLogout: () => void }) {
  const [tab, setTab] = useState<ModuleKey>('overview')

  // fleet
  const [robots, setRobots] = useState<Robot[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [fleetEvents, setFleetEvents] = useState<TimelineEvent[]>([])
  // factory
  const [equipments, setEquipments] = useState<Equipment[]>([])
  const [lines, setLines] = useState<ProductionLine[]>([])
  const [workOrders, setWorkOrders] = useState<WorkOrder[]>([])
  const [factoryEvents, setFactoryEvents] = useState<TimelineEvent[]>([])

  const taskTimer = useRef<number | undefined>(undefined)

  const loadTasks = useCallback(() => {
    api.fleet.tasks().then(setTasks).catch(() => {})
  }, [])

  const loadWorkOrders = useCallback(() => {
    api.factory.workOrders().then(setWorkOrders).catch(() => {})
  }, [])

  // TASK_* 이벤트가 몰려올 때 목록 리페치를 한 번으로 묶는다.
  const scheduleTaskRefetch = useCallback(() => {
    window.clearTimeout(taskTimer.current)
    taskTimer.current = window.setTimeout(loadTasks, 300)
  }, [loadTasks])

  useEffect(() => {
    api.fleet.robots().then(setRobots).catch(() => {})
    api.fleet.events().then((e: FleetEvent[]) => setFleetEvents(e)).catch(() => {})
    api.factory.equipments().then(setEquipments).catch(() => {})
    api.factory.lines().then(setLines).catch(() => {})
    api.factory.events().then(setFactoryEvents).catch(() => {})
    loadTasks()
    loadWorkOrders()
  }, [loadTasks, loadWorkOrders])

  // 실시간은 현재 fleet만 제공한다(factory는 폴링 없이 조회 시점 기준).
  const connected = useFleetSocket({
    onRobot: (robot) =>
      setRobots((prev) => {
        const next = prev.some((r) => r.id === robot.id)
          ? prev.map((r) => (r.id === robot.id ? robot : r))
          : [...prev, robot]
        return next.sort((a, b) => a.robotCode.localeCompare(b.robotCode))
      }),
    onEvent: (event) => {
      setFleetEvents((prev) => [event, ...prev].slice(0, MAX_EVENTS))
      if (event.eventType.startsWith('TASK_')) scheduleTaskRefetch()
    },
  })

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          Pixel<span className="brand-accent">Platform</span>
          <span className={connected ? 'pill up' : 'pill down'}>
            {connected ? '실시간 연결됨' : '연결 끊김'}
          </span>
        </div>
        <nav className="tabs">
          {TABS.map((t) => (
            <button
              key={t.key}
              className={tab === t.key ? 'tab active' : 'tab'}
              onClick={() => setTab(t.key)}
            >
              {t.label}
            </button>
          ))}
        </nav>
        <div className="user">
          <span>
            {user.name} <span className="muted">({user.role})</span>
          </span>
          <button className="ghost" onClick={onLogout}>
            로그아웃
          </button>
        </div>
      </header>

      <main className="content">
        {tab === 'overview' && (
          <OverviewView
            equipments={equipments}
            workOrders={workOrders}
            robots={robots}
            tasks={tasks}
            factoryEvents={factoryEvents}
            fleetEvents={fleetEvents}
            onGo={setTab}
          />
        )}
        {tab === 'factory' && (
          <FactoryView
            equipments={equipments}
            lines={lines}
            workOrders={workOrders}
            events={factoryEvents}
            onWorkOrderChanged={loadWorkOrders}
          />
        )}
        {tab === 'fleet' && (
          <FleetView robots={robots} tasks={tasks} events={fleetEvents} onTaskChanged={loadTasks} />
        )}
      </main>
    </div>
  )
}
