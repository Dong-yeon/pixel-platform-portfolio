import { useEffect, useRef, useState } from 'react'
import type { Pallet } from '../api'
import {
  agvRoutePoints, nodeIndex, prodZones, routePoints,
  type Equipment, type EquipmentStatus, type Layout, type LayoutBuilding, type LayoutRack,
  type MrbOpenSummary, type ProdZoneBox, type Robot, type RobotStatus, type Task, type TerminalPresence,
} from '../types'

/** P34 — 팬/줌 뷰박스. viewBox 문자열로 직렬화하기 전 상태다. */
interface ViewBox { x: number; y: number; w: number; h: number }

/** 지도 레이어 on/off. 밀도가 빠듯해 겹치는 시스템을 끌 수 있게 한다(지도 시각 규칙). */
export interface MapLayers {
  equipment: boolean
  amr: boolean
  routes: boolean
  pop: boolean
  /** 품질 흐름 — 품질동 강조 + 부적합 정보 흐름 점선. */
  quality: boolean
  /** AGV(P21/P22, 옛 이름: 랙 피더) — 창고동 1층 전용 로봇. AMR과 별개로 껐다 켤 수 있다. */
  agv: boolean
}

const ALL_LAYERS: MapLayers = {
  equipment: true, amr: true, routes: true, pop: true, quality: true, agv: true,
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

/**
 * 한 층의 렉 수가 이걸 넘으면 RackShape를 단순화한다(P32 — 창고동 1층 864기).
 * 기존 최대 밀도(2·3층 24기)보다 훨씬 높은 값이라 옛 층은 전혀 영향받지 않는다.
 */
const RACK_LOD_THRESHOLD = 150

// ---- P34: 팬/줌 ----
/** 휠 한 번에 배율이 이만큼 바뀐다(15%). */
const ZOOM_STEP = 1.15
/** 최대 축소 — 전체 보기(기본 viewBox)보다 더 못 나간다. */
const ZOOM_OUT_LIMIT = 1.0
/** 최대 확대 — 밀집 렉(VD, 폭 1.0) 1~2개가 화면에 꽉 차는 수준. */
const MIN_ZOOM_WIDTH = 2.5

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
 * 통합 공장 평면도 — 건물 4채(P33로 품질동이 생산동에 흡수돼 3채)를 하나의 좌표계 위에 그린다.
 *
 *   창고동  렉(적재율) · 도크 · 피킹존 · 출하장                    ← 3층, 층 선택으로 본다
 *   생산동  A(가공)·B(조립·검사)·L(물류)·Q(품질) 구역(P33) · POP 단말
 *
 * <p>물류 흐름은 창고동 → 생산동 A(가공) → B(조립·검사) → <b>Q(품질, 전수 검사)</b> →
 * 합격은 창고동 / 불합격은 생산동 재작업이다. P33 이전엔 생산동·품질동이 별개 건물이었는데,
 * "가공→조립→물류→검사"가 한 건물 안에서 도는 그림으로 합쳤다(구역은 nodeCode 접두어로
 * 계산 — {@link prodZones}). "한 공장을 네 시스템이 관제한다"는 플랫폼의 요지가 이 한
 * 화면에 드러난다.
 */
export function UnifiedMap({
  layout,
  equipments,
  robots,
  tasks,
  presence = [],
  mrbOpen = null,
  rackStock = {},
  rackPallets = {},
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
  /** 렉 코드 → 그 위의 파렛트 목록(P23). 적재율 %만으론 안 보이는 "몇 장·뭘 실었는지"를 툴팁에 보탠다. */
  rackPallets?: Record<string, Pallet[]>
  view?: MapView
  layers?: MapLayers
}) {
  if (!layout) {
    return <p className="muted small">평면도를 불러오는 중…</p>
  }

  const activeTasks = tasks.filter((t) => ACTIVE_TASK.has(t.status))
  // 지금 AGV가 서비스 중인 렉(P21) — 진행 중인 작업의 출발지가 렉 코드인 것들.
  // 실제 진행 중인 주문에서만 뽑는다(없는 데이터를 시각효과로 지어내지 않는다, 지도 시각 규칙).
  const rackCodes = new Set(layout.racks.map((r) => r.rackCode))
  const activeRackCodes = new Set(activeTasks.map((t) => t.originNode).filter((n) => rackCodes.has(n)))
  const robotById = new Map(robots.map((r) => [r.id, r]))
  // 일을 맡은 로봇에만 경로 색 테를 두른다 — 쉬는 로봇까지 두르면 화면만 시끄럽다.
  const workingRobotIds = new Set(activeTasks.map((t) => t.assignedRobotId).filter(Boolean))
  const presenceByTerminal = new Map(presence.map((p) => [p.terminalCode, p]))
  const equipByCode = new Map(equipments.map((e) => [e.equipmentCode, e]))
  const NODES = nodeIndex(layout)
  // P32 D10 — 렉도 경로 좌표 조회 대상에 넣는다. 예전엔 NODES(layout_nodes)에서만
  // 찾아서 목적지가 렉 코드(AGV 취출)면 좌표를 못 찾고 그 작업의 경로 전체가 조용히
  // 스킵됐다 — 로봇 마커만 있고 선이 없어 "공중에 뜬 것처럼" 보이던 원인.
  const RACKS: Record<string, [number, number]> = Object.fromEntries(
    layout.racks.map((r) => [r.rackCode, [r.posX, r.posY]]),
  )
  const resolvePoint = (code: string): [number, number] | undefined => NODES[code] ?? RACKS[code]
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
  // P33 — 품질동(QC)은 별도 건물이 아니라 생산동(PROD) 안의 구역이 됐다. qcBuilding이
  // 하던 일(MRB 배지·품질 흐름선 기준점)은 이제 계산된 Q 구역 바운딩박스가 대신한다.
  const prodBuilding = layout.buildings.find((b) => b.buildingCode === 'PROD') ?? null
  const prodZoneBoxes = prodBuilding ? prodZones(layout, prodBuilding) : []
  const qZone = prodZoneBoxes.find((z) => z.key === 'Q') ?? null

  // 보고 있는 층의 렉만(위층은 같은 자리를 쓴다). P32로 창고동 1층이 864기가 되면서
  // 이 목록이 커지면 RackShape를 단순화한다(성능, 아래 렌더링 참고).
  const floorRacks = layout.racks.filter((rack) => rack.floorNo === view.floorNo)
  const simplifiedRacks = floorRacks.length > RACK_LOD_THRESHOLD

  // 건물을 고르면 그 외곽으로 확대한다. 여백을 둬 벽이 잘리지 않게.
  const pad = 1.5
  const viewWidth = selected ? selected.width + pad * 2 : width
  const baseBox: ViewBox = selected
    ? { x: selected.posX - pad, y: selected.posY - pad, w: viewWidth, h: selected.height + pad * 2 }
    : { x: 0, y: 0, w: width, h: height }

  // ---- P34: 팬/줌 ----
  // null이면 수동 줌 안 한 상태 — baseBox(건물 선택에 따른 기본 확대)를 그대로 쓴다.
  // 건물 선택이 바뀌면 그 확대와 수동 줌이 같이 안 꼬이게 수동 줌을 리셋한다.
  const [zoomBox, setZoomBox] = useState<ViewBox | null>(null)
  const svgRef = useRef<SVGSVGElement>(null)
  const dragRef = useRef<{ startX: number; startY: number; box: ViewBox } | null>(null)
  useEffect(() => setZoomBox(null), [view.buildingCode])
  const box = zoomBox ?? baseBox
  const viewBox = `${box.x} ${box.y} ${box.w} ${box.h}`

  /** 화면 픽셀 좌표(clientX/Y) → 지금 viewBox 기준 SVG 좌표. */
  function toSvgPoint(clientX: number, clientY: number): { x: number; y: number } | null {
    const rect = svgRef.current?.getBoundingClientRect()
    if (!rect || rect.width === 0 || rect.height === 0) return null
    return {
      x: box.x + ((clientX - rect.left) / rect.width) * box.w,
      y: box.y + ((clientY - rect.top) / rect.height) * box.h,
    }
  }

  /**
   * 휠 줌 — <b>네이티브 리스너로 직접 붙인다</b>, JSX {@code onWheel}이 아니다.
   *
   * <p>React 17부터 wheel·touch 리스너는 성능을 위해 루트에 <b>passive</b>로 등록된다 —
   * JSX {@code onWheel} 안에서 {@code e.preventDefault()}를 불러도 조용히 무시된다(콘솔에
   * "Unable to preventDefault inside passive event listener invocation" 경고만 남는다).
   * 그 결과 우리 줌(viewBox 갱신)과 브라우저 기본 동작(페이지 스크롤·Ctrl+휠 페이지 확대)이
   * <b>동시에</b> 일어나 서로 겹쳐 마구잡이로 확대되는 것처럼 보였다 — 실사용 중 발견.
   * {@code addEventListener(..., {passive:false})}로 직접 등록해야 preventDefault가
   * 실제로 먹는다.
   */
  useEffect(() => {
    const svg = svgRef.current
    if (!svg) return
    const handleWheel = (e: WheelEvent) => {
      e.preventDefault()
      const cursor = toSvgPoint(e.clientX, e.clientY)
      if (!cursor) return
      const rawFactor = e.deltaY > 0 ? ZOOM_STEP : 1 / ZOOM_STEP
      const maxW = baseBox.w * ZOOM_OUT_LIMIT
      const newW = Math.min(maxW, Math.max(MIN_ZOOM_WIDTH, box.w * rawFactor))
      const factor = newW / box.w // 한계에 걸려 배율이 잘렸으면 그 실제 배율로 다시 계산
      const newH = box.h * factor
      setZoomBox({
        x: cursor.x - (cursor.x - box.x) * factor,
        y: cursor.y - (cursor.y - box.y) * factor,
        w: newW,
        h: newH,
      })
    }
    svg.addEventListener('wheel', handleWheel, { passive: false })
    return () => svg.removeEventListener('wheel', handleWheel)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [box.x, box.y, box.w, box.h, baseBox.w])

  function onPointerDown(e: React.MouseEvent<SVGSVGElement>) {
    if (e.button !== 0) return
    dragRef.current = { startX: e.clientX, startY: e.clientY, box }
  }

  function onPointerMove(e: React.MouseEvent<SVGSVGElement>) {
    const drag = dragRef.current
    const rect = svgRef.current?.getBoundingClientRect()
    if (!drag || !rect || rect.width === 0 || rect.height === 0) return
    const dx = ((e.clientX - drag.startX) / rect.width) * drag.box.w
    const dy = ((e.clientY - drag.startY) / rect.height) * drag.box.h
    setZoomBox({ x: drag.box.x - dx, y: drag.box.y - dy, w: drag.box.w, h: drag.box.h })
  }

  function endDrag() {
    dragRef.current = null
  }

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
    <svg
      ref={svgRef}
      className="umap"
      viewBox={viewBox}
      preserveAspectRatio="xMidYMid meet"
      onMouseDown={onPointerDown}
      onMouseMove={onPointerMove}
      onMouseUp={endDrag}
      onMouseLeave={endDrag}
      onDoubleClick={() => setZoomBox(null)}
    >
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

      {/* ---- 생산동 내부 구역(P33) ---- A(가공)/B(조립·검사)/L(물류)/Q(품질). 품질동(QC)이
             건물 통합으로 폐지되면서 필요해졌다 — 색·라벨로 예전 건물 구분을 대신한다. */}
      {showGround && prodZoneBoxes.map((z) => (
        <ZoneShape
          key={`zone-${z.key}`}
          zone={z}
          textScale={k}
          dim={selected !== null && selected.buildingCode !== 'PROD'}
        />
      ))}

      {/* ---- 통로 ---- 건물을 관통한다. 벽과 만나는 자리가 출입구다. */}
      {showGround && aisles.map((y) => (
        <line key={`aisle-${y}`} x1={2} y1={y} x2={width - 2} y2={y} className="umap-aisle" />
      ))}

      {/* ---- 렉 ---- 보고 있는 층의 것만(위층은 같은 자리를 쓴다). P32로 창고동 1층이
             864기가 되면서, 렉마다 칸 단위 격자(cols×levels)를 전부 그리면 SVG 노드가
             수천 개로 불어나 렌더링이 무거워진다 — 밀도가 이 문턱을 넘으면(RACK_LOD_THRESHOLD)
             칸 세부묘사·라벨을 생략한 단순 렉으로 낮춘다(RackShape의 simplified). 툴팁은
             그대로라 코드·재고는 hover로 여전히 보인다. */}
      {floorRacks.map((rack) => (
        <RackShape
          key={rack.rackCode}
          rack={rack}
          quantity={rackStock[rack.rackCode] ?? 0}
          pallets={rackPallets[rack.rackCode] ?? []}
          active={activeRackCodes.has(rack.rackCode)}
          simplified={simplifiedRacks}
        />
      ))}

      {/* ---- AMR 이동 경로 ---- 설비/로봇보다 아래에 깔린다.
             **앞으로 갈 길만 그린다.** 로봇이 아직 짐을 싣지 않았으면 목적지로 직행하는 게
             아니라 픽업 지점을 먼저 들른다 — 그 구간을 빼먹으면 그려진 선이 실제 주행과
             어긋나 "지나간 경로"처럼 보인다. */}
      {layers.routes && floorTasks.map((t) => {
        const to = resolvePoint(t.destinationNode)
        const origin = resolvePoint(t.originNode)
        if (!to) return null

        const robot = t.assignedRobotId ? robotById.get(t.assignedRobotId) : undefined
        const color = routeColorFor(robot?.robotCode)

        // 실제 주행과 같은 통로 경유 경로로 그린다(직선으로 그리면 벽을 관통하는 것처럼 보인다).
        let points: [number, number][]
        let pickup: [number, number] | null = null

        // P32 D10 — AGV는 LaneGraph의 연결로 스냅 로직(routePoints)을 안 탄다(P21 D2,
        // 존 안 로컬 이동) — 대신 agvRoutePoints가 fleet OrderService#agvWaypoints와
        // 같은 규칙(대각선 금지, 밴드 아이슬·좌측 스파인만 타고 이동)으로 그린다.
        const isAgv = robot?.robotType === 'AGV'
        const legPoints = isAgv ? agvRoutePoints : routePoints

        if (robot && !robot.laden && origin) {
          // 아직 가지러 가는 중 — 로봇 → 픽업 → 도착. 두 다리를 이어 붙인다(이음매 중복 제거).
          const leg1 = legPoints(layout, [robot.posX, robot.posY], origin)
          const leg2 = legPoints(layout, origin, to)
          points = [...leg1, ...leg2.slice(1)]
          pickup = origin
        } else {
          const from: [number, number] | undefined = robot ? [robot.posX, robot.posY] : origin
          if (!from) return null
          points = legPoints(layout, from, to)
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

      {/* ---- 하역 지점·도크·검사 기착지 ---- 보고 있는 층의 것만(위층은 같은 자리를 쓴다).
          JUNCTION은 LaneGraph 내부 분기점일 뿐 실제 기착지가 아니라 원래도 안 그렸고,
          GATE(P22, AMR↔AGV 경계)는 아래에서 전용 "문" 마커로 따로 그린다. */}
      {layout.nodes
        .filter((node) =>
          node.floorNo === view.floorNo && node.nodeType !== 'ELEVATOR'
          && node.nodeType !== 'JUNCTION' && node.nodeType !== 'GATE')
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

      {/* ---- 게이트(P22) ---- WH↔PROD 사이, AMR의 LaneGraph 경로가 끝나고 AGV의 로컬
          이동 구역이 시작되는 유일한 물리적 경계. 문(door) 모양으로 눈에 띄게 그린다. */}
      {layout.nodes
        .filter((node) => node.floorNo === view.floorNo && node.nodeType === 'GATE')
        .map((node) => (
          <g key={node.nodeCode} className="umap-gate-mark">
            <rect x={node.posX - 0.35} y={node.posY - 1.6} width={0.7} height={3.2} rx={0.15} />
            <text x={node.posX} y={node.posY + 2.6} className="umap-gate-label" textAnchor="middle">
              AMR⇄AGV
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

      {/* ---- 품질 정보 흐름 ---- 부적합 설비 → 품질(Q) 구역. 운송 경로와 다른 색 점선.
             P33 — 기준점이 QC 건물 전체에서 계산된 Q 구역 바운딩박스로 바뀌었다. */}
      {qZone && qualityFlows.map((e) => {
        const targetX = (qZone.minX + qZone.maxX) / 2
        const targetY = qZone.minY + 2.5
        return (
          <polyline
            key={`qflow-${e.equipmentCode}`}
            points={`${e.posX},${e.posY} ${e.posX},${qZone.minY - 0.6} ${targetX},${qZone.minY - 0.6} ${targetX},${targetY}`}
            className="umap-quality-flow"
          />
        )
      })}

      {/* ---- 품질(Q) 구역 MRB 대기 배지 ---- */}
      {qZone && layers.quality && (
        <g>
          <text
            x={(qZone.minX + qZone.maxX) / 2}
            y={qZone.maxY - 1.2}
            textAnchor="middle"
            className="umap-office-sub"
            style={fs(0.95)}
          >
            MRB 대기 {openMrbCount}건
          </text>
          {openMrbCount > 0 && (
            <circle
              cx={qZone.maxX - 1.0}
              cy={qZone.minY + 1.0}
              r={0.65}
              className="umap-office-badge"
            />
          )}
        </g>
      )}

      {/* ---- AMR·AGV ---- 항상 맨 위. 보고 있는 층의 로봇만(층마다 따로 있다).
             종류별로 레이어를 따로 끌 수 있다 — AGV는 창고동 1층 안쪽에서만 돌고 AMR은
             거기 못 들어간다(P22) — 아예 다니는 곳이 다르다. */}
      {floorRobots
        .filter((r) => (r.robotType === 'AGV' ? layers.agv : layers.amr))
        .map((r) => {
          // AGV는 렉 낱칸(칸 하나가 대략 0.3~0.9 단위) 사이를 다니므로, AMR과 같은 크기면
          // 렉보다 로봇이 훨씬 커 보인다. 마커·파렛트·작업 테를 한 단계 작게 그린다.
          const isAgv = r.robotType === 'AGV'
          const markHalf = isAgv ? 0.55 : 0.95
          const palletHalf = isAgv ? 0.75 : 1.15
          const ringR = isAgv ? 0.95 : 1.45
          const battY = isAgv ? -1.05 : -1.55
          return (
        <g
          key={r.robotCode}
          className="umap-robot"
          style={{ transform: `translate(${r.posX}px, ${r.posY}px)` }}
        >
          {/* 적재 중이면 파렛트를 얹어 그린다 — "가지러 가는 중"과 "옮기는 중"의 구분이
              물류 화면에서 가장 먼저 읽혀야 하는 정보다. 로봇 뒤에 깔아 원을 가리지 않는다. */}
          {r.laden && (
            <rect x={-palletHalf} y={-palletHalf} width={palletHalf * 2} height={palletHalf * 2}
                  rx={0.2} className="umap-pallet" />
          )}
          {/* 자기 경로와 같은 색 테 — 선이 겹쳐도 어느 로봇 것인지 눈으로 잇는다. */}
          {workingRobotIds.has(r.id) && (
            <circle r={ringR} fill="none" stroke={routeColorFor(r.robotCode)} strokeWidth={0.26} opacity={0.9} />
          )}
          {/* AGV는 사각, AMR은 원 — 창고 안에서만 도는 다른 종류의 로봇임을 모양으로 구분한다. */}
          {isAgv ? (
            <rect x={-markHalf} y={-markHalf} width={markHalf * 2} height={markHalf * 2} rx={0.16}
                  fill={ROBOT_COLOR[r.status]} className="umap-robot-agv-mark" />
          ) : (
            <circle r={markHalf} fill={ROBOT_COLOR[r.status]} stroke="#fff" strokeWidth={0.18} />
          )}
          <text y={0.38} className="umap-robot-label" textAnchor="middle" style={fs(isAgv ? 0.8 : 1.1)}>
            {r.robotCode.slice(-1)}
          </text>
          <text y={battY} className="umap-robot-batt" textAnchor="middle" style={fs(isAgv ? 0.75 : 0.95)}>
            {r.batteryPercent}%
          </text>
        </g>
          )
        })}
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
      {/* 창고동 전용 — 벽 안쪽에 점선 둘레선(P29). 실제 건물 좌표에서 일정 간격 들어간
          사각형일 뿐 새 좌표를 지어내지 않는다 — 참고 이미지의 순환 통로 인상만 낸다.
          라우팅에는 관여하지 않는 순수 시각 요소다(설계 근거: docs/p29-*.md 0-2·D3). */}
      {building.buildingCode === 'WH' && (
        <rect x={x + 0.9} y={y + 0.9} width={w - 1.8} height={h - 1.8} rx={0.6}
              className="umap-building-loop" />
      )}
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

/**
 * 생산동 내부 구역 사각형(P33) — A/B/L/Q. {@link BuildingShape}처럼 건물 하나를 통째로
 * 그리지 않고, `prodZones`가 계산한 바운딩박스 하나를 그대로 그린다. 라벨은 사각형
 * 안쪽 위에 작게 — 건물 명패(BuildingNameplate)만큼 무겁게 만들 필요는 없다(구역은
 * 건물보다 한 단계 아래 정보).
 */
function ZoneShape({ zone, textScale, dim }: { zone: ProdZoneBox; textScale: number; dim: boolean }) {
  const { minX, minY, maxX, maxY, key, label } = zone
  return (
    <g className={`umap-zone zone-${key}`} opacity={dim ? 0.35 : 1}>
      <rect x={minX} y={minY} width={maxX - minX} height={maxY - minY} rx={0.5} className="umap-zone-fill" />
      <text
        x={(minX + maxX) / 2}
        y={minY + 1.5 * textScale}
        textAnchor="middle"
        className="umap-zone-label"
        style={{ fontSize: 0.9 * textScale }}
      >
        {label}
      </text>
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

/**
 * 렉 — 실제 열×단(columnsCount×levelsCount) 격자로 낱칸을 그린다.
 *
 * <p>재고는 렉 단위로만 집계되고 칸 단위 데이터는 없다(지도 시각 규칙 — 없는 데이터를
 * 지어내지 않는다). 그래서 "몇 칸이 찼는지"는 지어내지 않고, 적재율을 칸 수에 결정적으로
 * 환산한다(`round(ratio × 총칸수)`) — 같은 비율이면 항상 같은 칸 수가 찬다. 채운 칸의 색은
 * 기존 4단계 적재율 색을 그대로 쓰고, 빈 칸은 옅게 비워 둔다. 아래 단부터 채워 보이게 해
 * "바닥부터 쌓는다"는 창고 직관을 따른다.
 */
function RackShape({
  rack, quantity, pallets = [], active = false, simplified = false,
}: {
  rack: LayoutRack
  quantity: number
  /** 이 렉 위의 파렛트 목록(P23) — 툴팁에 "몇 장·뭘 실었는지"를 보탠다. */
  pallets?: Pallet[]
  /** 지금 AGV가 이 렉에서 취출 중인가(P21) — 실제 진행 중인 주문 근거만(지도 시각 규칙). */
  active?: boolean
  /**
   * P32 — 한 층에 렉이 아주 많을 때(창고동 1층 864기) 칸 단위 격자·라벨을 생략한다
   * (RACK_LOD_THRESHOLD, UnifiedMap 참고). 툴팁은 그대로라 hover로는 여전히 코드·재고가
   * 보인다 — "안 보이게" 지운 게 아니라 "한눈에 다 그리기엔 너무 잘다"는 판단이다.
   */
  simplified?: boolean
}) {
  const ratio = rack.capacityQty > 0 ? Math.min(1, quantity / rack.capacityQty) : 0
  // 'V'/'H' = 기존 성긴 렉(2·3층, P28~P30). 'VD'/'HD' = P32 창고동 1층 밀집 렉 —
  // 발자국이 훨씬 작다(실측 밀도를 내려면 렉 하나하나가 작아야 한다). 기존 값은
  // 글자 하나도 안 바뀌었다 — 새 방향만 추가한다.
  const dense = rack.orientation === 'VD' || rack.orientation === 'HD'
  const vertical = rack.orientation === 'V' || rack.orientation === 'VD'
  const w = dense ? (vertical ? 1.0 : 1.6) : (vertical ? 1.6 : 4.6)
  const h = dense ? (vertical ? 1.6 : 1.0) : (vertical ? 4.4 : 1.6)
  const x = rack.posX - w / 2
  const y = rack.posY - h / 2
  const filledColor = rackFill(ratio)

  // 렉 코드에서 짧은 번호만 뽑는다(WH-1F-R01→R01, WH-1F-B01-R01→B01-R01) — 풀네임은
  // title 툴팁에 그대로 남긴다.
  const shortLabel = rack.rackCode.replace(/^WH-\d+F-/, '')

  const title = (
    <title>
      {`${rack.rackCode} · ${quantity}/${rack.capacityQty} EA (${Math.round(ratio * 100)}%)`
        + `${active ? ' · AGV 취출 중' : ''}`
        // P23 — 로봇이 실제로 옮기는 단위는 EA가 아니라 파렛트 한 장이다. 적재율 %만으론
        // "몇 장이 있는지·각각 뭘 실었는지"가 안 보여서 파렛트별로 한 줄씩 덧붙인다.
        + (pallets.length > 0
          ? '\n' + pallets
              .map((p) => `  ${p.pltCode}: ${p.itemCode ?? '?'} ${p.quantity ?? '?'}개`
                + (p.status === 'IN_TRANSIT' ? ' (운송 중)' : ''))
              .join('\n')
          : '')}
    </title>
  )

  if (simplified) {
    // 칸 단위 격자 대신 "아래부터 ratio만큼 채운 막대" 하나로 — 노드 수를 렉당 7~8개에서
    // 3개(프레임·채움·title)로 줄인다. 라벨도 뺀다 — 렉 수백 개가 한 화면에 있으면
    // 글자가 겹쳐 어차피 못 읽는다(hover 툴팁으로 대신한다).
    const filledH = h * ratio
    return (
      <g className={`umap-rack-g${active ? ' servicing' : ''}`}>
        <rect x={x} y={y} width={w} height={h} rx={0.1} className="umap-rack-frame" />
        {ratio > 0 && (
          <rect
            x={x} y={y + (h - filledH)} width={w} height={filledH}
            fill={filledColor} className="umap-rack-cell"
          />
        )}
        {title}
      </g>
    )
  }

  const cols = Math.max(1, rack.columnsCount)
  const levels = Math.max(1, rack.levelsCount)
  const totalCells = cols * levels
  const filledCells = Math.round(ratio * totalCells)
  const cellW = w / cols
  const cellH = h / levels

  // 아래 단(level 0)부터, 한 단 안에서는 왼쪽부터 채운다.
  const cells: { cx: number; cy: number; filled: boolean }[] = []
  let seq = 0
  for (let level = levels - 1; level >= 0; level--) {
    for (let col = 0; col < cols; col++) {
      cells.push({ cx: x + col * cellW, cy: y + level * cellH, filled: seq < filledCells })
      seq++
    }
  }

  return (
    <g className={`umap-rack-g${active ? ' servicing' : ''}`}>
      <rect x={x} y={y} width={w} height={h} rx={0.15} className="umap-rack-frame" />
      {cells.map((c, i) => (
        <rect
          key={i}
          x={c.cx + cellW * 0.08} y={c.cy + cellH * 0.08}
          width={Math.max(0, cellW * 0.84)} height={Math.max(0, cellH * 0.84)}
          fill={c.filled ? filledColor : undefined}
          className={`umap-rack-cell${c.filled ? '' : ' empty'}`}
        />
      ))}
      <text x={rack.posX} y={y + h + 0.9} textAnchor="middle" className="umap-rack-label">
        {shortLabel}
      </text>
      {title}
    </g>
  )
}

/** ISO 시각으로부터 지난 분. 배지 흐리기 판정에 쓴다. */
function minutesSince(iso: string): number {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return 0
  return (Date.now() - then) / 60000
}
