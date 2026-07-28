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
  // 두 모듈 토큰이 모두 있어야 로그인 상태로 본다(하나만 남았으면 다시 로그인).
  const [user, setUser] = useState<AuthUser | null>(() =>
    getToken('fleet') && getToken('factory') ? loadUser() : null,
  )

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
