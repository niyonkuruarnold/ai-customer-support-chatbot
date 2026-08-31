import { defineStore } from 'pinia'
import {
  buildErrorMessage,
  closeChatSession,
  fetchSessionInfo,
  resetBackendSession,
  sendChatMessage,
} from '../api/chat'

// Must match the backend's @Size validation on ChatRequestDto
export const MAX_MESSAGE_LENGTH = 2000

// localStorage keys used to persist the conversation across reloads
export const STORAGE_KEY = 'ai-support-chat:messages'
export const SESSION_KEY = 'ai-support-chat:sessionId'

// How often the customer view polls the backend for status + agent replies
const POLL_INTERVAL_MS = 3000

const SENDER_TO_ROLE = { USER: 'user', AI: 'assistant', AGENT: 'agent' }

function loadSessionId() {
  try {
    return localStorage.getItem(SESSION_KEY) || null
  } catch {
    return null
  }
}

function parseTimestamp(value) {
  if (!value) return Date.now()
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? Date.now() : parsed
}

function mapServerMessage(m) {
  return {
    id: `srv-${m.id}`,
    serverId: m.id,
    role: SENDER_TO_ROLE[m.sender] ?? 'assistant',
    content: m.content,
    timestamp: parseTimestamp(m.timestamp),
    status: 'sent',
    // The backend session endpoint does not carry RAG metadata, so these
    // stay empty for restored messages; live responses populate them via
    // the send path (and poll merges preserve them, see pollSession).
    ragUsed: false,
    contextReferences: [],
  }
}

let idCounter = 0
function nextId() {
  return `msg-${Date.now()}-${idCounter++}`
}

/**
 * Chat state store.
 *
 * Messages have the shape:
 *   { id, role: 'user' | 'assistant' | 'agent', content, timestamp, status, error? }
 * where status is 'sending' | 'sent' | 'failed'.
 *
 * The conversation lives on the backend in a chat session (created on the
 * first message). Messages are mirrored to localStorage as an offline
 * fallback, and `loadHistory()` restores from the backend when a session id
 * exists. While a session is active the store polls the backend so agent
 * replies (after a human handoff) appear in the customer's chat.
 */
export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [],
    isLoading: false,
    sessionId: loadSessionId(),
    sessionStatus: null, // 'ACTIVE' | 'ESCALATED' | null
    pollTimer: null,
  }),

  getters: {
    hasMessages: (state) => state.messages.length > 0,
    isEscalated: (state) => state.sessionStatus === 'ESCALATED',
    hasAgentMessages: (state) => state.messages.some((m) => m.role === 'agent'),
  },

  actions: {
    /** Load prior messages when the conversation loads. */
    async loadHistory() {
      if (this.sessionId) {
        try {
          const info = await fetchSessionInfo(this.sessionId)
          this.sessionStatus = info.status ?? this.sessionStatus
          this.messages = (info.messages ?? []).map(mapServerMessage)
          this.persist()
          this.startPolling()
          return
        } catch {
          // Session gone or backend down — fall back to local storage
        }
      }
      this.loadFromStorage()
    },

    loadFromStorage() {
      try {
        const raw = localStorage.getItem(STORAGE_KEY)
        if (raw) {
          const parsed = JSON.parse(raw)
          if (Array.isArray(parsed)) this.messages = parsed
        }
      } catch {
        // Ignore corrupt or unavailable storage
      }
    },

    persist() {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(this.messages))
      } catch {
        // Storage unavailable (private mode, quota) — history just won't
        // survive a reload
      }
    },

    persistSession() {
      try {
        if (this.sessionId) {
          localStorage.setItem(SESSION_KEY, String(this.sessionId))
        } else {
          localStorage.removeItem(SESSION_KEY)
        }
      } catch {
        // Ignore storage errors
      }
    },

    /**
     * Push a message with an initial status and persist.
     * Returns the reactive proxy (not the raw object) so callers can mutate
     * the message's status later and still trigger component updates.
     *
     * `extra` merges additional fields onto the message — e.g. RAG metadata
     * (ragUsed / contextReferences) for assistant responses.
     */
    addMessage(role, content, status = 'sent', extra = {}) {
      const message = {
        id: nextId(),
        role,
        content,
        timestamp: Date.now(),
        status,
        ...extra,
      }
      this.messages.push(message)
      this.persist()
      return this.messages[this.messages.length - 1]
    },

    /**
     * Validate + send a message through the API.
     *
     * Returns the user message id when the message was accepted, or null
     * when validation blocked it (empty, over the length limit, or a
     * request already in flight). Never throws: failures are recorded on
     * the message with status 'failed' and a readable `error`.
     *
     * @param {string} text
     * @returns {Promise<string|null>}
     */
    async sendMessage(text) {
      const content = (text ?? '').trim()
      if (!content || content.length > MAX_MESSAGE_LENGTH || this.isLoading) {
        return null
      }

      const userMessage = this.addMessage('user', content, 'sending', {
        originalContent: content,
      })
      this.isLoading = true

      try {
        const data = await sendChatMessage(content, this.sessionId)
        userMessage.status = 'sent'
        if (data.sessionId) {
          this.sessionId = data.sessionId
          this.persistSession()
        }
        if (data.status) this.sessionStatus = data.status
        this.addMessage('assistant', data.response ?? data.content ?? '', 'sent', {
          ragUsed: data.ragUsed ?? false,
          contextReferences: data.contextReferences ?? [],
          sources: data.sourceCitations ?? [],
        })
        this.startPolling()
        return userMessage.id
      } catch (err) {
        userMessage.status = 'failed'
        userMessage.error = buildErrorMessage(err)
        userMessage.content = userMessage.error
        this.persist()
        return userMessage.id
      } finally {
        this.isLoading = false
      }
    },

    /**
     * Re-send a failed user message. Removes the failed bubble and runs it
     * through the normal send pipeline again.
     *
     * @param {string} id
     * @returns {Promise<string|null>}
     */
    async retryMessage(id) {
      const failed = this.messages.find((m) => m.id === id)
      if (!failed || failed.role !== 'user') return null

      const content = failed.originalContent ?? failed.content
      this.messages = this.messages.filter((m) => m.id !== id)
      this.persist()
      return this.sendMessage(content)
    },

    /** Clear local history and notify the backend to reset its session. */
    async clearConversation() {
      this.stopPolling()
      this.messages = []
      this.isLoading = false
      this.sessionId = null
      this.sessionStatus = null
      this.persist()
      this.persistSession()
      // Fire-and-forget: a no-op until the backend exposes a reset endpoint
      resetBackendSession().catch(() => {})
    },

    /**
     * Close the current session on the backend (mark as CLOSED), then
     * fully reset local state so the customer can start a fresh AI
     * conversation immediately.
     */
    async closeAndResetConversation() {
      const previousSessionId = this.sessionId
      this.stopPolling()
      // Close the session on the backend (fire-and-forget)
      if (previousSessionId) {
        closeChatSession(previousSessionId).catch(() => {})
      }
      this.messages = []
      this.isLoading = false
      this.sessionId = null
      this.sessionStatus = null
      this.persist()
      this.persistSession()
    },

    /** Begin polling the backend for status changes + agent replies. */
    startPolling() {
      this.stopPolling()
      if (!this.sessionId) return
      this.pollTimer = setInterval(() => this.pollSession(), POLL_INTERVAL_MS)
    },

    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },

    /** Fetch the latest session state and merge it into the feed. */
    async pollSession() {
      if (!this.sessionId) return
      try {
        const info = await fetchSessionInfo(this.sessionId)
        if (info.status) this.sessionStatus = info.status

        // The session endpoint does not return RAG metadata, so carry the
        // ragUsed/contextReferences over from the local copy of each message
        // — matched by serverId when known, else by identical assistant
        // content (locally-sent messages have no serverId yet) — so
        // citations survive poll merges.
        const serverMessages = (info.messages ?? []).map((m) => {
          const mapped = mapServerMessage(m)
          const local = this.messages.find(
            (l) =>
              l.serverId === m.id ||
              (l.role === 'assistant' && l.content === m.content),
          )
          if (local) {
            mapped.ragUsed = local.ragUsed
            mapped.contextReferences = local.contextReferences
            mapped.sources = local.sources
          }
          return mapped
        })
        // Keep locally failed messages so the customer can retry them
        const localFailed = this.messages.filter((m) => m.status === 'failed')
        const next = serverMessages.concat(localFailed)
        if (next.length !== this.messages.length) {
          this.messages = next
          this.persist()
        }
      } catch {
        // Transient poll failure — the next tick will retry
      }
    },
  },
})
