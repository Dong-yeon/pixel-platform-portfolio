import { useCallback, useEffect, useState } from 'react'
import { api, type PopBoard } from '../../api'
import type { LayoutTerminal, WorkOrder } from '../../types'

/**
 * POP(Point of Production) 단말 화면 — 현장 작업자용, 터치 친화적 큰 버튼.
 *
 * 흐름은 최소로: (로그인은 상위에서) → 내 작업지시 목록 → 착수 → 실적/불량 입력 → 종료.
 * 착수/실적/종료는 이 단말을 출처로 이벤트에 남아, 통합 지도의 키오스크에 담당자 배지가 뜬다.
 *
 * - 키오스크(App): `/pop/{terminalCode}` 로 단말이 고정된다.
 * - 역할 진입(Dashboard OPERATOR): 단말이 안 정해졌으면 목록에서 하나 고른 뒤 화면이 뜬다.
 */
export function PopScreen({
  terminalCode,
  terminals,
  onLogout,
}: {
  terminalCode?: string
  terminals: LayoutTerminal[]
  onLogout?: () => void
}) {
  const [selected, setSelected] = useState<string | null>(
    terminalCode ?? (terminals.length === 1 ? terminals[0].terminalCode : null),
  )
  const [board, setBoard] = useState<PopBoard | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  // 실적 입력 중인 작업지시 + 입력값.
  const [reporting, setReporting] = useState<{ id: number; producedQty: number; defectQty: number } | null>(null)

  const load = useCallback(() => {
    if (!selected) return
    api.pop
      .board(selected)
      .then((b) => {
        setBoard(b)
        setError(null)
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'POP 데이터를 불러오지 못했습니다.'))
  }, [selected])

  useEffect(() => {
    load()
  }, [load])

  async function run(action: () => Promise<unknown>, id: number) {
    setBusyId(id)
    setError(null)
    try {
      await action()
      setReporting(null)
      load()
    } catch (err) {
      setError(err instanceof Error ? err.message : '조작에 실패했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  // 단말 미선택 — 역할 진입(OPERATOR)에서 키오스크 URL 없이 들어온 경우.
  if (!selected) {
    return (
      <div className="pop">
        <div className="pop-head">
          <h1>POP 단말 선택</h1>
          {onLogout && <button className="ghost" onClick={onLogout}>로그아웃</button>}
        </div>
        {terminals.length === 0 ? (
          <p className="muted">등록된 단말이 없습니다.</p>
        ) : (
          <div className="pop-terminal-pick">
            {terminals.map((t) => (
              <button key={t.terminalCode} className="pop-big" onClick={() => setSelected(t.terminalCode)}>
                {t.name}
                <span className="mono small">{t.terminalCode}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="pop">
      <div className="pop-head">
        <h1>
          {board?.terminal.name ?? selected} <span className="mono small">{selected}</span>
        </h1>
        <div className="pop-head-actions">
          {!terminalCode && terminals.length > 1 && (
            <button className="ghost" onClick={() => setSelected(null)}>단말 변경</button>
          )}
          <button className="ghost" onClick={load}>새로고침</button>
          {onLogout && <button className="ghost" onClick={onLogout}>로그아웃</button>}
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {board && board.workOrders.length === 0 && <p className="muted">배정된 작업지시가 없습니다.</p>}

      <div className="pop-wo-list">
        {board?.workOrders.map((wo) => (
          <PopWorkOrderCard
            key={wo.id}
            wo={wo}
            busy={busyId === wo.id}
            reporting={reporting?.id === wo.id ? reporting : null}
            onStart={() => run(() => api.pop.start(selected, wo.id), wo.id)}
            onBeginReport={() => setReporting({ id: wo.id, producedQty: wo.plannedQty, defectQty: 0 })}
            onChangeReport={(patch) => setReporting((r) => (r ? { ...r, ...patch } : r))}
            onSubmitReport={() =>
              reporting &&
              run(
                () =>
                  api.pop.completeProduction(selected, wo.id, {
                    producedQty: reporting.producedQty,
                    defectQty: reporting.defectQty,
                  }),
                wo.id,
              )
            }
            onCancelReport={() => setReporting(null)}
            onClose={() => run(() => api.pop.close(selected, wo.id), wo.id)}
          />
        ))}
      </div>
    </div>
  )
}

function progress(wo: WorkOrder): number {
  if (wo.plannedQty <= 0) return 0
  return Math.min(100, Math.round((wo.producedQty / wo.plannedQty) * 100))
}

function PopWorkOrderCard({
  wo,
  busy,
  reporting,
  onStart,
  onBeginReport,
  onChangeReport,
  onSubmitReport,
  onCancelReport,
  onClose,
}: {
  wo: WorkOrder
  busy: boolean
  reporting: { id: number; producedQty: number; defectQty: number } | null
  onStart: () => void
  onBeginReport: () => void
  onChangeReport: (patch: Partial<{ producedQty: number; defectQty: number }>) => void
  onSubmitReport: () => void
  onCancelReport: () => void
  onClose: () => void
}) {
  const startable = wo.status === 'READY' || wo.status === 'ASSIGNED'
  const reportable = wo.status === 'IN_PROGRESS'
  const closable = wo.status === 'INSPECTION_WAITING' || wo.status === 'ON_HOLD'

  return (
    <div className="pop-wo">
      <div className="pop-wo-top">
        <span className="mono pop-wo-no">{wo.workOrderNo}</span>
        <span className={`badge wo-${wo.status}`}>{wo.status}</span>
      </div>
      {/* 작업자가 제일 먼저 봐야 할 것 — 무엇을 만드는가. */}
      {wo.partCode && (
        <div className="pop-wo-part">
          <span className="mono">{wo.partCode}</span> {wo.partName}
          {wo.modelCode && <span className="badge model-badge">{wo.modelCode}</span>}
        </div>
      )}
      <div className="muted small">LOT {wo.lotNo}</div>
      <div className="wo-bar">
        <div className="wo-fill" style={{ width: `${progress(wo)}%` }} />
        <span className="wo-text">
          {wo.producedQty} / {wo.plannedQty}
          {wo.defectQty > 0 && <span className="defect"> · 불량 {wo.defectQty}</span>}
        </span>
      </div>

      {reporting ? (
        <div className="pop-report">
          <label>
            생산 수량
            <input
              type="number"
              min={0}
              value={reporting.producedQty}
              onChange={(e) => onChangeReport({ producedQty: Number(e.target.value) })}
            />
          </label>
          <label>
            불량 수량
            <input
              type="number"
              min={0}
              value={reporting.defectQty}
              onChange={(e) => onChangeReport({ defectQty: Number(e.target.value) })}
            />
          </label>
          <div className="pop-actions">
            <button className="pop-big primary" disabled={busy} onClick={onSubmitReport}>
              {busy ? '처리 중…' : '실적 확정'}
            </button>
            <button className="pop-big" disabled={busy} onClick={onCancelReport}>
              취소
            </button>
          </div>
        </div>
      ) : (
        <div className="pop-actions">
          {startable && (
            <button className="pop-big primary" disabled={busy} onClick={onStart}>
              {busy ? '착수 중…' : '착수'}
            </button>
          )}
          {reportable && (
            <button className="pop-big primary" disabled={busy} onClick={onBeginReport}>
              실적 입력
            </button>
          )}
          {closable && (
            <button className="pop-big" disabled={busy} onClick={onClose}>
              {busy ? '종료 중…' : '종료'}
            </button>
          )}
        </div>
      )}
    </div>
  )
}
