import type {
  AuthUser, Equipment, FleetEvent, ProductionLine,
  Robot, Task, TaskPriority, TimelineEvent, WorkOrder,
} from './types'

// 모든 호출은 게이트웨이(9000)로 나가고, 접두사로 모듈이 정해진다.
//   /api/auth/**     → 인증 담당 모듈 (플랫폼 로그인 창구)
//   /api/factory/**  → pixel-factory
//   /api/fleet/**    → pixel-fleet
const AUTH = '/api/auth'
const FACTORY = '/api/factory'
const FLEET = '/api/fleet'

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
  },
}
