import type { AuthUser, FleetEvent, Robot, Task, TaskPriority } from './types'

const TOKEN_KEY = 'pf_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message)
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const res = await fetch('/api' + path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {}),
    },
  })
  const body = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(body?.error?.message ?? res.statusText, res.status)
  }
  return body.data as T
}

export interface CreateTaskInput {
  taskCode: string
  originNode: string
  destinationNode: string
  priority: TaskPriority
}

export const api = {
  login: (username: string, password: string) =>
    request<AuthUser>('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  robots: () => request<Robot[]>('/robots'),
  tasks: () => request<Task[]>('/tasks'),
  events: () => request<FleetEvent[]>('/events'),
  createTask: (input: CreateTaskInput) =>
    request<Task>('/tasks', { method: 'POST', body: JSON.stringify(input) }),
  dispatch: () => request<Task | null>('/tasks/dispatch', { method: 'POST' }),
}
