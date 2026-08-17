import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAgentStore } from './agent'
import * as agentApi from '../api/agent'
import * as adminApi from '../api/admin'

vi.mock('../api/agent', () => ({
  setAgentAuth: vi.fn(),
  clearAgentAuth: vi.fn(),
  fetchTickets: vi.fn(),
  fetchTicketDetail: vi.fn(),
  takeOverTicket: vi.fn(),
  sendAgentReply: vi.fn(),
  addTicketNote: vi.fn(),
  resolveTicket: vi.fn(),
}))

vi.mock('../api/admin', () => ({
  setAdminAuth: vi.fn(),
  clearAdminAuth: vi.fn(),
  uploadDocument: vi.fn(),
  addTextDocument: vi.fn(),
  fetchDocuments: vi.fn(),
  fetchChunks: vi.fn(),
  deleteDocument: vi.fn(),
}))

function ticket(overrides = {}) {
  return {
    id: 1,
    sessionId: 10,
    userId: 1,
    subject: 'Refund request',
    description: 'I need a refund',
    status: 'ESCALATED',
    priority: 'HIGH',
    assignedAgent: null,
    aiSummary: '• Customer wants a refund\n• Order #123',
    sentiment: 'negative',
    lastMessage: 'I need a refund please',
    createdAt: '2026-08-15T10:00:00',
    updatedAt: '2026-08-15T10:05:00',
    ...overrides,
  }
}

function detail(overrides = {}) {
  return {
    ...ticket(),
    messages: [
      { id: 1, sender: 'USER', content: 'I need a refund', timestamp: '2026-08-15T10:00:00' },
      { id: 2, sender: 'AI', content: 'Let me help you with that.', timestamp: '2026-08-15T10:00:05' },
    ],
    internalNotes: [],
    ...overrides,
  }
}

function authError(status = 401) {
  const err = new Error('Unauthorized')
  err.status = status
  return err
}

describe('agent store', () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    store = useAgentStore()
  })

  describe('login', () => {
    it('sets credentials and authenticates on success', async () => {
      agentApi.fetchTickets.mockResolvedValue([ticket()])

      await store.login('sarah', 'secret')

      expect(agentApi.setAgentAuth).toHaveBeenCalledWith('sarah', 'secret')
      // The knowledge base manager reuses the same Basic credentials
      expect(adminApi.setAdminAuth).toHaveBeenCalledWith('sarah', 'secret')
      expect(store.authenticated).toBe(true)
      expect(store.agentName).toBe('sarah')
      expect(store.tickets).toHaveLength(1)
    })

    it('stays unauthenticated when credentials are rejected', async () => {
      agentApi.fetchTickets.mockRejectedValue(authError(401))

      await expect(store.login('sarah', 'wrong')).rejects.toThrow()
      expect(store.authenticated).toBe(false)
    })
  })

  describe('fetchTickets', () => {
    it('populates the ticket queue and counts escalated tickets', async () => {
      agentApi.fetchTickets.mockResolvedValue([
        ticket(),
        ticket({ id: 2, status: 'OPEN', priority: 'LOW' }),
      ])

      await store.fetchTickets()

      expect(store.tickets).toHaveLength(2)
      expect(store.escalatedCount).toBe(1)
    })

    it('flips to unauthenticated on a 401', async () => {
      store.authenticated = true
      agentApi.fetchTickets.mockRejectedValue(authError(401))

      await store.fetchTickets()

      expect(store.authenticated).toBe(false)
      expect(agentApi.clearAgentAuth).toHaveBeenCalled()
      expect(adminApi.clearAdminAuth).toHaveBeenCalled()
    })
  })

  describe('openTicket', () => {
    it('loads the ticket detail with its transcript', async () => {
      agentApi.fetchTicketDetail.mockResolvedValue(detail())

      await store.openTicket(1)

      expect(store.activeTicket.id).toBe(1)
      expect(store.activeMessages).toHaveLength(2)
      expect(store.activeSummary).toContain('Customer wants a refund')
    })
  })

  describe('takeOver', () => {
    it('assigns the ticket, marks it in progress, and refreshes the list', async () => {
      store.activeTicket = detail()
      agentApi.takeOverTicket.mockResolvedValue(
        detail({ status: 'IN_PROGRESS', assignedAgent: 'sarah' }),
      )
      agentApi.fetchTickets.mockResolvedValue([
        detail({ status: 'IN_PROGRESS', assignedAgent: 'sarah' }),
      ])

      const ok = await store.takeOver()

      expect(ok).toBe(true)
      expect(agentApi.takeOverTicket).toHaveBeenCalledWith(1)
      expect(store.activeTicket.status).toBe('IN_PROGRESS')
      expect(store.activeTicket.assignedAgent).toBe('sarah')
      expect(store.activeIsAssigned).toBe(true)
    })
  })

  describe('sendReply', () => {
    it('sends an agent reply and updates the transcript', async () => {
      store.activeTicket = detail()
      agentApi.sendAgentReply.mockResolvedValue(
        detail({
          messages: [
            ...detail().messages,
            { id: 3, sender: 'AGENT', content: 'Sure, processing your refund', timestamp: '2026-08-15T10:06:00' },
          ],
        }),
      )

      const ok = await store.sendReply('Sure, processing your refund')

      expect(ok).toBe(true)
      expect(agentApi.sendAgentReply).toHaveBeenCalledWith(1, 'Sure, processing your refund')
      expect(store.activeMessages).toHaveLength(3)
      expect(store.activeMessages[2]).toMatchObject({
        sender: 'AGENT',
        content: 'Sure, processing your refund',
      })
    })

    it('does nothing for empty replies', async () => {
      store.activeTicket = detail()

      const ok = await store.sendReply('   ')

      expect(ok).toBe(false)
      expect(agentApi.sendAgentReply).not.toHaveBeenCalled()
    })
  })

  describe('addNote', () => {
    it('appends an internal note to the ticket', async () => {
      store.activeTicket = detail()
      agentApi.addTicketNote.mockResolvedValue(
        detail({ internalNotes: ['Customer was upset about the delay'] }),
      )

      await store.addNote('Customer was upset about the delay')

      expect(agentApi.addTicketNote).toHaveBeenCalledWith(1, 'Customer was upset about the delay')
      expect(store.activeNotes).toEqual(['Customer was upset about the delay'])
    })
  })

  describe('logout', () => {
    it('clears auth and all workspace state', () => {
      store.authenticated = true
      store.agentName = 'sarah'
      store.tickets = [ticket()]
      store.activeTicket = detail()

      store.logout()

      expect(store.authenticated).toBe(false)
      expect(store.agentName).toBe('')
      expect(store.tickets).toHaveLength(0)
      expect(store.activeTicket).toBeNull()
      expect(agentApi.clearAgentAuth).toHaveBeenCalled()
      expect(adminApi.clearAdminAuth).toHaveBeenCalled()
    })
  })
})
