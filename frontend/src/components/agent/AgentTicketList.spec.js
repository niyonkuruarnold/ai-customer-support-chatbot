import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AgentTicketList from './AgentTicketList.vue'
import { useAgentStore } from '../../stores/agent'

function ticket(overrides = {}) {
  return {
    id: 1,
    sessionId: 10,
    userId: 1,
    userEmail: 'customer@example.com',
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

describe('AgentTicketList', () => {
  let pinia
  let store

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    store = useAgentStore(pinia)
  })

  function mountList() {
    return mount(AgentTicketList, { global: { plugins: [pinia] } })
  }

  it('shows the AI summary, sentiment, priority, and user details per ticket', () => {
    store.tickets = [
      ticket(),
      ticket({ id: 2, subject: 'Login problem', userEmail: 'sarah@example.com' }),
    ]
    const wrapper = mountList()

    const text = wrapper.text()
    expect(text).toContain('Refund request')
    expect(text).toContain('Customer wants a refund')
    expect(text).toContain('Order #123')
    expect(text).toContain('negative')
    expect(text).toContain('HIGH')
    expect(text).toContain('customer@example.com')
    expect(text).toContain('Login problem')
    expect(text).toContain('sarah@example.com')
  })

  it('falls back to the last message when no AI summary exists', () => {
    store.tickets = [ticket({ aiSummary: null, sentiment: null })]
    const wrapper = mountList()

    expect(wrapper.text()).toContain('I need a refund please')
  })

  it('shows the empty state when the queue has no tickets', () => {
    store.tickets = []
    const wrapper = mountList()

    expect(wrapper.text()).toContain('No escalated or open tickets right now.')
  })

  it('marks tickets as unassigned or with their agent', () => {
    store.tickets = [ticket(), ticket({ id: 2, assignedAgent: 'sarah' })]
    const wrapper = mountList()

    expect(wrapper.text()).toContain('Unassigned')
    expect(wrapper.text()).toContain('@sarah')
  })
})
