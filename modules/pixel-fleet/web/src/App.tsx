import { useState } from 'react'
import { getToken, setToken } from './api'
import { Dashboard } from './components/Dashboard'
import { LoginView } from './components/LoginView'
import type { AuthUser } from './types'

const USER_KEY = 'pf_user'

function loadUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? (JSON.parse(raw) as AuthUser) : null
}

export function App() {
  const [user, setUser] = useState<AuthUser | null>(() => (getToken() ? loadUser() : null))

  function handleLogin(authUser: AuthUser) {
    setToken(authUser.accessToken)
    localStorage.setItem(USER_KEY, JSON.stringify(authUser))
    setUser(authUser)
  }

  function handleLogout() {
    setToken(null)
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }

  if (!user) {
    return <LoginView onLogin={handleLogin} />
  }
  return <Dashboard user={user} onLogout={handleLogout} />
}
