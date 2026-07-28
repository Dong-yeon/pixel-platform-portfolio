import type { Robot, Task, TimelineEvent } from '../../types'
import { EventTimeline } from '../EventTimeline'
import { FactoryMap } from './FactoryMap'
import { RobotPanel } from './RobotPanel'
import { TaskPanel } from './TaskPanel'

/** pixel-fleet — AMR 군집 관제 뷰. */
export function FleetView({
  robots,
  tasks,
  events,
  onTaskChanged,
}: {
  robots: Robot[]
  tasks: Task[]
  events: TimelineEvent[]
  onTaskChanged: () => void
}) {
  return (
    <div className="grid">
      <section className="card map-card">
        <h2>공장 지도</h2>
        <FactoryMap robots={robots} />
        <RobotPanel robots={robots} />
      </section>

      <section className="card">
        <h2>운송 작업</h2>
        <TaskPanel tasks={tasks} robots={robots} onChanged={onTaskChanged} />
      </section>

      <section className="card">
        <h2>이벤트 타임라인</h2>
        <EventTimeline events={events} />
      </section>
    </div>
  )
}
