import { useState } from 'react'
import type { Equipment, Layout, Robot, Task, TimelineEvent } from '../../types'
import { useRackStock } from '../../useRackStock'
import { EventTimeline } from '../EventTimeline'
import { MapControls } from '../MapControls'
import { ALL_VIEW, UnifiedMap, type MapLayers, type MapView } from '../UnifiedMap'
import { RobotPanel } from './RobotPanel'
import { TaskPanel } from './TaskPanel'

/**
 * 관제 화면의 지도는 <b>물류 레이어만</b> 켠다.
 *
 * <p>설비는 켜 둔다 — AMR이 어디를 피해 다니는지, 하역 지점이 어느 설비 앞인지가 배차의
 * 맥락이다. 반면 품질 심의(사무실·정보 흐름)와 POP 작업자 배지는 배차와 무관해서,
 * 켜 두면 이미 빠듯한 지도 밀도만 늘린다. 통합 현황에서는 토글로 다시 볼 수 있다.
 */
const FLEET_LAYERS: MapLayers = {
  equipment: true,
  amr: true,
  routes: true,
  pop: false,
  quality: false,
}

/**
 * pixel-fleet — AMR 군집 관제 뷰.
 *
 * <p>지도는 통합 현황과 <b>같은 컴포넌트</b>를 쓴다. 예전에는 fleet 전용 지도를 따로 두어
 * 설비도 경로도 안 보였는데, 같은 공장을 보는 화면이 서로 다르게 그려질 이유가 없다.
 * 다만 <b>무엇을 켜 두는지</b>는 화면의 목적에 따라 다르다({@link FLEET_LAYERS}).
 */
export function FleetView({
  layout,
  robots,
  tasks,
  equipments,
  events,
  onTaskChanged,
}: {
  layout: Layout | null
  robots: Robot[]
  tasks: Task[]
  equipments: Equipment[]
  events: TimelineEvent[]
  onTaskChanged: () => void
}) {
  const [view, setView] = useState<MapView>(ALL_VIEW)
  // 창고 렉의 적재율 — 배차 담당도 "어디가 찼는지"는 봐야 한다.
  const rackStock = useRackStock()

  return (
    <div className="grid">
      <section className="card map-card">
        <h2>공장 지도</h2>
        <UnifiedMap
          layout={layout}
          equipments={equipments}
          robots={robots}
          tasks={tasks}
          rackStock={rackStock}
          view={view}
          layers={FLEET_LAYERS}
        />
        <MapControls layout={layout} view={view} onViewChange={setView} />
        <RobotPanel robots={robots} />
      </section>

      <section className="card">
        <h2>운송 작업</h2>
        <TaskPanel layout={layout} tasks={tasks} robots={robots} onChanged={onTaskChanged} />
      </section>

      <section className="card">
        <h2>이벤트 타임라인</h2>
        <EventTimeline events={events} />
      </section>
    </div>
  )
}
