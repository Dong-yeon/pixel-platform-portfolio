import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from './api'
import { FactoryView } from './components/factory/FactoryView'
import { FleetView } from './components/fleet/FleetView'
import { InspectionView } from './components/inspection/InspectionView'
import { MasterView } from './components/master/MasterView'
import { OutboxView } from './components/outbox/OutboxView'
import { OverviewView } from './components/OverviewView'
import { PopScreen } from './components/pop/PopScreen'
import { usePlatformSocket } from './usePlatformSocket'
import type {
  AuthUser, Equipment, EquipmentOee, FleetEvent, Layout, ModuleKey, ProductionLine,
  Robot, Task, TerminalPresence, TimelineEvent, WorkOrder,
} from './types'

const MAX_EVENTS = 200

const TAB_LABEL: Record<ModuleKey, string> = {
  overview: '통합 현황',
  factory: 'PixelFactory',
  fleet: 'PixelFleet',
  pop: 'POP 단말',
  inspection: '품질 (검사·MRB)',
  outbox: '발송함',
  master: '기준정보 (차종·BOM)',
}

// 역할별 진입 화면·접근 범위(P12-4). 배열 첫 항목이 진입 탭이다.
//   ADMIN → 통합현황(전체) · OPERATOR → POP · INSPECTOR → 품질 · DISPATCHER → Fleet 관제
// 프론트 게이팅이 1차 차단이다(operator는 관제 탭이 아예 없다). 서버측 강제는 모듈 몫.
const ROLE_TABS: Record<string, ModuleKey[]> = {
  ADMIN: ['overview', 'factory', 'fleet', 'inspection', 'outbox', 'master'],
  OPERATOR: ['pop'],
  INSPECTOR: ['inspection', 'outbox'],
  DISPATCHER: ['fleet'],
}

function tabsForRole(role: string): ModuleKey[] {
  return ROLE_TABS[role] ?? ['overview']
}

export function Dashboard({ user, onLogout }: { user: AuthUser; onLogout: () => void }) {
  const allowedTabs = useMemo(() => tabsForRole(user.role), [user.role])
  const [tab, setTab] = useState<ModuleKey>(() => allowedTabs[0])

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
  // 평면도 — 좌표의 단일 출처. 못 받으면 지도를 그릴 좌표계가 없다.
  const [layout, setLayout] = useState<Layout | null>(null)
  // POP 파생 위치(presence) — 지도 키오스크 배지용. 저장값 아님, 서버가 최근 이벤트에서 계산.
  const [presence, setPresence] = useState<TerminalPresence[]>([])

  const taskTimer = useRef<number | undefined>(undefined)
  const workOrderTimer = useRef<number | undefined>(undefined)
  const presenceTimer = useRef<number | undefined>(undefined)

  const loadTasks = useCallback(() => {
    api.fleet.tasks().then(setTasks).catch(() => {})
  }, [])

  const loadWorkOrders = useCallback(() => {
    api.factory.workOrders().then(setWorkOrders).catch(() => {})
  }, [])

  const loadPresence = useCallback(() => {
    api.factory.terminalPresence().then(setPresence).catch(() => {})
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

  const schedulePresenceRefetch = useCallback(() => {
    window.clearTimeout(presenceTimer.current)
    presenceTimer.current = window.setTimeout(loadPresence, 500)
  }, [loadPresence])

  useEffect(() => {
    api.fleet.robots().then(setRobots).catch(() => {})
    api.fleet.events().then((e: FleetEvent[]) => setFleetEvents(e)).catch(() => {})
    api.factory.equipments().then(setEquipments).catch(() => {})
    api.factory.lines().then(setLines).catch(() => {})
    api.factory.events().then(setFactoryEvents).catch(() => {})
    api.factory.oeeCurrent().then(setOee).catch(() => {})
    api.factory.layout().then(setLayout).catch(() => {})
    loadTasks()
    loadWorkOrders()
    loadPresence()
  }, [loadTasks, loadWorkOrders, loadPresence])

  // presence는 이벤트로도 갱신되지만, 타임아웃 경과분은 이벤트 없이도 사라져야 한다.
  // 주기적으로 다시 파생시켜 오래된 배지를 걷어낸다(흐리기→제거).
  useEffect(() => {
    const timer = window.setInterval(loadPresence, 60_000)
    return () => window.clearInterval(timer)
  }, [loadPresence])

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
      // POP 조작(WORK_ORDER_*)은 단말 배지를 바꾼다 — presence를 다시 파생시킨다.
      // 신규 WS 토픽 없이 기존 이벤트 스트림에 얹는다.
      if (event.eventType.startsWith('WORK_ORDER_') || event.eventType === 'PRODUCTION_COMPLETED') {
        schedulePresenceRefetch()
      }
    },
    // OEE는 서버가 몇 초마다 계산해 밀어 준다 — 대시보드는 다시 계산하지 않는다.
    '/topic/factory/oee': (body: unknown) => setOee(body as EquipmentOee[]),
  }), [scheduleWorkOrderRefetch, schedulePresenceRefetch])

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
          {allowedTabs.map((key) => (
            <button
              key={key}
              className={tab === key ? 'tab active' : 'tab'}
              onClick={() => setTab(key)}
            >
              {TAB_LABEL[key]}
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
            layout={layout}
            equipments={equipments}
            workOrders={workOrders}
            oee={oee}
            robots={robots}
            tasks={tasks}
            presence={presence}
            factoryEvents={factoryEvents}
            fleetEvents={fleetEvents}
            onGo={setTab}
          />
        )}
        {tab === 'pop' && (
          <PopScreen terminals={layout?.terminals ?? []} />
        )}
        {tab === 'inspection' && <InspectionView />}
        {tab === 'outbox' && <OutboxView />}
        {tab === 'master' && <MasterView role={user.role} />}
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
            layout={layout}
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
