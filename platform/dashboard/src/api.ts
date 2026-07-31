import type {
  AuthUser, Equipment, EquipmentOee, FleetEvent, Inspection, Layout, MrbDecision,
  MrbOpenSummary, MrbReview, Nonconformance, OutboxMail, ProductionLine,
  Robot, Task, TaskPriority, TerminalPresence, TimelineEvent, WorkOrder,
} from './types'

/** POP 화면 초기 데이터 — 단말 + 로그인 작업자의 작업지시. */
export interface PopBoard {
  terminal: { id: number; terminalCode: string; name: string; lineId: number; posX: number; posY: number }
  workOrders: WorkOrder[]
}

// 모든 호출은 게이트웨이(9000)로 나가고, 접두사로 모듈이 정해진다.
//   /api/auth/**     → 인증 담당 모듈 (플랫폼 로그인 창구)
//   /api/factory/**  → pixel-factory
//   /api/fleet/**    → pixel-fleet
const AUTH = '/api/auth'
const FACTORY = '/api/factory'
const FLEET = '/api/fleet'
const QMS = '/api/qms'
const WMS = '/api/wms'

/** WMS 재고 한 줄 — 로케이션(=렉) × 품목. */
export interface Stock {
  id: number
  locationCode: string
  itemCode: string
  quantity: number
}

// 게이트웨이 중앙 인증(P6) — 토큰 하나로 모든 모듈에 접근한다.
// 모듈들이 같은 서명 키를 쓰므로 로그인은 한 번이면 되고, 게이트웨이가 관문에서 검증한다.
const TOKEN_KEY = 'pp_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export function clearTokens(): void {
  setToken(null)
}

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message)
  }
}

async function request<T>(base: string, path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const res = await fetch(base + path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {}),
    },
  })
  // 토큰 만료(JWT 2시간)면 모든 조회가 조용히 실패해 화면이 텅 빈 채로 남는다.
  // 사용자가 이유를 알 수 없으므로 토큰을 지우고 로그인 화면으로 되돌린다.
  if (res.status === 401 && token) {
    clearTokens()
    window.location.reload()
  }

  const body = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(body?.error?.message ?? res.statusText, res.status)
  }
  return body.data as T
}

export const api = {
  /** 플랫폼 로그인 — 한 번으로 모든 모듈에 통하는 토큰을 받는다. */
  async login(username: string, password: string): Promise<AuthUser> {
    const user = await request<AuthUser>(AUTH, '/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    setToken(user.accessToken)
    return user
  },

  fleet: {
    robots: () => request<Robot[]>(FLEET, '/robots'),
    tasks: () => request<Task[]>(FLEET, '/tasks'),
    events: () => request<FleetEvent[]>(FLEET, '/events'),
    createTask: (input: {
      taskCode: string; originNode: string; destinationNode: string; priority: TaskPriority
    }) => request<Task>(FLEET, '/tasks', { method: 'POST', body: JSON.stringify(input) }),
  },

  factory: {
    equipments: () => request<Equipment[]>(FACTORY, '/equipments'),
    lines: () => request<ProductionLine[]>(FACTORY, '/lines'),
    workOrders: () => request<WorkOrder[]>(FACTORY, '/work-orders'),
    // 서버가 occurredAt(발생시각)을 내려주므로 변환 없이 그대로 쓴다.
    events: () => request<TimelineEvent[]>(FACTORY, '/events/recent'),
    startWorkOrder: (id: number) =>
      request<WorkOrder>(FACTORY, `/work-orders/${id}/start`, { method: 'PATCH' }),
    /** 현재 교대 기준 전 설비 OEE. 이후 갱신은 WebSocket(/topic/factory/oee)이 밀어 준다. */
    oeeCurrent: () => request<EquipmentOee[]>(FACTORY, '/oee/current'),
    /** 공장 평면도 — 좌표의 단일 출처. 대시보드는 좌표를 하드코딩하지 않는다. */
    layout: () => request<Layout>(FACTORY, '/layout'),
    /** 파생 위치(presence) — 지도 키오스크 배지용. 저장값 아님, 최근 TERMINAL 이벤트에서 계산. */
    terminalPresence: () => request<TerminalPresence[]>(FACTORY, '/terminals/presence'),
    /** 내게 배정된 작업지시(인증된 사용자 기준). POP·현장용. */
    myWorkOrders: () => request<WorkOrder[]>(FACTORY, '/work-orders/my'),
  },

  /** 창고(WMS) — 재고. 로케이션 코드가 곧 렉 코드라 지도의 적재율이 여기서 나온다. */
  wms: {
    stocks: () => request<Stock[]>(WMS, '/stocks'),
  },

  /** 품질(QMS) — 검사·부적합·MRB 심의·발송함. */
  qms: {
    inspections: () => request<Inspection[]>(QMS, '/inspections'),
    pendingInspections: () => request<Inspection[]>(QMS, '/inspections/pending'),
    completeInspection: (id: number, input: {
      result: 'PASSED' | 'FAILED'; inspectedQty?: number; defectQty?: number; note?: string; defectCode?: string
    }) => request<Inspection>(QMS, `/inspections/${id}/complete`, { method: 'POST', body: JSON.stringify(input) }),
    nonconformances: () => request<Nonconformance[]>(QMS, '/nonconformances'),
    mrbList: () => request<MrbReview[]>(QMS, '/mrb'),
    /** 지도 품질관리실 배지 — 열려 있는 심의. */
    mrbOpen: () => request<MrbOpenSummary>(QMS, '/mrb/open'),
    raiseMrb: (nonconformanceId: number) =>
      request<MrbReview>(QMS, '/mrb', { method: 'POST', body: JSON.stringify({ nonconformanceId }) }),
    startMrbReview: (id: number) => request<MrbReview>(QMS, `/mrb/${id}/start-review`, { method: 'POST' }),
    decideMrb: (id: number, decision: MrbDecision, decisionNote?: string) =>
      request<MrbReview>(QMS, `/mrb/${id}/decide`, { method: 'POST', body: JSON.stringify({ decision, decisionNote }) }),
    closeMrb: (id: number) => request<MrbReview>(QMS, `/mrb/${id}/close`, { method: 'POST' }),
    /** 발송함 — Outbox에 쌓인 메일 카드. */
    notifications: () => request<OutboxMail[]>(QMS, '/notifications'),
  },

  /** POP(Point of Production) 단말 — 현장 작업자 전용 조작. */
  pop: {
    board: (terminalCode: string) => request<PopBoard>(FACTORY, `/pop/${terminalCode}`),
    start: (terminalCode: string, id: number) =>
      request<WorkOrder>(FACTORY, `/pop/${terminalCode}/work-orders/${id}/start`, { method: 'POST' }),
    completeProduction: (terminalCode: string, id: number, input: { producedQty: number; defectQty: number }) =>
      request<WorkOrder>(FACTORY, `/pop/${terminalCode}/work-orders/${id}/complete-production`, {
        method: 'POST',
        body: JSON.stringify(input),
      }),
    close: (terminalCode: string, id: number) =>
      request<WorkOrder>(FACTORY, `/pop/${terminalCode}/work-orders/${id}/close`, { method: 'POST' }),
  },
}
