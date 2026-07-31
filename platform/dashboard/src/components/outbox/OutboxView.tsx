import { useEffect, useState } from 'react'
import { api } from '../../api'
import type { OutboxMail } from '../../types'

/**
 * 발송함 — QMS가 보낸 알림을 메일 카드로 보여준다.
 *
 * 실제 SMTP가 아니라 Outbox(DB)에 쌓인 것이다. 클릭하면 본문이 펼쳐진다.
 */
export function OutboxView() {
  const [mails, setMails] = useState<OutboxMail[]>([])
  const [openId, setOpenId] = useState<number | null>(null)

  useEffect(() => {
    api.qms.notifications().then(setMails).catch(() => {})
    const timer = window.setInterval(() => {
      api.qms.notifications().then(setMails).catch(() => {})
    }, 15_000)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <div className="grid">
      <section className="card">
        <div className="module-head">
          <h2>발송함</h2>
          <span className="muted small">{mails.length}통 · SMTP 아님 — Outbox 적재분</span>
        </div>
        {mails.length === 0 ? (
          <p className="muted small">발송된 알림이 없습니다. MRB를 열면 심의 요청 메일이 쌓입니다.</p>
        ) : (
          <div className="mail-list">
            {mails.map((m) => (
              <div
                key={m.id}
                className={`mail-card ${openId === m.id ? 'open' : ''}`}
                onClick={() => setOpenId(openId === m.id ? null : m.id)}
                role="button"
                tabIndex={0}
              >
                <div className="mail-head">
                  <span className="mail-subject">{m.subject}</span>
                  <span className="muted small">{new Date(m.sentAt).toLocaleString()}</span>
                </div>
                <div className="muted small">
                  받는 사람: {m.recipient}
                  {m.referenceNo && <span className="mono"> · {m.referenceNo}</span>}
                  <span className="badge mail-channel"> {m.channel}</span>
                </div>
                {openId === m.id && <pre className="mail-body">{m.body}</pre>}
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
