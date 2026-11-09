import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { useWebSocket } from './useWebSocket'

const hoisted = vi.hoisted(() => ({ clients: [] }))

// SockJS would open a real network connection — replace with a stub.
vi.mock('sockjs-client', () => ({
  default: class FakeSockJS {
    close() {}
  },
}))

// Mirror the real @stomp/stompjs contract: subscribe() before the async
// handshake completes throws TypeError('There is no underlying STOMP
// connection') — the bug that used to kill the page when the Agent
// Workspace mounted and subscribed in the same tick as connect().
vi.mock('@stomp/stompjs', () => ({
  Client: class FakeStompClient {
    constructor(config) {
      this.config = config
      this.connected = false
      this.subscribeCalls = []
      hoisted.clients.push(this)
    }

    activate() {
      this.activated = true
    }

    deactivate() {
      this.connected = false
    }

    publish() {}

    subscribe(topic, callback) {
      if (!this.connected) {
        throw new TypeError('There is no underlying STOMP connection')
      }
      const subscription = { id: topic, unsubscribe: vi.fn() }
      this.subscribeCalls.push({ topic, callback, subscription })
      return subscription
    }

    /** Test helper: complete the (async) connection handshake. */
    goConnected() {
      this.connected = true
      this.config.onConnect({})
    }
  },
}))

/** Mount a throwaway component so the composable has a component context. */
function mountComposable() {
  let ws
  const Probe = defineComponent({
    setup() {
      ws = useWebSocket({ brokerUrl: '/ws-chat' })
      return () => null
    },
  })
  const wrapper = mount(Probe)
  return { ws, wrapper }
}

describe('useWebSocket', () => {
  beforeEach(() => {
    hoisted.clients.length = 0
  })

  it('queues a subscription made before the handshake instead of throwing', () => {
    const { ws, wrapper } = mountComposable()

    ws.connect()
    const client = hoisted.clients[0]
    expect(client.connected).toBe(false)

    const received = []
    let subId
    expect(() => {
      subId = ws.subscribe('/topic/agent/queue', (msg) => received.push(msg))
    }).not.toThrow()
    expect(subId).toBeTruthy()

    // Not registered with STOMP yet — it would throw there today
    expect(client.subscribeCalls).toHaveLength(0)

    // Handshake completes → the queued subscription is registered
    client.goConnected()
    expect(ws.isConnected.value).toBe(true)
    expect(client.subscribeCalls.map((c) => c.topic)).toEqual(['/topic/agent/queue'])

    // …and it delivers messages
    client.subscribeCalls[0].callback({ body: JSON.stringify({ id: 7 }) })
    expect(received).toEqual([{ id: 7 }])

    expect(() => wrapper.unmount()).not.toThrow()
  })

  it('subscribes immediately once the connection is established', () => {
    const { ws, wrapper } = mountComposable()

    ws.connect()
    const client = hoisted.clients[0]
    client.goConnected()

    ws.subscribe('/topic/chat/42', () => {})
    expect(client.subscribeCalls.map((c) => c.topic)).toEqual(['/topic/chat/42'])

    expect(() => wrapper.unmount()).not.toThrow()
  })

  it('unsubscribing and unmounting are safe while still disconnected', () => {
    const { ws, wrapper } = mountComposable()

    ws.connect() // handshake never completes
    const subId = ws.subscribe('/topic/agent/queue', () => {})

    expect(() => ws.unsubscribe(subId)).not.toThrow()
    expect(() => wrapper.unmount()).not.toThrow()
    expect(ws.isConnected.value).toBe(false)
  })
})
