import type { Equipment, ProductionLine, TimelineEvent, WorkOrder } from '../../types'
import { EventTimeline } from '../EventTimeline'
import { EquipmentPanel } from './EquipmentPanel'
import { WorkOrderPanel } from './WorkOrderPanel'

/** pixel-factory — 가공라인 OEE 뷰. */
export function FactoryView({
  equipments,
  lines,
  workOrders,
  events,
  onWorkOrderChanged,
}: {
  equipments: Equipment[]
  lines: ProductionLine[]
  workOrders: WorkOrder[]
  events: TimelineEvent[]
  onWorkOrderChanged: () => void
}) {
  return (
    <div className="grid">
      <section className="card map-card">
        <h2>설비 현황</h2>
        <EquipmentPanel equipments={equipments} lines={lines} />
      </section>

      <section className="card">
        <h2>작업지시</h2>
        <WorkOrderPanel
          workOrders={workOrders}
          equipments={equipments}
          onChanged={onWorkOrderChanged}
        />
      </section>

      <section className="card">
        <h2>이벤트 타임라인</h2>
        <EventTimeline events={events} />
      </section>
    </div>
  )
}
