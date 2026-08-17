import { useState } from 'react'
import { api } from '../api'
import type { AuthUser } from '../types'

export function LoginView({ onLogin }: { onLogin: (user: AuthUser) => void }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('password')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function loginAs(id: string, pw: string) {
    setBusy(true)
    setError(null)
    try {
      onLogin(await api.login(id, pw))
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인 실패')
    } finally {
      setBusy(false)
    }
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    await loginAs(username, password)
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
        {/* 채용담당자·방문자용 최단 경로 — 입력 없이 관리자 데모 계정으로 입장한다.
            계정은 어차피 README에 공개돼 있으므로 보안상 잃는 것이 없다. */}
        <button type="button" className="guest" disabled={busy} onClick={() => loginAs('admin', 'password')}>
          게스트로 바로 둘러보기
        </button>
        <p className="muted small">데모: admin / operator · 비밀번호 password</p>
      </form>
    </div>
  )
}
