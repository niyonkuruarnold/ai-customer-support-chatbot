import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import {
  MAX_MESSAGE_LENGTH,
  SESSION_KEY,
  STORAGE_KEY,
  useChatStore,
} from './chat'
import {
  fetchSessionInfo,
  resetBackendSession,
  sendChatMessage,
} from '../api/chat'

vi.mock('../api/chat', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    fetchSessionInfo: vi.fn(),
    resetBackendSession: vi.fn(),
    sendChatMessage: vi.fn(),
  }
})

function okResponse(overrides = {}) {
  return {
    response: 'Hi there! How can I help?',
    sessionId: 5,
    status: 'ACTIVE',
    ...overrides,
  }
}

describe('chat store', () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    fetchSessionInfo.mockResolvedValue({ id: 5, status: 'ACTIVE', messages: [] })
    resetBackendSession.mockResolvedValue(undefined)
    store = useChatStore()
  })

  describe('sendMessage', () => {
    it('sends a message, stores the session id, and appends the AI response', async () => {
      sendChatMessage.mockResolvedValue(okResponse())

      const id = await store.sendMessage('Hello')

      expect(id).toBeTruthy()
      expect(sendChatMessage).toHaveBeenCalledWith('Hello', null)
      expect(store.sessionId).toBe(5)
      expect(store.sessionStatus).toBe('ACTIVE')
      expect(store.messages).toHaveLength(2)
      expect(store.messages[0]).toMatchObject({
        role: 'user',
        content: 'Hello',
        status: 'sent',
      })
      expect(store.messages[1]).toMatchObject({
        role: 'assistant',
        content: 'Hi there! How can I help?',
      })
      expect(store.isLoading).toBe(false)
      expect(localStorage.getItem(SESSION_KEY)).toBe('5')
      store.stopPolling()
    })

    it('reuses the stored session id on follow-up messages', async () => {
      store.sessionId = 42
      sendChatMessage.mockResolvedValue(okResponse({ sessionId: 42 }))

      await store.sendMessage('Second question')

      expect(sendChatMessage).toHaveBeenCalledWith('Second question', 42)
      store.stopPolling()
    })

    it('marks the session as escalated when the backend returns ESCALATED', async () => {
      sendChatMessage.mockResolvedValue(
        okResponse({
          response: "You've been connected to a human support agent.",
          status: 'ESCALATED',
        }),
      )

      await store.sendMessage('Talk to a human agent')

      expect(store.isEscalated).toBe(true)
      store.stopPolling()
    })

    it('trims surrounding whitespace before sending', async () => {
      sendChatMessage.mockResolvedValue(okResponse())

      await store.sendMessage('   Hello   ')

      expect(sendChatMessage).toHaveBeenCalledWith('Hello', null)
      expect(store.messages[0].content).toBe('Hello')
      store.stopPolling()
    })

    it('rejects empty and whitespace-only messages', async () => {
      expect(await store.sendMessage('')).toBeNull()
      expect(await store.sendMessage('   ')).toBeNull()

      expect(sendChatMessage).not.toHaveBeenCalled()
      expect(store.messages).toHaveLength(0)
    })

    it('rejects messages over the character limit but allows the exact limit', async () => {
      expect(await store.sendMessage('a'.repeat(MAX_MESSAGE_LENGTH + 1))).toBeNull()
      expect(sendChatMessage).not.toHaveBeenCalled()
      expect(store.messages).toHaveLength(0)

      sendChatMessage.mockResolvedValue(okResponse())
      await store.sendMessage('a'.repeat(MAX_MESSAGE_LENGTH))
      expect(sendChatMessage).toHaveBeenCalledTimes(1)
      expect(store.messages).toHaveLength(2)
      store.stopPolling()
    })

    it('blocks sending while a request is in flight', async () => {
      let resolveRequest
      sendChatMessage.mockReturnValue(
        new Promise((resolve) => (resolveRequest = resolve)),
      )

      const first = store.sendMessage('first')
      expect(await store.sendMessage('second')).toBeNull()

      resolveRequest(okResponse())
      await first

      expect(sendChatMessage).toHaveBeenCalledTimes(1)
      expect(store.messages).toHaveLength(2)
      store.stopPolling()
    })

    it('captures RAG metadata from the response onto the assistant message', async () => {
      sendChatMessage.mockResolvedValue(
        okResponse({
          ragUsed: true,
          contextReferences: [
            { documentId: 3, title: 'Returns Policy', sourceType: 'TEXT' },
          ],
        }),
      )

      await store.sendMessage('What is the return policy?')

      expect(store.messages[1]).toMatchObject({
        role: 'assistant',
        ragUsed: true,
        contextReferences: [
          { documentId: 3, title: 'Returns Policy', sourceType: 'TEXT' },
        ],
      })
      store.stopPolling()
    })

    it('defaults RAG metadata to empty when the response has none', async () => {
      sendChatMessage.mockResolvedValue(okResponse())

      await store.sendMessage('Hello')

      expect(store.messages[1].ragUsed).toBe(false)
      expect(store.messages[1].contextReferences).toEqual([])
      store.stopPolling()
    })

    it('marks the message as failed with an error on API failure', async () => {
      sendChatMessage.mockRejectedValueOnce(new Error('boom'))

      const id = await store.sendMessage('Hello')

      const failed = store.messages.find((m) => m.id === id)
      expect(failed.status).toBe('failed')
      expect(failed.error).toBeTruthy()
      expect(store.isLoading).toBe(false)
    })
  })

  describe('retryMessage', () => {
    it('removes the failed message and resends its content', async () => {
      sendChatMessage.mockRejectedValueOnce(new Error('network'))
      const id = await store.sendMessage('Hello')

      sendChatMessage.mockResolvedValueOnce(okResponse({ response: 'Back online!' }))
      const newId = await store.retryMessage(id)

      expect(newId).toBeTruthy()
      expect(newId).not.toBe(id)
      expect(store.messages).toHaveLength(2)
      expect(store.messages.some((m) => m.id === id)).toBe(false)
      expect(store.messages[1].content).toBe('Back online!')
      store.stopPolling()
    })

    it('ignores unknown ids and assistant messages', async () => {
      const assistant = store.addMessage('assistant', 'hey')

      expect(await store.retryMessage('does-not-exist')).toBeNull()
      expect(await store.retryMessage(assistant.id)).toBeNull()
      expect(sendChatMessage).not.toHaveBeenCalled()
    })
  })

  describe('persistence', () => {
    it('persists messages to localStorage after sending', async () => {
      sendChatMessage.mockResolvedValue(okResponse())
      await store.sendMessage('Hello')

      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY))
      expect(saved).toHaveLength(2)
      expect(saved[0].content).toBe('Hello')
      store.stopPolling()
    })

    it('restores persisted messages from localStorage when there is no session', async () => {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify([
          { id: 'a', role: 'user', content: 'hi', timestamp: 1, status: 'sent' },
        ]),
      )

      await store.loadHistory()

      expect(store.messages).toHaveLength(1)
      expect(store.messages[0].content).toBe('hi')
    })

    it('loads history from the backend when a session id exists', async () => {
      localStorage.setItem(SESSION_KEY, '7')
      setActivePinia(createPinia())
      store = useChatStore()

      fetchSessionInfo.mockResolvedValue({
        id: 7,
        status: 'ESCALATED',
        messages: [
          { id: 1, sender: 'USER', content: 'hi', timestamp: '2026-08-15T10:00:00' },
        ],
      })

      await store.loadHistory()

      expect(store.messages).toHaveLength(1)
      expect(store.messages[0]).toMatchObject({
        role: 'user',
        content: 'hi',
        serverId: 1,
      })
      expect(store.sessionStatus).toBe('ESCALATED')
      store.stopPolling()
    })

    it('handles corrupt local storage gracefully', () => {
      localStorage.setItem(STORAGE_KEY, '{not valid json')

      expect(() => store.loadHistory()).not.toThrow()
      expect(store.messages).toHaveLength(0)
    })
  })

  describe('polling', () => {
    it('polls the backend and merges agent replies after a handoff', async () => {
      sendChatMessage.mockResolvedValue(okResponse({ sessionId: 7 }))
      await store.sendMessage('hello')
      store.stopPolling()

      fetchSessionInfo.mockResolvedValue({
        id: 7,
        status: 'ESCALATED',
        messages: [
          { id: 10, sender: 'USER', content: 'hello', timestamp: '2026-08-15T10:00:00' },
          { id: 11, sender: 'AI', content: 'ok', timestamp: '2026-08-15T10:00:01' },
          { id: 12, sender: 'AGENT', content: 'Hi, I am Sarah', timestamp: '2026-08-15T10:05:00' },
        ],
      })

      await store.pollSession()

      expect(store.sessionStatus).toBe('ESCALATED')
      expect(store.hasAgentMessages).toBe(true)
      const agentMessage = store.messages.find((m) => m.role === 'agent')
      expect(agentMessage).toMatchObject({ content: 'Hi, I am Sarah', serverId: 12 })
    })

    it('startPolling schedules an interval and stopPolling clears it', () => {
      vi.useFakeTimers()
      try {
        store.sessionId = 7
        store.startPolling()
        expect(fetchSessionInfo).not.toHaveBeenCalled()

        vi.advanceTimersByTime(3100)
        expect(fetchSessionInfo).toHaveBeenCalledWith(7)

        store.stopPolling()
        vi.advanceTimersByTime(3100)
        expect(fetchSessionInfo).toHaveBeenCalledTimes(1)
      } finally {
        vi.useRealTimers()
      }
    })

    it('does not poll without a session id', () => {
      vi.useFakeTimers()
      try {
        store.startPolling()
        vi.advanceTimersByTime(3100)
        expect(fetchSessionInfo).not.toHaveBeenCalled()
      } finally {
        vi.useRealTimers()
      }
    })

    it('preserves RAG metadata on assistant messages across poll merges', async () => {
      sendChatMessage.mockResolvedValue(
        okResponse({
          sessionId: 9,
          ragUsed: true,
          contextReferences: [
            { documentId: 3, title: 'Returns Policy', sourceType: 'TEXT' },
          ],
        }),
      )
      await store.sendMessage('What is the return policy?')
      store.stopPolling()

      // The session endpoint returns the same transcript without RAG fields
      fetchSessionInfo.mockResolvedValue({
        id: 9,
        status: 'ACTIVE',
        messages: [
          {
            id: 1,
            sender: 'USER',
            content: 'What is the return policy?',
            timestamp: '2026-08-15T10:00:00',
          },
          {
            id: 2,
            sender: 'AI',
            content: 'Returns are accepted within 30 days.',
            timestamp: '2026-08-15T10:00:01',
          },
        ],
      })

      await store.pollSession()

      const ai = store.messages.find((m) => m.role === 'assistant')
      expect(ai.ragUsed).toBe(true)
      expect(ai.contextReferences).toHaveLength(1)
      expect(ai.contextReferences[0].documentId).toBe(3)
    })
  })

  describe('clearConversation', () => {
    it('clears messages, session id, status, and notifies the backend', async () => {
      store.sessionId = 7
      store.sessionStatus = 'ESCALATED'

      await store.clearConversation()

      expect(store.messages).toHaveLength(0)
      expect(store.sessionId).toBeNull()
      expect(store.sessionStatus).toBeNull()
      expect(store.isLoading).toBe(false)
      expect(localStorage.getItem(SESSION_KEY)).toBeNull()
      expect(resetBackendSession).toHaveBeenCalledTimes(1)
    })
  })
})
