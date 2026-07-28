import type { FleetEvent } from '../types'

function time(iso: string): string {
  return iso ? iso.replace('T', ' ').slice(11, 19) : ''
}

export function EventTimeline({ events }: { events: FleetEvent[] }) {
  if (events.length === 0) {
    return <p className="muted small">이벤트 대기 중…</p>
  }
  return (
    <div className="timeline">
      {events.map((e) => (
        <div key={e.id} className={`event sev-${e.severity}`}>
          <span className="ev-time">{time(e.occurredAt)}</span>
          <span className="ev-type">{e.eventType}</span>
          <span className="ev-msg">{e.message}</span>
        </div>
      ))}
    </div>
  )
}
