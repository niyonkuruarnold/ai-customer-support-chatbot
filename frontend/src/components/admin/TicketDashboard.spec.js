import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import TicketDashboard from './TicketDashboard.vue'
import * as adminApi from '../../api/admin'
import * as agentApi from '../../api/agent'
import { useToasts } from '../../composables/useToasts'

vi.mock('../../api/admin', () => ({
  setAdminAuth: vi.fn(),
  clearAdminAuth: vi.fn(),
  uploadDocument: vi.fn(),
  addTextDocument: vi.fn(),
  fetchDocuments: vi.fn(),
  fetchChunks: vi.fn(),
  deleteDocument: vi.fn(),
  fetchTickets: vi.fn(),
  closeTicket: vi.fn(),
}))

vi.mock('../../api/agent', () => ({
  setAgentAuth: vi.fn(),
  clearAgentAuth: vi.fn(),
  fetchTickets: vi.fn(),
  fetchTicketDetail: vi.fn(),
  takeOverTicket: vi.fn(),
  sendAgentReply: vi.fn(),
  addTicketNote: vi.fn(),
  resolveTicket: vi.fn(),
}))

function ticket(overrides = {}) {
  return {
    id: 1,
    sessionId: 10,
    userId: 1,
    userEmail: 'customer@codafriqa.local',
    subject: 'Refund request',
    description: 'I need a refund for my last order',
    status: 'ESCALATED',
    priority: 'HIGH',
    assignedAgent: null,
    sentiment: 'negative',
    createdAt: '2026-08-16T09:00:00',
    updatedAt: '2026-08-16T10:30:00',
    ...overrides,
  }
}

function pageResponse(content, { page = 0, size = 10, totalElements } = {}) {
  const total = totalElements ?? content.length
  const totalPages = Math.ceil(total / size)
  return {
    content,
    page,
    size,
    totalElements: total,
    totalPages,
    last: page >= totalPages - 1,
  }
}

function authError(status = 401) {
  const err = new Error('Unauthorized')
  err.status = status
  return err
}

async function mountPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(TicketDashboard, { global: { plugins: [pinia] } })
  await flushPromises()
  return wrapper
}

async function signIn(wrapper) {
  await wrapper.find('input[type="text"]').setValue('admin')
  await wrapper.find('input[type="password"]').setValue('admin123')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('TicketDashboard', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    useToasts().clear()
    agentApi.fetchTickets.mockResolvedValue([])
    adminApi.fetchTickets.mockResolvedValue(pageResponse([]))
  })

  it('shows the sign-in gate when unauthenticated', async () => {
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Admin sign in')
    expect(adminApi.fetchTickets).not.toHaveBeenCalled()
  })

  it('loads the ticket list after a successful sign-in', async () => {
    adminApi.fetchTickets.mockResolvedValue(pageResponse([ticket()]))
    const wrapper = await mountPage()

    await signIn(wrapper)

    expect(agentApi.setAgentAuth).toHaveBeenCalledWith('admin', 'admin123')
    expect(adminApi.fetchTickets).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Refund request')
    expect(wrapper.text()).toContain('customer@codafriqa.local')
    expect(wrapper.text()).toContain('Escalated')
  })

  it('rejects invalid credentials with an inline error', async () => {
    agentApi.fetchTickets.mockRejectedValue(authError(401))
    const wrapper = await mountPage()

    await signIn(wrapper)

    expect(wrapper.text()).toContain('Invalid admin credentials')
  })

  it('applies status/priority/agent filters and resets to the first page', async () => {
    adminApi.fetchTickets.mockResolvedValue(pageResponse([ticket()]))
    const wrapper = await mountPage()
    await signIn(wrapper)

    // Change the status filter
    await wrapper.find('[data-test="filter-status"]').setValue('RESOLVED')
    await wrapper.find('[data-test="filter-status"]').trigger('change')
    await flushPromises()

    expect(adminApi.fetchTickets).toHaveBeenLastCalledWith({
      status: 'RESOLVED',
      priority: undefined,
      assignedAgentId: undefined,
      page: 0,
      size: 10,
    })

    // Priority filter joins the request
    await wrapper.find('[data-test="filter-priority"]').setValue('HIGH')
    await wrapper.find('[data-test="filter-priority"]').trigger('change')
    await flushPromises()

    expect(adminApi.fetchTickets).toHaveBeenLastCalledWith({
      status: 'RESOLVED',
      priority: 'HIGH',
      assignedAgentId: undefined,
      page: 0,
      size: 10,
    })

    // Agent filter (number input -> numeric value)
    await wrapper.find('[data-test="filter-agent"]').setValue('2')
    await wrapper.find('[data-test="filter-agent"]').trigger('change')
    await flushPromises()

    expect(adminApi.fetchTickets).toHaveBeenLastCalledWith({
      status: 'RESOLVED',
      priority: 'HIGH',
      assignedAgentId: 2,
      page: 0,
      size: 10,
    })
  })

  it('paginates through the ticket list', async () => {
    adminApi.fetchTickets.mockResolvedValue(
      pageResponse([ticket({ id: 1 })], { page: 0, size: 10, totalElements: 12 }),
    )
    const wrapper = await mountPage()
    await signIn(wrapper)

    // Page 1 of 2
    expect(wrapper.text()).toContain('Page 1 of 2')

    adminApi.fetchTickets.mockResolvedValue(
      pageResponse([ticket({ id: 2 })], { page: 1, size: 10, totalElements: 12 }),
    )
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('Next'))
      .trigger('click')
    await flushPromises()

    expect(adminApi.fetchTickets).toHaveBeenLastCalledWith({
      status: undefined,
      priority: undefined,
      assignedAgentId: undefined,
      page: 1,
      size: 10,
    })
    expect(wrapper.text()).toContain('Page 2 of 2')
  })

  it('shows the close action only for resolved tickets and closes them', async () => {
    adminApi.fetchTickets.mockResolvedValue(
      pageResponse([
        ticket({ id: 1, status: 'RESOLVED', assignedAgent: 'alex@codafriqa.local' }),
        ticket({ id: 2, status: 'OPEN' }),
      ]),
    )
    adminApi.closeTicket.mockResolvedValue(ticket({ id: 1, status: 'CLOSED' }))
    const wrapper = await mountPage()
    await signIn(wrapper)

    const closeButtons = wrapper.findAll('[data-test="close-ticket"]')
    expect(closeButtons).toHaveLength(1) // only the resolved ticket

    await closeButtons[0].trigger('click')
    await flushPromises()

    expect(adminApi.closeTicket).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('marked as closed')
  })

  it('shows an error toast when closing fails', async () => {
    adminApi.fetchTickets.mockResolvedValue(pageResponse([ticket({ id: 1, status: 'RESOLVED' })]))
    const err = new Error('Bad transition')
    err.response = { status: 400, data: { message: 'Invalid ticket status transition' } }
    adminApi.closeTicket.mockRejectedValue(err)
    const wrapper = await mountPage()
    await signIn(wrapper)

    await wrapper.find('[data-test="close-ticket"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Invalid ticket status transition')
  })

  it('returns to the sign-in gate when a request hits a 401', async () => {
    adminApi.fetchTickets.mockResolvedValueOnce(pageResponse([ticket()])) // sign-in load
    adminApi.fetchTickets.mockRejectedValue(authError(401)) // every later load
    const wrapper = await mountPage()
    await signIn(wrapper)

    // Trigger a refresh (filter change) that hits the 401 — setValue on a
    // select fires `change`, and the component re-renders to the gate, so
    // no second trigger is possible
    await wrapper.find('[data-test="filter-status"]').setValue('OPEN')
    await flushPromises()

    expect(wrapper.text()).toContain('Admin sign in')
    expect(wrapper.text()).toContain('Session expired')
  })

  it('clears filters with the clear button', async () => {
    adminApi.fetchTickets.mockResolvedValue(pageResponse([ticket()]))
    const wrapper = await mountPage()
    await signIn(wrapper)

    await wrapper.find('[data-test="filter-status"]').setValue('OPEN')
    await wrapper.find('[data-test="filter-status"]').trigger('change')
    await flushPromises()

    const clearButton = wrapper
      .findAll('button')
      .find((b) => b.text().includes('Clear filters'))
    expect(clearButton).toBeTruthy()

    await clearButton.trigger('click')
    await flushPromises()

    expect(adminApi.fetchTickets).toHaveBeenLastCalledWith({
      status: undefined,
      priority: undefined,
      assignedAgentId: undefined,
      page: 0,
      size: 10,
    })
  })
})
