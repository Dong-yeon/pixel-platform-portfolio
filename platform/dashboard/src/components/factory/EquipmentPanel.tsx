import type { Equipment, EquipmentStatus, ProductionLine } from '../../types'

const STATUS_LABEL: Record<EquipmentStatus, string> = {
  RUNNING: '가동',
  IDLE: '대기',
  DOWN: '고장',
  QUALITY_HOLD: '품질보류',
}

export function EquipmentPanel({
  equipments,
  lines,
}: {
  equipments: Equipment[]
  lines: ProductionLine[]
}) {
  if (equipments.length === 0) {
    return <p className="muted small">설비가 없습니다.</p>
  }
  const lineName = new Map(lines.map((l) => [l.id, l.name]))

  return (
    <div className="equip-grid">
      {equipments.map((e) => (
        <div key={e.equipmentCode} className={`equip-card eq-${e.status}`}>
          <div className="equip-head">
            <strong>{e.equipmentCode}</strong>
            <span className={`badge eq-badge-${e.status}`}>{STATUS_LABEL[e.status]}</span>
          </div>
          <div className="equip-name">{e.name}</div>
          <div className="muted small">
            {lineName.get(e.lineId) ?? `라인 #${e.lineId}`} · 목표 C/T {(e.idealCycleTimeMs / 1000).toFixed(0)}초
          </div>
        </div>
      ))}
    </div>
  )
}
