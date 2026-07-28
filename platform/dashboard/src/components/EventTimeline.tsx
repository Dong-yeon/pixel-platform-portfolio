import { memo } from 'react'
import type { TimelineEvent } from '../types'

function time(iso: string): string {
  return iso ? iso.replace('T', ' ').slice(11, 19) : ''
}

function EventTimelineImpl({ events }: { events: TimelineEvent[] }) {
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

/**
 * 로봇 위치는 초당 여러 번 갱신되는데, 그때마다 이 목록(최대 200행)까지 다시 그리면
 * 지도 애니메이션이 끊긴다. events 배열이 그대로면 렌더를 건너뛴다.
 */
export const EventTimeline = memo(EventTimelineImpl)
