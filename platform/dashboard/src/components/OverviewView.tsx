import type { Equipment, ModuleKey, Robot, Task, TimelineEvent, WorkOrder } from '../types'
import { EventTimeline } from './EventTimeline'
import { UnifiedMap } from './UnifiedMap'

function Stat({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: string }) {
  return (
    <div className={`stat ${tone ?? ''}`}>
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
      {sub && <div className="stat-sub muted small">{sub}</div>}
    </div>
  )
}

/**
 * 플랫폼 요약 — 두 모듈의 지표를 한 화면에 모은다.
 * 이벤트 타임라인은 factory·fleet을 시간순으로 합쳐 보여준다.
 */
export function OverviewView({
  equipments,
  workOrders,
  robots,
  tasks,
  factoryEvents,
  fleetEvents,
  onGo,
}: {
  equipments: Equipment[]
  workOrders: WorkOrder[]
  robots: Robot[]
  tasks: Task[]
  factoryEvents: TimelineEvent[]
  fleetEvents: TimelineEvent[]
  onGo: (m: ModuleKey) => void
}) {
  const running = equipments.filter((e) => e.status === 'RUNNING').length
  const down = equipments.filter((e) => e.status === 'DOWN').length
  const activeWo = workOrders.filter((w) => w.status === 'IN_PROGRESS').length
  const produced = workOrders.reduce((s, w) => s + w.producedQty, 0)
  const defects = workOrders.reduce((s, w) => s + w.defectQty, 0)
  const quality = produced > 0 ? ((produced - defects) / produced) * 100 : null

  const online = robots.filter((r) => r.status !== 'OFFLINE').length
  const moving = robots.filter((r) => r.status === 'MOVING').length
  const pending = tasks.filter((t) => t.status === 'PENDING' || t.status === 'ASSIGNED').length
  const failed = tasks.filter((t) => t.status === 'FAILED').length
  const avgBattery = robots.length
    ? Math.round(robots.reduce((s, r) => s + r.batteryPercent, 0) / robots.length)
    : null

  // 두 모듈 이벤트를 합쳐 최신순으로. id가 모듈별로 겹치므로 키에 접두사를 붙인다.
  const merged: TimelineEvent[] = [
    ...factoryEvents.map((e) => ({ ...e, id: e.id, eventType: `[F] ${e.eventType}` })),
    ...fleetEvents.map((e) => ({ ...e, id: e.id + 1_000_000, eventType: `[A] ${e.eventType}` })),
  ]
    .sort((a, b) => (a.occurredAt < b.occurredAt ? 1 : -1))
    .slice(0, 60)

  const activeRoutes = tasks.filter((t) => t.status === 'ASSIGNED' || t.status === 'IN_PROGRESS').length

  return (
    <div className="overview">
      <section className="card">
        <div className="module-head">
          <h2>공장 현황</h2>
          <span className="muted small">
            설비 {equipments.length} · AMR {robots.length}
            {activeRoutes > 0 && ` · 운송 중 ${activeRoutes}`}
          </span>
        </div>
        <UnifiedMap equipments={equipments} robots={robots} tasks={tasks} />
        <div className="umap-legend">
          <span><i className="lg-swatch" style={{ background: '#27ae60' }} />설비 가동</span>
          <span><i className="lg-swatch" style={{ background: '#9aa5b4' }} />설비 대기</span>
          <span><i className="lg-swatch" style={{ background: '#e0392b' }} />설비 고장</span>
          <span><i className="lg-dot" style={{ background: '#2d7ff9' }} />AMR 이동</span>
          <span><i className="lg-dot" style={{ background: '#27ae60' }} />AMR 대기</span>
          <span><i className="lg-dot" style={{ background: '#e08a00' }} />AMR 충전</span>
          <span style={{ color: '#2d7ff9' }}>┈ 운송 경로</span>
        </div>
      </section>

      <section className="card module-card" onClick={() => onGo('factory')} role="button" tabIndex={0}>
        <div className="module-head">
          <h2>PixelFactory — OEE</h2>
          <span className="link">자세히 →</span>
        </div>
        <div className="stat-row">
          <Stat label="가동 설비" value={`${running}/${equipments.length}`} />
          <Stat label="고장" value={String(down)} tone={down > 0 ? 'bad' : ''} />
          <Stat label="진행 중 작업지시" value={String(activeWo)} />
          <Stat
            label="품질률"
            value={quality === null ? '—' : `${quality.toFixed(1)}%`}
            sub={produced > 0 ? `생산 ${produced} · 불량 ${defects}` : '생산 실적 없음'}
          />
        </div>
      </section>

      <section className="card module-card" onClick={() => onGo('fleet')} role="button" tabIndex={0}>
        <div className="module-head">
          <h2>PixelFleet — AMR</h2>
          <span className="link">자세히 →</span>
        </div>
        <div className="stat-row">
          <Stat label="온라인 로봇" value={`${online}/${robots.length}`} />
          <Stat label="이동 중" value={String(moving)} />
          <Stat label="대기 작업" value={String(pending)} tone={pending > 3 ? 'warn' : ''} />
          <Stat
            label="평균 배터리"
            value={avgBattery === null ? '—' : `${avgBattery}%`}
            sub={failed > 0 ? `실패 작업 ${failed}` : undefined}
            tone={avgBattery !== null && avgBattery < 30 ? 'bad' : ''}
          />
        </div>
      </section>

      <section className="card">
        <h2>통합 이벤트 타임라인 <span className="muted small">[F] 공장 · [A] 물류</span></h2>
        <EventTimeline events={merged} />
      </section>
    </div>
  )
}
