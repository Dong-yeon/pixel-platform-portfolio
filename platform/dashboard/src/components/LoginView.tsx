import { useState } from 'react'
import { api } from '../api'
import type { AuthUser } from '../types'

export function LoginView({ onLogin }: { onLogin: (user: AuthUser) => void }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('password')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onLogin(await api.loginAll(username, password))
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인 실패')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <h1>Pixel Platform</h1>
        <p className="muted">스마트팩토리 통합 관제</p>
        <label>
          아이디
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </label>
        <label>
          비밀번호
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <div className="error">{error}</div>}
        <button type="submit" disabled={busy}>
          {busy ? '로그인 중…' : '로그인'}
        </button>
        <p className="muted small">데모: admin / operator · 비밀번호 password</p>
      </form>
    </div>
  )
}
