import { useEffect, useState } from 'react'
import { api } from './api'

/**
 * 렉 코드 → 적재 수량.
 *
 * <p>렉의 <b>용량</b>은 평면도(factory)가, <b>수량</b>은 재고(WMS)가 갖는다. 둘을 코드로 맞춰
 * 적재율을 낸다 — 모듈 DB가 다르므로 조인은 소비 측인 여기서 한다.
 *
 * <p>WMS가 없으면 조용히 빈 값이다(컴포저블) — 렉은 회색으로 그려진다.
 */
export function useRackStock(intervalMs = 20_000): Record<string, number> {
  const [stock, setStock] = useState<Record<string, number>>({})

  useEffect(() => {
    const load = () => {
      api.wms
        .stocks()
        .then((rows) => {
          // 한 로케이션에 품목이 여럿일 수 있으니 합산한다.
          const byLocation: Record<string, number> = {}
          for (const row of rows) {
            byLocation[row.locationCode] = (byLocation[row.locationCode] ?? 0) + row.quantity
          }
          setStock(byLocation)
        })
        .catch(() => setStock({}))
    }
    load()
    const timer = window.setInterval(load, intervalMs)
    return () => window.clearInterval(timer)
  }, [intervalMs])

  return stock
}
