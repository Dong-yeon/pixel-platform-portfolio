import { useEffect, useState } from 'react'
import { api } from '../../api'
import type { Stock } from '../../api'
import type { Equipment, Layout, WorkOrder } from '../../types'

function nextOrderNo(): string {
  return 'SCN-' + (Math.floor(Date.now() / 1000) % 1000000)
}

/**
 * 데모 시나리오 러너 — 시연 중 버튼으로 이벤트를 주입하는 컨트롤 패널(P15-1, ADMIN 전용).
 *
 * <p>목표 사이클(WMS 출고지시 → AMR 운송 → POP 착수 → 설비 가공 → 불량 → NCR → MRB →
 * 홀드 해제)의 절반 이상은 이미 실제로 연결돼 있다 — 이 화면이 새로 하는 일은 두 가지뿐:
 * (1) 실재고에서 출고지시를 하나 만들어 fleet에 실제 운송을 태우는 것, (2) 시뮬레이터가
 * 명령을 못 받는 문제를 우회해 설비 고장/불량을 강제로 터뜨리는 것. 그 다음(불합격 처리→
 * MRB 상신→판정)은 기존 "품질" 탭이 이미 다 한다 — 여기서 새로 안 만든다.
 */
export function ScenarioView({
  equipments,
  workOrders,
  layout,
}: {
  equipments: Equipment[]
  workOrders: WorkOrder[]
  layout: Layout | null
}) {
  return (
    <div className="grid">
      <OutboundScenario layout={layout} />
      <EquipmentInjection equipments={equipments} workOrders={workOrders} />
    </div>
  )
}

/** 출발지에서 실제로 재고가 있는 로케이션만 고를 수 있게 한다 — blind 호출은 실패한다(D5, 부분 피킹 불가). */
function OutboundScenario({ layout }: { layout: Layout | null }) {
  const [stocks, setStocks] = useState<Stock[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [toNode, setToNode] = useState('WH-SHIP')
  const [orderNo, setOrderNo] = useState(nextOrderNo)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    api.wms.stocks().then(setStocks).catch(() => {})
  }, [])

  // 하역/출하 지점만 목적지 후보로 — 도크·연결로·게이트는 실제 운송 목적지가 아니다(TaskPanel과 같은 필터).
  const nodeNames = (layout?.nodes ?? [])
    .filter((n) => n.nodeType !== 'DOCK' && n.nodeType !== 'JUNCTION' && n.nodeType !== 'GATE')
    .map((n) => n.nodeCode)

  const selected = stocks.find((s) => s.id === selectedId) ?? null

  async function submit() {
    if (!selected) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const order = await api.wms.createOutboundOrder({
        orderNo,
        itemCode: selected.itemCode,
        fromLocationCode: selected.locationCode,
        palletCode: selected.palletCode,
        toNodeCode: toNode,
        quantity: selected.quantity,
      })
      setNotice(
        `출고지시 ${order.orderNo} 생성 완료 — fleet 작업 ${order.taskCode ?? '배차 대기'}. `
        + 'Fleet 탭에서 운송을 확인하세요.',
      )
      setOrderNo(nextOrderNo())
      setSelectedId(null)
      api.wms.stocks().then(setStocks).catch(() => {})
    } catch (err) {
      setError(err instanceof Error ? err.message : '출고지시 생성 실패')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <div className="module-head">
        <h2>출고 시나리오</h2>
      </div>
      <p className="muted small">
        실재고 한 줄을 골라 출고지시를 만듭니다 — 그 순간 fleet에 실제 운송 작업이 생깁니다.
      </p>
      {stocks.length === 0 ? (
        <p className="muted small">재고가 없습니다.</p>
      ) : (
        <div className="wo-list">
          {stocks.map((s) => (
            <div
              key={s.id}
              className={`wo-row part-row ${selectedId === s.id ? 'selected' : ''}`}
              onClick={() => setSelectedId(s.id)}
              role="button"
              tabIndex={0}
            >
              <div className="wo-top">
                <span className="mono">{s.itemCode}</span>
                <span className="badge">{s.quantity}개</span>
              </div>
              <div className="muted small">{s.locationCode} · {s.palletCode}</div>
            </div>
          ))}
        </div>
      )}

      {selected && (
        <div className="task-form" style={{ marginTop: 10 }}>
          <input value={orderNo} onChange={(e) => setOrderNo(e.target.value)} aria-label="출고지시 번호" />
          <span className="arrow">→</span>
          <select value={toNode} onChange={(e) => setToNode(e.target.value)} aria-label="목적지">
            {nodeNames.map((n) => (
              <option key={n}>{n}</option>
            ))}
          </select>
          <button type="button" disabled={busy} onClick={submit}>
            {busy ? '생성 중…' : '출고 지시 생성'}
          </button>
        </div>
      )}
      {error && <div className="error">{error}</div>}
      {notice && <div className="small" style={{ color: '#1a7f4b' }}>{notice}</div>}
    </section>
  )
}

/** 진행 중(IN_PROGRESS) 작업지시가 있는 설비만 불량 폭주 주입 대상으로 따로 추린다. */
function EquipmentInjection({ equipments, workOrders }: { equipments: Equipment[]; workOrders: WorkOrder[] }) {
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const inProgressEquipmentIds = new Set(
    workOrders.filter((w) => w.status === 'IN_PROGRESS').map((w) => w.equipmentId),
  )

  async function run(equipmentCode: string, label: string, action: () => Promise<unknown>) {
    setBusy(equipmentCode + label)
    setError(null)
    setNotice(null)
    try {
      await action()
      setNotice(`${equipmentCode} — ${label} 주입 완료.`)
    } catch (err) {
      setError(err instanceof Error ? err.message : `${label} 주입 실패`)
    } finally {
      setBusy(null)
    }
  }

  return (
    <section className="card">
      <div className="module-head">
        <h2>설비 이상 주입</h2>
      </div>
      <p className="muted small">
        시뮬레이터는 외부에서 명령할 방법이 없어(순수 발행 전용) 대신 여기서 직접 강제합니다.
        불량 폭주는 진행 중인 작업지시가 있는 설비에서만 되고, 임계(기본 3)를 넘으면 품질 탭에
        검사 대기가 실제로 뜹니다.
      </p>
      {error && <div className="error">{error}</div>}
      {notice && <div className="small" style={{ color: '#1a7f4b' }}>{notice}</div>}
      <div className="wo-list">
        {equipments.map((e) => {
          const hasInProgress = inProgressEquipmentIds.has(e.id)
          return (
            <div key={e.id} className="wo-row">
              <div className="wo-top">
                <span className="mono">{e.equipmentCode}</span>
                <span className={`badge eq-badge-${e.status}`}>{e.status}</span>
              </div>
              <div className="task-actions">
                <button
                  className="ghost small"
                  disabled={busy === e.equipmentCode + '고장'}
                  onClick={() => run(e.equipmentCode, '고장', () => api.factory.injectBreakdown(e.equipmentCode))}
                >
                  고장 주입
                </button>
                <button
                  className="ghost small"
                  disabled={busy === e.equipmentCode + '복구'}
                  onClick={() => run(e.equipmentCode, '복구', () => api.factory.injectRecover(e.equipmentCode))}
                >
                  복구
                </button>
                <button
                  className="ghost small"
                  disabled={!hasInProgress || busy === e.equipmentCode + '불량 폭주'}
                  title={hasInProgress ? undefined : '진행 중인 작업지시가 없습니다'}
                  onClick={() => run(e.equipmentCode, '불량 폭주', () => api.factory.injectDefectBurst(e.equipmentCode))}
                >
                  불량 폭주 주입
                </button>
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}
