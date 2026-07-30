import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from './api'
import { FactoryView } from './components/factory/FactoryView'
import { FleetView } from './components/fleet/FleetView'
import { OverviewView } from './components/OverviewView'
import { usePlatformSocket } from './usePlatformSocket'
import type {
  AuthUser, Equipment, EquipmentOee, FleetEvent, ModuleKey, ProductionLine,
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
  const [oee, setOee] = useState<EquipmentOee[]>([])

  const taskTimer = useRef<number | undefined>(undefined)
  const workOrderTimer = useRef<number | undefined>(undefined)

  const loadTasks = useCallback(() => {
    api.fleet.tasks().then(setTasks).catch(() => {})
  }, [])

  const loadWorkOrders = useCallback(() => {
    api.factory.workOrders().then(setWorkOrders).catch(() => {})
  }, [])

  // 같은 종류 이벤트가 몰려올 때 목록 리페치를 한 번으로 묶는다.
  const scheduleTaskRefetch = useCallback(() => {
    window.clearTimeout(taskTimer.current)
    taskTimer.current = window.setTimeout(loadTasks, 300)
  }, [loadTasks])

  const scheduleWorkOrderRefetch = useCallback(() => {
    window.clearTimeout(workOrderTimer.current)
    workOrderTimer.current = window.setTimeout(loadWorkOrders, 500)
  }, [loadWorkOrders])

  useEffect(() => {
    api.fleet.robots().then(setRobots).catch(() => {})
    api.fleet.events().then((e: FleetEvent[]) => setFleetEvents(e)).catch(() => {})
    api.factory.equipments().then(setEquipments).catch(() => {})
    api.factory.lines().then(setLines).catch(() => {})
    api.factory.events().then(setFactoryEvents).catch(() => {})
    api.factory.oeeCurrent().then(setOee).catch(() => {})
    loadTasks()
    loadWorkOrders()
  }, [loadTasks, loadWorkOrders])

  // ---- 실시간 ----
  // 모듈마다 별개 연결이다. 한쪽이 재기동돼도 다른 쪽 실시간은 살아 있어야 한다.

  const fleetTopics = useMemo(() => ({
    '/topic/robots': (body: unknown) => {
      const robot = body as Robot
      setRobots((prev) => {
        const next = prev.some((r) => r.id === robot.id)
          ? prev.map((r) => (r.id === robot.id ? robot : r))
          : [...prev, robot]
        return next.sort((a, b) => a.robotCode.localeCompare(b.robotCode))
      })
    },
    '/topic/events': (body: unknown) => {
      const event = body as FleetEvent
      setFleetEvents((prev) => [event, ...prev].slice(0, MAX_EVENTS))
      if (event.eventType.startsWith('TASK_')) scheduleTaskRefetch()
    },
  }), [scheduleTaskRefetch])

  const factoryTopics = useMemo(() => ({
    '/topic/factory/equipments': (body: unknown) => {
      const equipment = body as Equipment
      setEquipments((prev) => prev.map((e) => (e.id === equipment.id ? equipment : e)))
    },
    '/topic/factory/events': (body: unknown) => {
      const event = body as TimelineEvent
      setFactoryEvents((prev) => [event, ...prev].slice(0, MAX_EVENTS))
      // 사이클이 작업지시 실적을 올리므로 진척바가 따라가게 한다(몰려오니 묶어서).
      if (event.eventType === 'CYCLE_COMPLETED' || event.eventType.startsWith('WORK_ORDER_')) {
        scheduleWorkOrderRefetch()
      }
    },
    // OEE는 서버가 몇 초마다 계산해 밀어 준다 — 대시보드는 다시 계산하지 않는다.
    '/topic/factory/oee': (body: unknown) => setOee(body as EquipmentOee[]),
  }), [scheduleWorkOrderRefetch])

  const fleetConnected = usePlatformSocket('/ws/fleet', fleetTopics)
  const factoryConnected = usePlatformSocket('/ws/factory', factoryTopics)

  // 둘 다 붙어야 "실시간 연결됨"이다. 하나만 살아 있는데 초록 불이면 화면 절반이 멈춘 걸
  // 모른 채 보게 되므로, 어느 쪽이 끊겼는지 표시한다.
  const connected = fleetConnected && factoryConnected
  const partial = fleetConnected !== factoryConnected

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          Pixel<span className="brand-accent">Platform</span>
          <span className={connected ? 'pill up' : 'pill down'}>
            {connected
              ? '실시간 연결됨'
              : partial
                ? `일부 연결 (${fleetConnected ? '물류' : '공장'}만)`
                : '연결 끊김'}
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
            oee={oee}
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
          <FleetView
            robots={robots}
            tasks={tasks}
            equipments={equipments}
            events={fleetEvents}
            onTaskChanged={loadTasks}
          />
        )}
      </main>
    </div>
  )
}
