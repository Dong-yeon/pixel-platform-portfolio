import {
  nodeIndex, routePoints,
  type Equipment, type EquipmentStatus, type Layout, type LayoutBuilding, type LayoutRack,
  type MrbOpenSummary, type Robot, type RobotStatus, type Task, type TerminalPresence,
} from '../types'

/** 지도 레이어 on/off. 밀도가 빠듯해 겹치는 시스템을 끌 수 있게 한다(지도 시각 규칙). */
export interface MapLayers {
  equipment: boolean
  amr: boolean
  routes: boolean
  pop: boolean
  /** 품질 흐름 — 품질동 강조 + 부적합 정보 흐름 점선. */
  quality: boolean
  /** 랙 피더(P21) — 창고동 렉 전용 로봇. AMR과 별개로 껐다 켤 수 있다. */
  rackFeeder: boolean
}

const ALL_LAYERS: MapLayers = {
  equipment: true, amr: true, routes: true, pop: true, quality: true, rackFeeder: true,
}

/**
 * 보고 있는 건물/층.
 *
 * <p>`buildingCode`가 null이면 전체 보기다. 위층은 아래층과 <b>같은 자리를 쓰므로</b>
 * 렉은 `floorNo`가 맞는 것만 그린다 — 안 그러면 3개 층이 겹쳐 뭉갠다.
 */
export interface MapView {
  buildingCode: string | null
  floorNo: number
}

export const ALL_VIEW: MapView = { buildingCode: null, floorNo: 1 }

// 마지막 조작 후 이만큼 지나면 배지를 흐리게 — "곧 사라짐" 신호(서버는 타임아웃에 목록에서 뺀다).
const STALE_FADE_MINUTES = 15

/** 통로가 벽을 지나는 자리는 출입구다 — 그만큼 벽을 끊어 그린다. */
const DOOR_HALF_HEIGHT = 1.1

const ROBOT_COLOR: Record<RobotStatus, string> = {
  IDLE: '#27ae60',
  MOVING: '#2d7ff9',
  CHARGING: '#e08a00',
  ERROR: '#e0392b',
  OFFLINE: '#8a8a8a',
}

// 서버 EquipmentStatus 전부를 다뤄야 한다 — Record<EquipmentStatus, …>라 값이 빠지면
// 타입체크가 잡아 준다(새 상태가 지도에서 조용히 무색이 되는 일을 막는다).
const EQUIP_COLOR: Record<EquipmentStatus, string> = {
  RUNNING: '#27ae60',
  IDLE: '#9aa5b4',
  SETUP: '#2d7ff9',        // 준비·교체 — 비계획 정지지만 고장과는 구분
  DOWN: '#e0392b',
  QUALITY_HOLD: '#e08a00',
  PLANNED_STOP: '#5c6470', // 계획정지 — 애초에 돌릴 계획이 없던 시간(A의 분모에서 빠진다)
}

/** 진행 중인 운송만 흐름선으로 그린다(대기/완료는 제외). */
const ACTIVE_TASK = new Set(['ASSIGNED', 'IN_PROGRESS'])

/**
 * 로봇별 경로 색.
 *
 * <p>전부 같은 파랑이면 선이 겹칠 때 어느 로봇 것인지 못 읽는다. 로봇 코드에서 번호를 뽑아
 * 고정 색을 준다 — 매번 같은 로봇이 같은 색이어야 화면을 보며 눈이 따라갈 수 있다.
 */
// 팔레트 크기는 로봇 대수 이상으로 유지한다 — 나머지 연산으로 접히면 두 로봇이 같은 색이 된다.
const ROUTE_PALETTE = ['#2d7ff9', '#8e44ad', '#e08a00', '#0f9b8e', '#c0392b', '#3b5bdb', '#5f8b1e', '#b3486e']

function routeColorFor(robotCode: string | undefined): string {
  if (!robotCode) return '#9aa5b4' // 아직 배차 안 된 작업
  const digits = robotCode.replace(/\D/g, '')
  const index = digits ? Number(digits) : robotCode.length
  return ROUTE_PALETTE[index % ROUTE_PALETTE.length]
}

/** 적재율 색 — 빈 곳/여유/적정/포화가 한눈에 갈리게. */
function rackFill(ratio: number): string {
  if (ratio <= 0) return '#eef1f6'
  if (ratio < 0.4) return '#cfe6d5'
  if (ratio < 0.8) return '#7cc496'
  return '#2f8f5b'
}

/**
 * 벽을 문 자리에서 끊는다.
 *
 * @param doorYs 이 벽을 지나는 통로 y들
 * @returns 실제로 그릴 벽 구간 [y0,y1] 목록
 */
function wallSegments(top: number, bottom: number, doorYs: number[]): [number, number][] {
  const doors = doorYs
    .filter((y) => y > top && y < bottom)
    .sort((a, b) => a - b)
  const segments: [number, number][] = []
  let cursor = top
  for (const doorY of doors) {
    const gapTop = doorY - DOOR_HALF_HEIGHT
    const gapBottom = doorY + DOOR_HALF_HEIGHT
    if (gapTop > cursor) segments.push([cursor, gapTop])
    cursor = gapBottom
  }
  if (cursor < bottom) segments.push([cursor, bottom])
  return segments
}

/**
 * 통합 공장 평면도 — 건물 3채를 하나의 좌표계 위에 그린다.
 *
 *   창고동  렉(적재율) · 도크 · 피킹존 · 출하장       ← 3층, 층 선택으로 본다
 *   생산동  설비(상태별 색) · 하역 지점 · POP 단말
 *   품질동  검사 입고/판정 출고 · MRB 대기 배지
 *
 * <p>물류 흐름은 창고동 → 생산동 → <b>품질동(전수 검사)</b> → 합격은 창고동 / 불합격은 생산동이다.
 * "한 공장을 네 시스템이 관제한다"는 플랫폼의 요지가 이 한 화면에 드러난다.
 */
export function UnifiedMap({
  layout,
  equipments,
  robots,
  tasks,
  presence = [],
  mrbOpen = null,
  rackStock = {},
  view = ALL_VIEW,
  layers = ALL_LAYERS,
}: {
  /** 서버가 내려준 평면도. 아직 못 받았으면 그릴 좌표계가 없으므로 안내만 띄운다. */
  layout: Layout | null
  equipments: Equipment[]
  robots: Robot[]
  tasks: Task[]
  /** POP 파생 위치 — 사용 중 단말에 담당자 배지를 붙인다(작업자 독립 마커는 그리지 않는다). */
  presence?: TerminalPresence[]
  /** 열려 있는 MRB — 품질동 배지 + 현장→품질동 정보 흐름 점선의 근거. */
  mrbOpen?: MrbOpenSummary | null
  /** 렉 코드 → WMS 재고 수량. 용량(평면도)과 나눠 적재율을 낸다. */
  rackStock?: Record<string, number>
  view?: MapView
  layers?: MapLayers
}) {
  if (!layout) {
    return <p className="muted small">평면도를 불러오는 중…</p>
  }

  const activeTasks = tasks.filter((t) => ACTIVE_TASK.has(t.status))
  // 지금 랙 피더가 서비스 중인 렉(P21) — 진행 중인 작업의 출발지가 렉 코드인 것들.
  // 실제 진행 중인 주문에서만 뽑는다(없는 데이터를 시각효과로 지어내지 않는다, 지도 시각 규칙).
  const rackCodes = new Set(layout.racks.map((r) => r.rackCode))
  const activeRackCodes = new Set(activeTasks.map((t) => t.originNode).filter((n) => rackCodes.has(n)))
  const robotById = new Map(robots.map((r) => [r.id, r]))
  // 일을 맡은 로봇에만 경로 색 테를 두른다 — 쉬는 로봇까지 두르면 화면만 시끄럽다.
  const workingRobotIds = new Set(activeTasks.map((t) => t.assignedRobotId).filter(Boolean))
  const presenceByTerminal = new Map(presence.map((p) => [p.terminalCode, p]))
  const equipByCode = new Map(equipments.map((e) => [e.equipmentCode, e]))
  const NODES = nodeIndex(layout)
  const { width, height } = layout
  const aisles = [layout.upperAisleY, layout.lowerAisleY]

  const selected = view.buildingCode
    ? layout.buildings.find((b) => b.buildingCode === view.buildingCode) ?? null
    : null

  // 위층을 보고 있으면 지상에만 있는 것(설비·통로·POP 단말·품질 흐름)은 그리지 않는다.
  const showGround = view.floorNo === 1
  // AMR과 운송 경로는 층마다 따로 있다 — 로봇은 층을 오가지 못하고(엘리베이터는 화물용),
  // 위층 노드는 아래층과 좌표가 겹치므로 걸러 내지 않으면 3개 층이 한 자리에 뭉친다.
  const floorRobots = robots.filter((r) => r.floorNo === view.floorNo)
  const floorTasks = activeTasks.filter((t) => t.floorNo === view.floorNo)
  const qcBuilding = layout.buildings.find((b) => b.buildingCode === 'QC') ?? null

  // 건물을 고르면 그 외곽으로 확대한다. 여백을 둬 벽이 잘리지 않게.
  const pad = 1.5
  const viewWidth = selected ? selected.width + pad * 2 : width
  const viewBox = selected
    ? `${selected.posX - pad} ${selected.posY - pad} ${viewWidth} ${selected.height + pad * 2}`
    : `0 0 ${width} ${height}`

  /**
   * 글자 크기 보정.
   *
   * <p>폰트 크기도 좌표 단위라, 확대하면 도형과 함께 3배로 커져 화면을 가린다. 확대 배율의
   * 역수를 곱해 <b>글자는 늘 같은 크기로</b> 보이게 한다 — 확대의 이득은 도형이 커지는 것이지
   * 글자가 커지는 게 아니다.
   */
  const k = viewWidth / width
  const fs = (base: number) => ({ fontSize: base * k })

  const openMrbCount = mrbOpen?.count ?? 0
  // 정보 흐름: 열려 있는 심의의 설비 → 품질동. 운송 경로와 다른 색 점선(지도 시각 규칙).
  const qualityFlows = layers.quality && mrbOpen && showGround
    ? mrbOpen.reviews
        .map((r) => (r.equipmentCode ? equipByCode.get(r.equipmentCode) : undefined))
        .filter((e): e is Equipment => !!e && e.posX != null && e.posY != null)
    : []

  return (
    <svg className="umap" viewBox={viewBox} preserveAspectRatio="xMidYMid meet">
      <rect x={0} y={0} width={width} height={height} className="umap-bg" />

      {/* ---- 건물 ---- */}
      {layout.buildings.map((b) => (
        <BuildingShape
          key={b.buildingCode}
          building={b}
          aisles={aisles}
          textScale={k}
          dim={selected !== null && selected.buildingCode !== b.buildingCode}
          floorLabel={b.buildingCode === view.buildingCode
            ? b.floors.find((f) => f.floorNo === view.floorNo)?.name
            : undefined}
        />
      ))}

      {/* ---- 통로 ---- 건물을 관통한다. 벽과 만나는 자리가 출입구다. */}
      {showGround && aisles.map((y) => (
        <line key={`aisle-${y}`} x1={2} y1={y} x2={width - 2} y2={y} className="umap-aisle" />
      ))}

      {/* ---- 렉 ---- 보고 있는 층의 것만(위층은 같은 자리를 쓴다) */}
      {layout.racks
        .filter((rack) => rack.floorNo === view.floorNo)
        .map((rack) => (
          <RackShape
            key={rack.rackCode}
            rack={rack}
            quantity={rackStock[rack.rackCode] ?? 0}
            active={activeRackCodes.has(rack.rackCode)}
          />
        ))}

      {/* ---- AMR 이동 경로 ---- 설비/로봇보다 아래에 깔린다.
             **앞으로 갈 길만 그린다.** 로봇이 아직 짐을 싣지 않았으면 목적지로 직행하는 게
             아니라 픽업 지점을 먼저 들른다 — 그 구간을 빼먹으면 그려진 선이 실제 주행과
             어긋나 "지나간 경로"처럼 보인다. */}
      {layers.routes && floorTasks.map((t) => {
        const to = NODES[t.destinationNode]
        const origin = NODES[t.originNode]
        if (!to) return null

        const robot = t.assignedRobotId ? robotById.get(t.assignedRobotId) : undefined
        const color = routeColorFor(robot?.robotCode)

        // 실제 주행과 같은 통로 경유 경로로 그린다(직선으로 그리면 벽을 관통하는 것처럼 보인다).
        let points: [number, number][]
        let pickup: [number, number] | null = null

        if (robot && !robot.laden && origin) {
          // 아직 가지러 가는 중 — 로봇 → 픽업 → 도착. 두 다리를 이어 붙인다(이음매 중복 제거).
          const leg1 = routePoints(layout, [robot.posX, robot.posY], origin)
          const leg2 = routePoints(layout, origin, to)
          points = [...leg1, ...leg2.slice(1)]
          pickup = origin
        } else {
          const from: [number, number] | undefined = robot ? [robot.posX, robot.posY] : origin
          if (!from) return null
          points = routePoints(layout, from, to)
        }

        return (
          <g key={`route-${t.id}`}>
            <polyline
              points={points.map((p) => `${p[0]},${p[1]}`).join(' ')}
              className="umap-route"
              stroke={color}
            />
            {points.slice(1, -1).map((p, i) => (
              <circle key={`wp-${t.id}-${i}`} cx={p[0]} cy={p[1]} r={0.35}
                      className="umap-waypoint" stroke={color} />
            ))}
            {/* 픽업 지점 — 여기서 싣고 나서 도착지로 간다 */}
            {pickup && (
              <g className="umap-route-pickup">
                <circle cx={pickup[0]} cy={pickup[1]} r={1.0} stroke={color} />
                <text x={pickup[0]} y={pickup[1] - 1.5} textAnchor="middle"
                      className="umap-route-tag" style={fs(0.8)} fill={color}>
                  픽업
                </text>
              </g>
            )}
            <circle cx={to[0]} cy={to[1]} r={1.5} className="umap-route-target" stroke={color} />
          </g>
        )
      })}

      {/* ---- 충전존 ---- 충전 베이를 감싸는 구역. 렉을 비워 둔 자리라 로봇이 렉과 겹치지 않는다. */}
      {layout.chargingZones
        .filter((zone) => zone.floorNo === view.floorNo)
        .map((zone) => (
          <g key={zone.zoneCode} className="umap-charge-zone">
            <rect x={zone.posX} y={zone.posY} width={zone.width} height={zone.height} rx={0.4} />
            <text x={zone.posX + zone.width / 2} y={zone.posY + zone.height - 0.4}
                  textAnchor="middle" className="umap-charge-label" style={fs(0.8)}>
              충전존
            </text>
          </g>
        ))}

      {/* ---- 엘리베이터 ---- 층마다 같은 자리. **물건만** 오르내린다(AMR은 자기 층에 머문다). */}
      {layout.elevators.map((elevator) => (
        <g key={elevator.elevatorCode} className="umap-elevator">
          <rect x={elevator.posX - 1.3} y={elevator.posY - 1.9} width={2.6} height={3.8} rx={0.3} />
          <line x1={elevator.posX - 0.7} y1={elevator.posY} x2={elevator.posX + 0.7} y2={elevator.posY} />
          <text x={elevator.posX} y={elevator.posY - 0.5} textAnchor="middle"
                className="umap-elevator-mark" style={fs(1.0)}>
            ⇅
          </text>
          <text x={elevator.posX} y={elevator.posY + 1.35} textAnchor="middle"
                className="umap-elevator-label" style={fs(0.75)}>
            {elevator.servesFloors.join('·')}층
          </text>
        </g>
      ))}

      {/* ---- 하역 지점·도크·검사 기착지 ---- 보고 있는 층의 것만(위층은 같은 자리를 쓴다) */}
      {layout.nodes
        .filter((node) => node.floorNo === view.floorNo && node.nodeType !== 'ELEVATOR')
        .map((node) => (
          <g key={node.nodeCode}>
            <rect x={node.posX - 0.7} y={node.posY - 0.7} width={1.4} height={1.4}
                  className={`umap-node node-${node.nodeType}`} rx={0.25} />
            <text x={node.posX} y={node.posY + 2} className="umap-node-label"
                  textAnchor="middle" style={fs(0.85)}>
              {node.nodeCode}
            </text>
          </g>
        ))}

      {/* ---- 설비 ---- 좌표는 서버가 실어 보낸다(하드코딩 매핑 없음) */}
      {showGround && layers.equipment && equipments.map((e) => {
        if (e.posX == null || e.posY == null) return null
        const [x, y] = [e.posX, e.posY]
        return (
          <g key={e.equipmentCode}>
            <rect
              x={x - 1.9} y={y - 1.3} width={3.8} height={2.6} rx={0.4}
              fill={EQUIP_COLOR[e.status]}
              className={`umap-equip eq-${e.status}`}
            />
            <text x={x} y={y + 0.35} className="umap-equip-label" textAnchor="middle" style={fs(1.05)}>
              {e.equipmentCode}
            </text>
          </g>
        )
      })}

      {/* ---- POP 단말 ---- 세로 직사각(키오스크). 사용 중이면 담당자·WO 배지.
             작업자는 독립 마커로 그리지 않는다(지도 시각 규칙) — 단말에 붙는 배지로만. */}
      {showGround && layers.pop && layout.terminals.map((t) => {
        const here = presenceByTerminal.get(t.terminalCode)
        const stale = here ? minutesSince(here.lastActivityAt) >= STALE_FADE_MINUTES : false
        return (
          <g key={t.terminalCode} className="umap-terminal-g">
            <rect
              x={t.posX - 0.9} y={t.posY - 1.5} width={1.8} height={3.0} rx={0.3}
              className={`umap-terminal ${here ? 'in-use' : ''}`}
            />
            <text
              x={t.posX} y={t.posY + 0.3} textAnchor="middle"
              className={`umap-terminal-label ${here ? 'in-use' : ''}`}
              style={fs(1.0)}
            >
              {t.terminalCode.replace('POP-', '')}
            </text>
            {here && (
              <g className="umap-operator-badge" opacity={stale ? 0.45 : 1}>
                <rect x={t.posX - 3.4} y={t.posY + 1.7} width={6.8} height={2.2} rx={0.4} />
                <text x={t.posX} y={t.posY + 2.75} textAnchor="middle" className="umap-badge-name" style={fs(1.05)}>
                  {here.operatorName}
                </text>
                <text x={t.posX} y={t.posY + 3.55} textAnchor="middle" className="umap-badge-wo" style={fs(0.9)}>
                  {here.workOrderNo}
                </text>
              </g>
            )}
          </g>
        )
      })}

      {/* ---- 품질 정보 흐름 ---- 부적합 설비 → 품질동. 운송 경로와 다른 색 점선. */}
      {qcBuilding && qualityFlows.map((e) => {
        const targetX = qcBuilding.posX + qcBuilding.width / 2
        const targetY = qcBuilding.posY + 2.5
        return (
          <polyline
            key={`qflow-${e.equipmentCode}`}
            points={`${e.posX},${e.posY} ${e.posX},${qcBuilding.posY - 0.6} ${targetX},${qcBuilding.posY - 0.6} ${targetX},${targetY}`}
            className="umap-quality-flow"
          />
        )
      })}

      {/* ---- 품질동 MRB 대기 배지 ---- */}
      {qcBuilding && layers.quality && (
        <g>
          <text
            x={qcBuilding.posX + qcBuilding.width / 2}
            y={qcBuilding.posY + qcBuilding.height - 1.2}
            textAnchor="middle"
            className="umap-office-sub"
            style={fs(0.95)}
          >
            MRB 대기 {openMrbCount}건
          </text>
          {openMrbCount > 0 && (
            <circle
              cx={qcBuilding.posX + qcBuilding.width - 1.0}
              cy={qcBuilding.posY + 1.0}
              r={0.65}
              className="umap-office-badge"
            />
          )}
        </g>
      )}

      {/* ---- AMR·랙 피더 ---- 항상 맨 위. 보고 있는 층의 로봇만(층마다 따로 있다).
             종류별로 레이어를 따로 끌 수 있다 — 랙 피더는 AMR과 아예 다니는 곳이 다르다. */}
      {floorRobots
        .filter((r) => (r.robotType === 'RACK_FEEDER' ? layers.rackFeeder : layers.amr))
        .map((r) => (
        <g
          key={r.robotCode}
          className="umap-robot"
          style={{ transform: `translate(${r.posX}px, ${r.posY}px)` }}
        >
          {/* 적재 중이면 파렛트를 얹어 그린다 — "가지러 가는 중"과 "옮기는 중"의 구분이
              물류 화면에서 가장 먼저 읽혀야 하는 정보다. 로봇 뒤에 깔아 원을 가리지 않는다. */}
          {r.laden && <rect x={-1.15} y={-1.15} width={2.3} height={2.3} rx={0.2} className="umap-pallet" />}
          {/* 자기 경로와 같은 색 테 — 선이 겹쳐도 어느 로봇 것인지 눈으로 잇는다. */}
          {workingRobotIds.has(r.id) && (
            <circle r={1.45} fill="none" stroke={routeColorFor(r.robotCode)} strokeWidth={0.26} opacity={0.9} />
          )}
          {/* 랙 피더는 사각, AMR은 원 — 창고 안에서만 도는 다른 종류의 로봇임을 모양으로 구분한다. */}
          {r.robotType === 'RACK_FEEDER' ? (
            <rect x={-0.85} y={-0.85} width={1.7} height={1.7} rx={0.25}
                  fill={ROBOT_COLOR[r.status]} className="umap-robot-feeder-mark" />
          ) : (
            <circle r={0.95} fill={ROBOT_COLOR[r.status]} stroke="#fff" strokeWidth={0.18} />
          )}
          <text y={0.38} className="umap-robot-label" textAnchor="middle" style={fs(1.1)}>
            {r.robotCode.slice(-1)}
          </text>
          <text y={-1.55} className="umap-robot-batt" textAnchor="middle" style={fs(0.95)}>
            {r.batteryPercent}%
          </text>
        </g>
      ))}
    </svg>
  )
}

/** 건물 외곽 — 벽은 통로가 지나는 자리에서 끊어 출입구를 만든다. */
function BuildingShape({
  building,
  aisles,
  textScale,
  dim,
  floorLabel,
}: {
  building: LayoutBuilding
  aisles: number[]
  textScale: number
  dim: boolean
  floorLabel?: string
}) {
  const { posX: x, posY: y, width: w, height: h } = building
  const right = x + w
  const bottom = y + h
  const segments = wallSegments(y, bottom, aisles)

  return (
    <g className={`umap-building b-${building.buildingCode}`} opacity={dim ? 0.35 : 1}>
      <rect x={x} y={y} width={w} height={h} rx={0.6} className="umap-building-fill" />
      {/* 위·아래 벽은 통로가 지나지 않으므로 통짜로 */}
      <line x1={x} y1={y} x2={right} y2={y} className="umap-wall" />
      <line x1={x} y1={bottom} x2={right} y2={bottom} className="umap-wall" />
      {/* 좌·우 벽은 문 자리에서 끊는다 */}
      {segments.map(([a, b], i) => (
        <line key={`l-${i}`} x1={x} y1={a} x2={x} y2={b} className="umap-wall" />
      ))}
      {segments.map(([a, b], i) => (
        <line key={`r-${i}`} x1={right} y1={a} x2={right} y2={b} className="umap-wall" />
      ))}
      {/* 이름표는 윗벽에 걸친 명패로 둔다 — 건물 안에 넣으면 렉·설비에 가린다. */}
      <BuildingNameplate
        x={x + w / 2}
        y={y}
        scale={textScale}
        text={building.name + (floorLabel ? ` · ${floorLabel}` : '')}
      />
    </g>
  )
}

/** 윗벽에 걸치는 명패 — 글자 뒤에 판을 깔아 벽선과 겹쳐도 읽히게. */
function BuildingNameplate({ x, y, scale, text }: { x: number; y: number; scale: number; text: string }) {
  // SVG는 텍스트 폭을 미리 모르니 글자 수로 어림한다(한글은 폭이 거의 글자 크기와 같다).
  const fontSize = 1.15 * scale
  const plateWidth = text.length * fontSize * 0.95 + 1.0 * scale
  const plateHeight = 1.9 * scale
  return (
    <g className="umap-nameplate">
      <rect
        x={x - plateWidth / 2} y={y - plateHeight / 2}
        width={plateWidth} height={plateHeight} rx={0.35 * scale}
      />
      <text x={x} y={y + fontSize * 0.35} textAnchor="middle" className="umap-building-label" style={{ fontSize }}>
        {text}
      </text>
    </g>
  )
}

/** 렉 — 열×단이 보이도록 칸을 긋고, 적재율로 색을 채운다. */
function RackShape({
  rack, quantity, active = false,
}: {
  rack: LayoutRack
  quantity: number
  /** 지금 랙 피더가 이 렉에서 취출 중인가(P21) — 실제 진행 중인 주문 근거만(지도 시각 규칙). */
  active?: boolean
}) {
  const ratio = rack.capacityQty > 0 ? Math.min(1, quantity / rack.capacityQty) : 0
  const vertical = rack.orientation !== 'H'
  const w = vertical ? 1.6 : 4.6
  const h = vertical ? 4.4 : 1.6
  const x = rack.posX - w / 2
  const y = rack.posY - h / 2
  // 단(levels)만큼 가로줄을 그어 선반처럼 보이게 한다.
  const dividers = Array.from({ length: Math.max(0, rack.levelsCount - 1) }, (_, i) =>
    y + (h * (i + 1)) / rack.levelsCount)

  return (
    <g className={`umap-rack-g${active ? ' servicing' : ''}`}>
      <rect x={x} y={y} width={w} height={h} rx={0.2} fill={rackFill(ratio)} className="umap-rack" />
      {dividers.map((dy, i) => (
        <line key={i} x1={x} y1={dy} x2={x + w} y2={dy} className="umap-rack-divider" />
      ))}
      <title>
        {`${rack.rackCode} · ${quantity}/${rack.capacityQty} EA (${Math.round(ratio * 100)}%) · `
          + `${rack.columnsCount}열 ${rack.levelsCount}단${active ? ' · 랙 피더 취출 중' : ''}`}
      </title>
    </g>
  )
}

/** ISO 시각으로부터 지난 분. 배지 흐리기 판정에 쓴다. */
function minutesSince(iso: string): number {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return 0
  return (Date.now() - then) / 60000
}
