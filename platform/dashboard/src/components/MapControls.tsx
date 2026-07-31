import type { Layout } from '../types'
import type { MapLayers, MapView } from './UnifiedMap'

const ALL_VALUE = 'ALL'

/** 드롭다운 값 ↔ MapView. 층이 여럿인 건물은 층까지 값에 담는다. */
function toValue(view: MapView): string {
  if (!view.buildingCode) return ALL_VALUE
  return `${view.buildingCode}:${view.floorNo}`
}

function fromValue(value: string): MapView {
  if (value === ALL_VALUE) return { buildingCode: null, floorNo: 1 }
  const [buildingCode, floorNo] = value.split(':')
  return { buildingCode, floorNo: Number(floorNo) }
}

/**
 * 지도 위 조작 — 건물·층 선택과 레이어 토글.
 *
 * <p>건물을 고르면 그 외곽으로 확대돼 렉 라벨·설비명이 읽힌다. 창고동처럼 층이 여럿이면
 * 층까지 고른다 — 위층은 아래층과 같은 자리를 쓰므로 한 번에 겹쳐 보여줄 수 없다.
 */
export function MapControls({
  layout,
  view,
  onViewChange,
  layers,
  onToggleLayer,
  layerLabels,
}: {
  layout: Layout | null
  view: MapView
  onViewChange: (view: MapView) => void
  /** 레이어 토글을 쓰지 않는 화면(관제 등)은 넘기지 않는다. */
  layers?: MapLayers
  onToggleLayer?: (key: keyof MapLayers) => void
  layerLabels?: { key: keyof MapLayers; label: string }[]
}) {
  const buildings = layout?.buildings ?? []

  return (
    <div className="umap-controls">
      <label className="umap-view-pick">
        보기
        <select value={toValue(view)} onChange={(e) => onViewChange(fromValue(e.target.value))}>
          <option value={ALL_VALUE}>전체 (공장 전경)</option>
          {buildings.flatMap((b) =>
            b.floors.length > 1
              ? b.floors.map((f) => (
                  <option key={`${b.buildingCode}:${f.floorNo}`} value={`${b.buildingCode}:${f.floorNo}`}>
                    {b.name} {f.floorNo}층 — {f.name}
                  </option>
                ))
              : [
                  <option key={`${b.buildingCode}:1`} value={`${b.buildingCode}:1`}>
                    {b.name}
                  </option>,
                ])}
        </select>
      </label>

      {layers && onToggleLayer && layerLabels && (
        <div className="umap-layers">
          {layerLabels.map(({ key, label }) => (
            <label key={key} className="umap-layer-toggle">
              <input type="checkbox" checked={layers[key]} onChange={() => onToggleLayer(key)} />
              {label}
            </label>
          ))}
        </div>
      )}
    </div>
  )
}
