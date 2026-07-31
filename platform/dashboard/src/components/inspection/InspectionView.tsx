import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api'
import type { Inspection, MrbDecision, MrbReview, Nonconformance } from '../../types'

const DECISION_LABEL: Record<MrbDecision, string> = {
  USE_AS_IS: '특채',
  REWORK: '재작업',
  SCRAP: '폐기',
  RETURN: '반품',
}

/**
 * 검사 대기 + MRB 심의 — INSPECTOR의 진입 화면. QMS 모듈(:9004)의 실데이터를 쓴다.
 *
 * 흐름: 검사 대기 → 판정(불합격이면 NCR 자동) → NCR을 심의에 올림(설비 홀드) → 판정(홀드 해제).
 */
export function InspectionView() {
  const [pending, setPending] = useState<Inspection[]>([])
  const [ncrs, setNcrs] = useState<Nonconformance[]>([])
  const [reviews, setReviews] = useState<MrbReview[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  const load = useCallback(() => {
    api.qms.pendingInspections().then(setPending).catch(() => {})
    api.qms.nonconformances().then(setNcrs).catch(() => {})
    api.qms.mrbList().then(setReviews).catch(() => {})
  }, [])

  useEffect(() => {
    load()
    // 검사는 factory 신호로 뒤에서 생긴다 — 주기 갱신으로 새 건을 끌어온다.
    const timer = window.setInterval(load, 15_000)
    return () => window.clearInterval(timer)
  }, [load])

  async function run(key: string, action: () => Promise<unknown>) {
    setBusy(key)
    setError(null)
    try {
      await action()
      load()
    } catch (err) {
      setError(err instanceof Error ? err.message : '처리에 실패했습니다.')
    } finally {
      setBusy(null)
    }
  }

  // 이미 심의가 열린 NCR은 다시 올릴 수 없다.
  const reviewedNcrIds = new Set(reviews.map((r) => r.nonconformanceId))
  const openReviews = reviews.filter((r) => r.status !== 'CLOSED')

  return (
    <div className="grid">
      <section className="card">
        <div className="module-head">
          <h2>검사 대기</h2>
          <span className="muted small">{pending.length}건 · QMS 자동 생성(불량 임계 초과)</span>
        </div>
        {error && <div className="error">{error}</div>}
        {pending.length === 0 ? (
          <p className="muted small">검사 대기 중인 건이 없습니다.</p>
        ) : (
          <div className="wo-list">
            {pending.map((ins) => (
              <div key={ins.id} className="wo-row">
                <div className="wo-top">
                  <span className="mono">{ins.inspectionNo}</span>
                  <span className="badge wo-INSPECTION_WAITING">{ins.inspectionType}</span>
                </div>
                <div className="muted small">
                  {ins.equipmentCode ?? '-'} · {ins.workOrderNo ?? '-'} · LOT {ins.lotNo ?? '-'} · 불량 {ins.defectQty}
                </div>
                <div className="pop-actions">
                  <button
                    className="mini"
                    disabled={busy === `pass-${ins.id}`}
                    onClick={() => run(`pass-${ins.id}`, () =>
                      api.qms.completeInspection(ins.id, { result: 'PASSED' }))}
                  >
                    합격
                  </button>
                  <button
                    className="mini danger"
                    disabled={busy === `fail-${ins.id}`}
                    onClick={() => run(`fail-${ins.id}`, () =>
                      api.qms.completeInspection(ins.id, { result: 'FAILED', defectCode: 'DIM' }))}
                  >
                    불합격 (NCR)
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="card">
        <div className="module-head">
          <h2>부적합 (NCR)</h2>
          <span className="muted small">{ncrs.length}건</span>
        </div>
        {ncrs.length === 0 ? (
          <p className="muted small">등록된 부적합이 없습니다.</p>
        ) : (
          <div className="wo-list">
            {ncrs.map((ncr) => (
              <div key={ncr.id} className="wo-row">
                <div className="wo-top">
                  <span className="mono">{ncr.ncrNo}</span>
                  {!reviewedNcrIds.has(ncr.id) && (
                    <button
                      className="mini"
                      disabled={busy === `mrb-${ncr.id}`}
                      onClick={() => run(`mrb-${ncr.id}`, () => api.qms.raiseMrb(ncr.id))}
                    >
                      {busy === `mrb-${ncr.id}` ? '개시 중…' : 'MRB 심의 개시'}
                    </button>
                  )}
                </div>
                <div className="muted small">
                  {ncr.equipmentCode ?? '-'} · {ncr.workOrderNo ?? '-'} · 불량 {ncr.defectQty}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="card">
        <div className="module-head">
          <h2>MRB 심의</h2>
          <span className="muted small">진행 {openReviews.length} / 전체 {reviews.length}</span>
        </div>
        {reviews.length === 0 ? (
          <p className="muted small">심의 이력이 없습니다.</p>
        ) : (
          <div className="wo-list">
            {reviews.map((mrb) => (
              <div key={mrb.id} className="wo-row">
                <div className="wo-top">
                  <span className="mono">{mrb.mrbNo}</span>
                  <span className={`badge mrb-${mrb.status}`}>{mrb.status}</span>
                  {mrb.holdApplied && <span className="badge eq-badge-QUALITY_HOLD">설비 홀드 중</span>}
                </div>
                <div className="muted small">
                  {mrb.equipmentCode ?? '-'} · {mrb.workOrderNo ?? '-'}
                  {mrb.decision && ` · 판정 ${DECISION_LABEL[mrb.decision]}`}
                </div>
                <div className="pop-actions">
                  {mrb.status === 'RAISED' && (
                    <button className="mini" disabled={busy === `rev-${mrb.id}`}
                      onClick={() => run(`rev-${mrb.id}`, () => api.qms.startMrbReview(mrb.id))}>
                      심의 시작
                    </button>
                  )}
                  {mrb.status === 'UNDER_REVIEW' && (
                    (['USE_AS_IS', 'REWORK', 'SCRAP', 'RETURN'] as MrbDecision[]).map((d) => (
                      <button key={d} className="mini" disabled={busy === `dec-${mrb.id}`}
                        onClick={() => run(`dec-${mrb.id}`, () => api.qms.decideMrb(mrb.id, d))}>
                        {DECISION_LABEL[d]}
                      </button>
                    ))
                  )}
                  {mrb.status === 'DECIDED' && (
                    <button className="mini" disabled={busy === `close-${mrb.id}`}
                      onClick={() => run(`close-${mrb.id}`, () => api.qms.closeMrb(mrb.id))}>
                      종결
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
