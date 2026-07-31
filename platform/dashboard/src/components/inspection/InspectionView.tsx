import type { Equipment, WorkOrder } from '../../types'

/**
 * 검사 대기 목록 — INSPECTOR 진입 화면.
 *
 * <p><b>P12 스텁이다.</b> 지금은 생산 완료돼 검사를 기다리는(INSPECTION_WAITING) 작업지시를
 * 전부 보여준다. "내 검사 건만" 필터(검사원 배정)는 검사 배정 개념이 생기는 P15에서 채운다.
 */
export function InspectionView({
  workOrders,
  equipments,
}: {
  workOrders: WorkOrder[]
  equipments: Equipment[]
}) {
  const waiting = workOrders.filter((w) => w.status === 'INSPECTION_WAITING')
  const equipCode = new Map(equipments.map((e) => [e.id, e.equipmentCode]))

  return (
    <div className="grid">
      <section className="card">
        <div className="module-head">
          <h2>검사 대기</h2>
          <span className="muted small">{waiting.length}건 · 내 검사 건 필터는 P15</span>
        </div>
        {waiting.length === 0 ? (
          <p className="muted small">검사 대기 중인 작업지시가 없습니다.</p>
        ) : (
          <div className="wo-list">
            {waiting.map((wo) => (
              <div key={wo.id} className="wo-row">
                <div className="wo-top">
                  <span className="mono">{wo.workOrderNo}</span>
                  <span className={`badge wo-${wo.status}`}>{wo.status}</span>
                </div>
                <div className="muted small">
                  {equipCode.get(wo.equipmentId) ?? `설비 #${wo.equipmentId}`} · LOT {wo.lotNo}
                </div>
                <div className="muted small">
                  생산 {wo.producedQty} · 불량 {wo.defectQty}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
