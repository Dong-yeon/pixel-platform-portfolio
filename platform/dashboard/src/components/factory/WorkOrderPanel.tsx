import { useState } from 'react'
import { api } from '../../api'
import type { Equipment, WorkOrder } from '../../types'

/** 계획 대비 생산 진척률(%). */
function progress(wo: WorkOrder): number {
  if (wo.plannedQty <= 0) return 0
  return Math.min(100, Math.round((wo.producedQty / wo.plannedQty) * 100))
}

export function WorkOrderPanel({
  workOrders,
  equipments,
  onChanged,
}: {
  workOrders: WorkOrder[]
  equipments: Equipment[]
  onChanged: () => void
}) {
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const equipCode = new Map(equipments.map((e) => [e.id, e.equipmentCode]))

  async function start(wo: WorkOrder) {
    setBusyId(wo.id)
    setError(null)
    try {
      await api.factory.startWorkOrder(wo.id)
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : '작업지시 시작 실패')
    } finally {
      setBusyId(null)
    }
  }

  if (workOrders.length === 0) {
    return <p className="muted small">작업지시가 없습니다.</p>
  }

  return (
    <div>
      {error && <div className="error">{error}</div>}
      <div className="wo-list">
        {workOrders.map((wo) => (
          <div key={wo.id} className="wo-row">
            <div className="wo-top">
              <span className="mono">{wo.workOrderNo}</span>
              <span className={`badge wo-${wo.status}`}>{wo.status}</span>
              {/* 서버가 상태 전이를 검증하므로, 여기서는 명백히 시작 가능한 상태만 노출한다. */}
              {(wo.status === 'READY' || wo.status === 'ASSIGNED') && (
                <button className="mini" disabled={busyId === wo.id} onClick={() => start(wo)}>
                  {busyId === wo.id ? '시작 중…' : '시작'}
                </button>
              )}
            </div>
            <div className="muted small">
              {/* 무엇을 만드는지 — 기준정보가 생기기 전엔 지시번호만 보였다. */}
              {wo.partCode && (
                <>
                  <span className="mono">{wo.partCode}</span> {wo.partName}
                  {wo.modelCode && <span className="badge model-badge">{wo.modelCode}</span>}
                  {' · '}
                </>
              )}
              {equipCode.get(wo.equipmentId) ?? `설비 #${wo.equipmentId}`} · LOT {wo.lotNo}
            </div>
            <div className="wo-bar">
              <div className="wo-fill" style={{ width: `${progress(wo)}%` }} />
              <span className="wo-text">
                {wo.producedQty} / {wo.plannedQty}
                {wo.defectQty > 0 && <span className="defect"> · 불량 {wo.defectQty}</span>}
              </span>
            </div>
            {wo.holdReason && <div className="small warn">보류: {wo.holdReason}</div>}
          </div>
        ))}
      </div>
    </div>
  )
}
