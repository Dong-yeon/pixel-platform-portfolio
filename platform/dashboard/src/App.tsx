import { useState } from 'react'
import { clearTokens, getToken } from './api'
import { Dashboard } from './Dashboard'
import { LoginView } from './components/LoginView'
import type { AuthUser } from './types'

const USER_KEY = 'pp_user'

function loadUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? (JSON.parse(raw) as AuthUser) : null
}

export function App() {
  // 플랫폼 토큰 하나 — 게이트웨이가 모든 모듈 앞에서 이 토큰을 검증한다.
  const [user, setUser] = useState<AuthUser | null>(() => (getToken() ? loadUser() : null))

  function handleLogin(authUser: AuthUser) {
    localStorage.setItem(USER_KEY, JSON.stringify(authUser))
    setUser(authUser)
  }

  function handleLogout() {
    clearTokens()
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }

  if (!user) {
    return <LoginView onLogin={handleLogin} />
  }
  return <Dashboard user={user} onLogout={handleLogout} />
}
