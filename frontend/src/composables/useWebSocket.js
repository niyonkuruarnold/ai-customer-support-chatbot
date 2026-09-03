/**
 * WebSocket composable for real-time chat messaging.
 * 
 * Uses STOMP over SockJS for reliable message delivery.
 * Provides methods to connect, disconnect, subscribe, and send messages.
 */
import { ref, onUnmounted } from 'vue'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

/**
 * WebSocket composable for real-time chat.
 * 
 * @param {Object} options - Configuration options
 * @param {string} options.brokerUrl - WebSocket broker URL (default: /ws)
 * @param {Function} options.onConnect - Callback when connected
 * @param {Function} options.onDisconnect - Callback when disconnected
 * @param {Function} options.onError - Callback on error
 * @returns {Object} WebSocket methods and state
 */
export function useWebSocket(options = {}) {
  const {
    brokerUrl = '/ws',
    onConnect = null,
    onDisconnect = null,
    onError = null,
  } = options

  const isConnected = ref(false)
  const isConnecting = ref(false)
  const error = ref(null)

  let stompClient = null
  let subscriptions = new Map()

  /**
   * Create a new STOMP client with SockJS fallback.
   */
  function createClient() {
    const wsUrl = import.meta.env.VITE_WS_URL || 
                  import.meta.env.VITE_API_BASE_URL?.replace('http', 'ws')?.replace('/api', '') ||
                  'http://localhost:8080'

    return new Client({
      webSocketFactory: () => new SockJS(`${wsUrl}${brokerUrl}`),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        isConnected.value = true
        isConnecting.value = false
        error.value = null
        console.log('[WebSocket] Connected')
        onConnect?.()
      },
      onDisconnect: () => {
        isConnected.value = false
        isConnecting.value = false
        console.log('[WebSocket] Disconnected')
        onDisconnect?.()
      },
      onStompError: (frame) => {
        console.error('[WebSocket] STOMP error:', frame.headers['message'])
        error.value = frame.headers['message']
        onError?.(frame)
      },
      onWebSocketError: (event) => {
        console.error('[WebSocket] WebSocket error:', event)
        error.value = 'WebSocket connection failed'
        isConnecting.value = false
        onError?.(event)
      },
    })
  }

  /**
   * Connect to the WebSocket broker.
   */
  function connect() {
    if (isConnected.value || isConnecting.value) return

    isConnecting.value = true
    error.value = null

    stompClient = createClient()
    stompClient.activate()
  }

  /**
   * Disconnect from the WebSocket broker.
   */
  function disconnect() {
    // Unsubscribe from all topics
    subscriptions.forEach((sub) => {
      try {
        sub.unsubscribe()
      } catch (e) {
        console.debug('[WebSocket] Unsubscribe error (ignored):', e)
      }
    })
    subscriptions.clear()

    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }

    isConnected.value = false
    isConnecting.value = false
  }

  /**
   * Subscribe to a topic.
   * 
   * @param {string} topic - The topic to subscribe to (e.g., /topic/chat/123)
   * @param {Function} callback - Message callback
   * @returns {string} Subscription ID for unsubscribing
   */
  function subscribe(topic, callback) {
    if (!stompClient) {
      console.warn('[WebSocket] Cannot subscribe: not connected')
      return null
    }

    const subscription = stompClient.subscribe(topic, (message) => {
      try {
        const payload = JSON.parse(message.body)
        callback(payload)
      } catch (e) {
        console.error('[WebSocket] Message parse error:', e)
      }
    })

    const subId = `sub-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    subscriptions.set(subId, subscription)

    return subId
  }

  /**
   * Unsubscribe from a topic.
   * 
   * @param {string} subId - Subscription ID returned by subscribe()
   */
  function unsubscribe(subId) {
    const subscription = subscriptions.get(subId)
    if (subscription) {
      try {
        subscription.unsubscribe()
      } catch (e) {
        console.debug('[WebSocket] Unsubscribe error (ignored):', e)
      }
      subscriptions.delete(subId)
    }
  }

  /**
   * Send a message to a destination.
   * 
   * @param {string} destination - The destination (e.g., /app/chat.sendMessage/123)
   * @param {Object} payload - Message payload
   */
  function send(destination, payload) {
    if (!stompClient || !isConnected.value) {
      console.warn('[WebSocket] Cannot send: not connected')
      return false
    }

    stompClient.publish({
      destination,
      body: JSON.stringify(payload),
    })

    return true
  }

  /**
   * Send a chat message to a session.
   * 
   * @param {number} sessionId - The chat session ID
   * @param {Object} message - Message object { sender, content, internal }
   */
  function sendChatMessage(sessionId, message) {
    const destination = `/app/chat.sendMessage/${sessionId}`
    return send(destination, {
      sessionId,
      ...message,
      type: message.internal ? 'NOTE' : 'MESSAGE',
    })
  }

  /**
   * Subscribe to a chat session topic.
   * 
   * @param {number} sessionId - The chat session ID
   * @param {Function} callback - Message callback
   * @returns {string} Subscription ID
   */
  function subscribeToSession(sessionId, callback) {
    return subscribe(`/topic/chat/${sessionId}`, callback)
  }

  /**
   * Subscribe to agent-only messages for a session.
   * 
   * @param {number} sessionId - The chat session ID
   * @param {Function} callback - Message callback
   * @returns {string} Subscription ID
   */
  function subscribeToAgentChannel(sessionId, callback) {
    return subscribe(`/topic/agent/${sessionId}`, callback)
  }

  // Cleanup on unmount
  onUnmounted(() => {
    disconnect()
  })

  return {
    // State
    isConnected,
    isConnecting,
    error,

    // Methods
    connect,
    disconnect,
    subscribe,
    unsubscribe,
    send,
    sendChatMessage,
    subscribeToSession,
    subscribeToAgentChannel,
  }
}
