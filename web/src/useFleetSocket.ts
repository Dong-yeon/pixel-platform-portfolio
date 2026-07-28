import { Client } from '@stomp/stompjs'
import { useEffect, useRef, useState } from 'react'
import SockJS from 'sockjs-client'
import type { FleetEvent, Robot } from './types'

interface Handlers {
  onRobot: (robot: Robot) => void
  onEvent: (event: FleetEvent) => void
}

/**
 * Opens one STOMP-over-SockJS connection to the control server and forwards
 * /topic/robots and /topic/events to the given handlers. Handlers are kept in refs
 * so the connection isn't torn down and rebuilt on every parent re-render.
 */
export function useFleetSocket({ onRobot, onEvent }: Handlers): boolean {
  const [connected, setConnected] = useState(false)
  const handlers = useRef<Handlers>({ onRobot, onEvent })
  handlers.current = { onRobot, onEvent }

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        client.subscribe('/topic/robots', (m) => handlers.current.onRobot(JSON.parse(m.body)))
        client.subscribe('/topic/events', (m) => handlers.current.onEvent(JSON.parse(m.body)))
      },
      onWebSocketClose: () => setConnected(false),
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [])

  return connected
}
