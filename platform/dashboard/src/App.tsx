import { useState } from 'react'
import { clearTokens, getToken } from './api'
import { Dashboard } from './Dashboard'
import { LoginView } from './components/LoginView'
import { PopScreen } from './components/pop/PopScreen'
import type { AuthUser } from './types'

const USER_KEY = 'pp_user'

function loadUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? (JSON.parse(raw) as AuthUser) : null
}

/**
 * POP 키오스크 진입 — 라우터 없이 경로만 본다. `/pop/POP-A1` 이면 그 단말에 고정된
 * POP 화면을 띄운다(현장 키오스크는 이 URL로 북마크된다). 역할과 무관하게 단말 화면이 우선한다.
 */
function popTerminalFromPath(): string | null {
  const match = window.location.pathname.match(/^\/pop\/([^/]+)/)
  return match ? decodeURIComponent(match[1]) : null
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

  // 키오스크 URL(/pop/{단말})이면 대시보드 대신 그 단말의 POP 화면으로 바로 간다.
  const kioskTerminal = popTerminalFromPath()
  if (kioskTerminal) {
    return <PopScreen terminalCode={kioskTerminal} terminals={[]} onLogout={handleLogout} />
  }

  return <Dashboard user={user} onLogout={handleLogout} />
}
