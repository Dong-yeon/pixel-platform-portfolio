import type { Equipment, Robot, Task, TimelineEvent } from '../../types'
import { EventTimeline } from '../EventTimeline'
import { UnifiedMap } from '../UnifiedMap'
import { RobotPanel } from './RobotPanel'
import { TaskPanel } from './TaskPanel'

/**
 * pixel-fleet — AMR 군집 관제 뷰.
 *
 * <p>지도는 통합 현황과 <b>같은 컴포넌트</b>를 쓴다. 예전에는 fleet 전용 지도를 따로 두어
 * 설비도 경로도 안 보였는데, 같은 공장을 보는 화면이 서로 다르게 그려질 이유가 없다.
 */
export function FleetView({
  robots,
  tasks,
  equipments,
  events,
  onTaskChanged,
}: {
  robots: Robot[]
  tasks: Task[]
  equipments: Equipment[]
  events: TimelineEvent[]
  onTaskChanged: () => void
}) {
  return (
    <div className="grid">
      <section className="card map-card">
        <h2>공장 지도</h2>
        <UnifiedMap equipments={equipments} robots={robots} tasks={tasks} />
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
