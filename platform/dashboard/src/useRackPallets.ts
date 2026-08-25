import { useEffect, useState } from 'react'
import { api, type Pallet } from './api'

/**
 * 렉 코드 → 그 위에 실린 파렛트 목록(P23).
 *
 * <p>`useRackStock`(적재율용 합계)과 같은 조인 방식이다 — 렉의 <b>용량</b>은 평면도(factory)가,
 * <b>파렛트</b>는 재고(WMS)가 갖는다. 로봇이 실제로 옮기는 단위는 품목 수량이 아니라
 * 파렛트 한 장이므로(설계 근거: docs/p23-pallet-unit-design.md), 적재율 %만으론 안 보이는
 * "몇 장이 있는지·각각 뭘 싣고 있는지"를 여기서 보여준다.
 *
 * <p>RETIRED(출고로 소진된) 파렛트는 뺀다 — 지금 그 로케이션에 물리적으로 있는 것만.
 */
export function useRackPallets(intervalMs = 20_000): Record<string, Pallet[]> {
  const [byLocation, setByLocation] = useState<Record<string, Pallet[]>>({})

  useEffect(() => {
    const load = () => {
      api.wms
        .pallets()
        .then((pallets) => {
          const grouped: Record<string, Pallet[]> = {}
          for (const p of pallets) {
            if (p.status === 'RETIRED') continue
            ;(grouped[p.locationCode] ??= []).push(p)
          }
          setByLocation(grouped)
        })
        .catch(() => setByLocation({}))
    }
    load()
    const timer = window.setInterval(load, intervalMs)
    return () => window.clearInterval(timer)
  }, [intervalMs])

  return byLocation
}
