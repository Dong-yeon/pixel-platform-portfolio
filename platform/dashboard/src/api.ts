import type {
  AuthUser, Equipment, FactoryEventRaw, FleetEvent, ProductionLine,
  Robot, Task, TaskPriority, TimelineEvent, WorkOrder,
} from './types'

// 모든 호출은 게이트웨이(9000)로 나가고, 접두사로 모듈이 정해진다.
//   /api/factory/**  → pixel-factory
//   /api/fleet/**    → pixel-fleet
const FACTORY = '/api/factory'
const FLEET = '/api/fleet'

// 모듈마다 자체 JWT를 발급한다(현재는 모듈이 각자 인증).
// P6에서 게이트웨이 중앙 인증으로 바뀌면 토큰 하나로 합쳐진다.
const TOKEN_KEYS = { factory: 'pp_token_factory', fleet: 'pp_token_fleet' } as const
type Realm = keyof typeof TOKEN_KEYS

export function getToken(realm: Realm): string | null {
  return localStorage.getItem(TOKEN_KEYS[realm])
}

export function setToken(realm: Realm, token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEYS[realm], token)
  else localStorage.removeItem(TOKEN_KEYS[realm])
}

export function clearTokens(): void {
  (Object.keys(TOKEN_KEYS) as Realm[]).forEach((r) => setToken(r, null))
}

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message)
  }
}

async function request<T>(realm: Realm, path: string, options: RequestInit = {}): Promise<T> {
  const base = realm === 'factory' ? FACTORY : FLEET
  const token = getToken(realm)
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
  if (res.status === 401 && getToken(realm)) {
    clearTokens()
    window.location.reload()
  }

  const body = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(body?.error?.message ?? res.statusText, res.status)
  }
  return body.data as T
}

async function login(realm: Realm, username: string, password: string): Promise<AuthUser> {
  const user = await request<AuthUser>(realm, '/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  setToken(realm, user.accessToken)
  return user
}

/** factory 이벤트(createdAt)를 공통 타임라인 형태(occurredAt)로 맞춘다. */
function toTimeline(e: FactoryEventRaw): TimelineEvent {
  return {
    id: e.id,
    eventType: e.eventType,
    severity: e.severity,
    message: e.message,
    occurredAt: e.createdAt,
  }
}

export const api = {
  /** 두 모듈에 각각 로그인한다. 데모 계정(admin/password)은 양쪽에 모두 존재한다. */
  async loginAll(username: string, password: string): Promise<AuthUser> {
    const [fleetUser] = await Promise.all([
      login('fleet', username, password),
      login('factory', username, password),
    ])
    return fleetUser
  },

  fleet: {
    robots: () => request<Robot[]>('fleet', '/robots'),
    tasks: () => request<Task[]>('fleet', '/tasks'),
    events: () => request<FleetEvent[]>('fleet', '/events'),
    createTask: (input: {
      taskCode: string; originNode: string; destinationNode: string; priority: TaskPriority
    }) => request<Task>('fleet', '/tasks', { method: 'POST', body: JSON.stringify(input) }),
  },

  factory: {
    equipments: () => request<Equipment[]>('factory', '/equipments'),
    lines: () => request<ProductionLine[]>('factory', '/lines'),
    workOrders: () => request<WorkOrder[]>('factory', '/work-orders'),
    events: async (): Promise<TimelineEvent[]> =>
      (await request<FactoryEventRaw[]>('factory', '/events/recent')).map(toTimeline),
    startWorkOrder: (id: number) =>
      request<WorkOrder>('factory', `/work-orders/${id}/start`, { method: 'PATCH' }),
  },
}
