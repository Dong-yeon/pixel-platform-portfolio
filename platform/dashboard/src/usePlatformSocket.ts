import { Client } from '@stomp/stompjs'
import { useEffect, useRef, useState } from 'react'
import SockJS from 'sockjs-client'

/** 구독할 토픽 → 메시지 본문(JSON 파싱된 것)을 받는 핸들러. */
export type Subscriptions = Record<string, (body: unknown) => void>

/**
 * 모듈 하나의 STOMP-over-SockJS 연결을 열고 주어진 토픽들을 구독한다.
 *
 * <p>모듈마다 별개 연결이다(`/ws/fleet`, `/ws/factory`). 한 연결로 묶지 않는 이유:
 * 모듈은 각자 배포·재기동되므로 한쪽이 죽어도 다른 쪽 실시간은 살아 있어야 한다.
 * 게이트웨이가 경로로 라우팅하므로 브라우저에서는 여전히 단일 오리진이다.
 *
 * 핸들러는 ref에 담아 두어 부모가 리렌더될 때마다 연결이 끊기고 다시 열리는 일을 막는다.
 */
export function usePlatformSocket(endpoint: string, subscriptions: Subscriptions): boolean {
  const [connected, setConnected] = useState(false)
  const handlers = useRef<Subscriptions>(subscriptions)
  handlers.current = subscriptions

  // 토픽 목록이 렌더마다 새 객체여도 연결을 다시 만들지 않도록 키만 비교한다.
  const topicKey = Object.keys(subscriptions).sort().join('|')

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(endpoint),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        for (const topic of Object.keys(handlers.current)) {
          client.subscribe(topic, (message) => {
            try {
              handlers.current[topic]?.(JSON.parse(message.body))
            } catch {
              // 형식이 어긋난 메시지 하나가 구독 전체를 끊지 않게 한다.
            }
          })
        }
      },
      onWebSocketClose: () => setConnected(false),
    })

    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [endpoint, topicKey])

  return connected
}
