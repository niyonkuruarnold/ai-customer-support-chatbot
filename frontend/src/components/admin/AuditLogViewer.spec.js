import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AuditLogViewer from './AuditLogViewer.vue'
import { getAuditLogsV1, getFilteredAuditLogsV1 } from '../../api/admin'

vi.mock('../../api/admin', () => ({
  getAuditLogsV1: vi.fn(),
  getFilteredAuditLogsV1: vi.fn(),
}))

vi.mock('../../api/analytics', () => ({
  exportAuditLogsCsv: vi.fn(),
  exportAuditLogsPdf: vi.fn(),
}))

const EMPTY_PAGE = { content: [], totalPages: 0, totalElements: 0 }

const EMPTY_STATE_TEXT = 'No audit logs found matching the current filters.'
const ERROR_TEXT = 'Failed to load audit logs'

function mountViewer() {
  return mount(AuditLogViewer, { props: { embedded: true } })
}

describe('AuditLogViewer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the empty state without an error banner for an empty page', async () => {
    getAuditLogsV1.mockResolvedValue(EMPTY_PAGE)

    const wrapper = mountViewer()
    await flushPromises()

    expect(wrapper.text()).toContain(EMPTY_STATE_TEXT)
    expect(wrapper.text()).toContain('0 total entries')
    expect(wrapper.text()).not.toContain(ERROR_TEXT)
  })

  it('handles a bare [] array response without an error banner', async () => {
    getAuditLogsV1.mockResolvedValue([])

    const wrapper = mountViewer()
    await flushPromises()

    expect(wrapper.text()).toContain(EMPTY_STATE_TEXT)
    expect(wrapper.text()).not.toContain(ERROR_TEXT)
  })

  it('handles a null response body without throwing', async () => {
    getAuditLogsV1.mockResolvedValue(null)

    const wrapper = mountViewer()
    await flushPromises()

    expect(wrapper.text()).toContain(EMPTY_STATE_TEXT)
    expect(wrapper.text()).not.toContain(ERROR_TEXT)
  })

  it('shows rows when the page has entries', async () => {
    getAuditLogsV1.mockResolvedValue({
      content: [{
        id: 1,
        timestamp: '2026-09-26T10:00:00',
        actorEmail: 'admin@example.com',
        actorRole: 'ADMIN',
        actionType: 'LOGIN',
        description: 'Signed in',
        success: true,
      }],
      totalPages: 1,
      totalElements: 1,
    })

    const wrapper = mountViewer()
    await flushPromises()

    expect(wrapper.text()).toContain('admin@example.com')
    expect(wrapper.text()).not.toContain(EMPTY_STATE_TEXT)
    expect(wrapper.text()).not.toContain(ERROR_TEXT)
  })

  it('shows a session message when the auth header is rejected (401)', async () => {
    const unauthorized = new Error('unauthorized')
    unauthorized.status = 401
    getAuditLogsV1.mockRejectedValue(unauthorized)

    const wrapper = mountViewer()
    await flushPromises()

    expect(wrapper.text()).toContain('Session expired — please sign in again.')
    expect(wrapper.text()).not.toContain(ERROR_TEXT)
  })

  it('still shows the failure banner for genuine server errors', async () => {
    getAuditLogsV1.mockRejectedValue(new Error('boom'))

    const wrapper = mountViewer()
    await flushPromises()

    expect(wrapper.text()).toContain(ERROR_TEXT)
  })

  it('uses the filtered endpoint once a filter is applied', async () => {
    getAuditLogsV1.mockResolvedValue(EMPTY_PAGE)
    getFilteredAuditLogsV1.mockResolvedValue(EMPTY_PAGE)

    const wrapper = mountViewer()
    await flushPromises()

    await wrapper
      .findAll('select')
      .find((s) => s.findAll('option').some((o) => o.text() === 'LOGIN'))
      .setValue('LOGIN')
    await flushPromises()

    expect(getFilteredAuditLogsV1).toHaveBeenCalledWith(
      expect.objectContaining({ actionType: 'LOGIN' }),
    )
    expect(wrapper.text()).toContain(EMPTY_STATE_TEXT)
    expect(wrapper.text()).not.toContain(ERROR_TEXT)
  })
})
