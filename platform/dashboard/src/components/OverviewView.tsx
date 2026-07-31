import { useEffect, useState } from 'react'
import { api } from '../api'
import type {
  Equipment, EquipmentOee, Layout, ModuleKey, MrbOpenSummary, Robot, Task,
  TerminalPresence, TimelineEvent, WorkOrder,
} from '../types'
import { useRackStock } from '../useRackStock'
import { EventTimeline } from './EventTimeline'
import { MapControls } from './MapControls'
import { ALL_VIEW, UnifiedMap, type MapLayers, type MapView } from './UnifiedMap'

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
const LAYER_LABEL: { key: keyof MapLayers; label: string }[] = [
  { key: 'equipment', label: '설비' },
  { key: 'amr', label: 'AMR' },
  { key: 'routes', label: '운송경로' },
  { key: 'pop', label: 'POP·작업자' },
  { key: 'quality', label: '품질 흐름' },
]

export function OverviewView({
  layout,
  equipments,
  workOrders,
  oee,
  robots,
  tasks,
  presence,
  factoryEvents,
  fleetEvents,
  onGo,
}: {
  layout: Layout | null
  equipments: Equipment[]
  workOrders: WorkOrder[]
  oee: EquipmentOee[]
  robots: Robot[]
  tasks: Task[]
  presence: TerminalPresence[]
  factoryEvents: TimelineEvent[]
  fleetEvents: TimelineEvent[]
  onGo: (m: ModuleKey) => void
}) {
  // 지도 레이어 토글 — 밀도가 빠듯해 겹치는 시스템을 끌 수 있게 한다(지도 시각 규칙).
  const [layers, setLayers] = useState<MapLayers>({
    equipment: true, amr: true, routes: true, pop: true, quality: true,
  })
  const toggleLayer = (key: keyof MapLayers) => setLayers((prev) => ({ ...prev, [key]: !prev[key] }))

  // 보고 있는 건물·층. 기본은 공장 전경.
  const [view, setView] = useState<MapView>(ALL_VIEW)
  // 렉 적재율 — 용량은 평면도, 수량은 WMS. 코드로 맞춘다.
  const rackStock = useRackStock()

  // 품질관리실 배지 — 열려 있는 MRB. QMS가 없으면 조용히 비워 둔다(컴포저블).
  const [mrbOpen, setMrbOpen] = useState<MrbOpenSummary | null>(null)
  useEffect(() => {
    const loadMrb = () => api.qms.mrbOpen().then(setMrbOpen).catch(() => setMrbOpen(null))
    loadMrb()
    const timer = window.setInterval(loadMrb, 15_000)
    return () => window.clearInterval(timer)
  }, [])

  const running = equipments.filter((e) => e.status === 'RUNNING').length
  const down = equipments.filter((e) => e.status === 'DOWN').length
  const activeWo = workOrders.filter((w) => w.status === 'IN_PROGRESS').length

  // OEE는 **서버가 이벤트에서 계산한 값**이다(현재 교대 기준). 여기서 다시 계산하지 않는다.
  // 예전엔 work_orders 누적 수량으로 품질률만 직접 냈는데, 조회 구간 개념이 없어
  // "지금 이 교대가 어떤가"에 답하지 못했다.
  //
  // 전 설비를 합산해 공장 값을 만든다 — 설비별 값의 단순 평균은 계획가동시간이 다른 설비를
  // 같은 무게로 다뤄 조금 돌린 설비가 지표를 흔든다(서버 ofLine과 같은 원칙).
  // P의 분자는 설비별 표준CT가 달라 직접 못 더하므로, 각 설비의 P × 실가동으로 되돌려 합한다.
  const totals = oee.reduce(
    (acc, e) => ({
      planned: acc.planned + e.plannedMinutes,
      operating: acc.operating + e.operatingMinutes,
      produced: acc.produced + e.producedQty,
      defects: acc.defects + e.defectQty,
      idealMinutes: acc.idealMinutes + e.performance * e.operatingMinutes,
    }),
    { planned: 0, operating: 0, produced: 0, defects: 0, idealMinutes: 0 },
  )

  const availability = totals.planned > 0 ? totals.operating / totals.planned : null
  const performance = totals.operating > 0 ? totals.idealMinutes / totals.operating : null
  const quality = totals.produced > 0 ? (totals.produced - totals.defects) / totals.produced : null
  const overallOee =
    availability !== null && performance !== null && quality !== null
      ? availability * performance * quality
      : null

  const anomalies = oee.filter((e) => e.performanceAnomaly).length
  const pct = (v: number | null) => (v === null ? '—' : `${(v * 100).toFixed(1)}%`)

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
      {/* 지도(좌) + 지표(우)를 한 줄에 놓아 스크롤 없이 전체가 보이도록 한다. */}
      <section className="card map-panel">
        <div className="module-head">
          <h2>공장 현황</h2>
          <span className="muted small">
            설비 {equipments.length} · AMR {robots.length}
            {activeRoutes > 0 && ` · 운송 중 ${activeRoutes}`}
          </span>
        </div>
        <UnifiedMap
          layout={layout}
          equipments={equipments}
          robots={robots}
          tasks={tasks}
          presence={presence}
          mrbOpen={mrbOpen}
          rackStock={rackStock}
          view={view}
          layers={layers}
        />
        <MapControls
          layout={layout}
          view={view}
          onViewChange={setView}
          layers={layers}
          onToggleLayer={toggleLayer}
          layerLabels={LAYER_LABEL}
        />
        <div className="umap-legend">
          <span><i className="lg-swatch" style={{ background: '#27ae60' }} />설비 가동</span>
          <span><i className="lg-swatch" style={{ background: '#9aa5b4' }} />대기</span>
          <span><i className="lg-swatch" style={{ background: '#e0392b' }} />고장</span>
          <span><i className="lg-dot" style={{ background: '#2d7ff9' }} />AMR 이동</span>
          <span><i className="lg-dot" style={{ background: '#27ae60' }} />대기</span>
          <span><i className="lg-dot" style={{ background: '#e08a00' }} />충전</span>
          <span><i className="lg-swatch" style={{ background: '#c8912f' }} />적재(파렛트)</span>
          <span style={{ color: '#2d7ff9' }}>┈ 운송 경로</span>
          <span><i className="lg-swatch" style={{ background: '#7b61ff' }} />POP 단말</span>
          <span><i className="lg-swatch" style={{ background: '#2f8f5b' }} />렉 만재</span>
          <span><i className="lg-swatch" style={{ background: '#cfe6d5' }} />렉 여유</span>
          <span style={{ color: '#d95d39' }}>┈ 품질 정보 흐름</span>
        </div>
      </section>

      <section className="card module-card kpi-panel" onClick={() => onGo('factory')} role="button" tabIndex={0}>
        <div className="module-head">
          <h2>PixelFactory — OEE</h2>
          <span className="link">자세히 →</span>
        </div>
        <div className="stat-row">
          <Stat
            label="OEE"
            value={pct(overallOee)}
            sub={totals.planned > 0 ? '현재 교대' : '교대 시간 아님'}
            tone={overallOee !== null && overallOee < 0.5 ? 'bad' : ''}
          />
          <Stat
            label="가동률 A"
            value={pct(availability)}
            sub={totals.planned > 0 ? `실가동 ${totals.operating}/${totals.planned}분` : undefined}
          />
          <Stat
            label="성능 P"
            value={pct(performance)}
            // P > 1.0 은 표준CT가 실제보다 크다는 신호다. 값을 자르지 않으므로 여기서 알린다.
            sub={anomalies > 0 ? `표준CT 확인 필요 ${anomalies}대` : undefined}
            tone={anomalies > 0 ? 'warn' : ''}
          />
          <Stat
            label="품질 Q"
            value={pct(quality)}
            sub={totals.produced > 0 ? `생산 ${totals.produced} · 불량 ${totals.defects}` : '생산 실적 없음'}
          />
        </div>
        <div className="stat-row">
          <Stat label="가동 설비" value={`${running}/${equipments.length}`} />
          <Stat label="고장" value={String(down)} tone={down > 0 ? 'bad' : ''} />
          <Stat label="진행 중 작업지시" value={String(activeWo)} />
        </div>
      </section>

      <section className="card module-card kpi-panel" onClick={() => onGo('fleet')} role="button" tabIndex={0}>
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

      <section className="card timeline-panel">
        <h2>통합 이벤트 타임라인 <span className="muted small">[F] 공장 · [A] 물류</span></h2>
        <EventTimeline events={merged} />
      </section>
    </div>
  )
}
