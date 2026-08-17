import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import KnowledgeBaseAdmin from './KnowledgeBaseAdmin.vue'
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

function document(overrides = {}) {
  return {
    id: 1,
    title: 'Shipping policy',
    sourceType: 'TEXT',
    fileName: null,
    chunkCount: 2,
    createdAt: '2026-08-16T09:00:00',
    ...overrides,
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
  const wrapper = mount(KnowledgeBaseAdmin, { global: { plugins: [pinia] } })
  await flushPromises()
  return wrapper
}

async function signIn(wrapper) {
  await wrapper.find('input[type="text"]').setValue('admin')
  await wrapper.find('input[type="password"]').setValue('admin123')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('KnowledgeBaseAdmin', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    useToasts().clear()
    adminApi.fetchDocuments.mockResolvedValue([])
    adminApi.fetchChunks.mockResolvedValue([])
    agentApi.fetchTickets.mockResolvedValue([])
  })

  it('shows the sign-in gate when unauthenticated', async () => {
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Admin sign in')
    expect(wrapper.find('[data-test="dropzone"]').exists()).toBe(false)
    expect(adminApi.fetchDocuments).not.toHaveBeenCalled()
  })

  it('loads the knowledge base after a successful sign-in', async () => {
    adminApi.fetchDocuments.mockResolvedValue([document()])
    const wrapper = await mountPage()

    await signIn(wrapper)

    expect(wrapper.text()).toContain('Shipping policy')
    expect(wrapper.text()).toContain('Knowledge Base Admin')
    expect(agentApi.setAgentAuth).toHaveBeenCalledWith('admin', 'admin123')
    expect(adminApi.fetchDocuments).toHaveBeenCalled()
  })

  it('rejects invalid credentials with an inline error', async () => {
    agentApi.fetchTickets.mockRejectedValue(authError(401))
    const wrapper = await mountPage()

    await signIn(wrapper)

    expect(wrapper.text()).toContain('Invalid admin credentials')
    expect(wrapper.find('[data-test="dropzone"]').exists()).toBe(false)
  })

  it('uploads a dropped .md file and shows a success toast', async () => {
    adminApi.uploadDocument.mockResolvedValue(document({ id: 2, chunkCount: 3 }))
    adminApi.fetchDocuments.mockResolvedValue([document(), document({ id: 2, title: 'FAQ', chunkCount: 3 })])
    const wrapper = await mountPage()
    await signIn(wrapper)

    const file = new File(['# FAQ'], 'faq.md', { type: 'text/markdown' })
    await wrapper
      .find('[data-test="dropzone"]')
      .trigger('drop', { dataTransfer: { files: [file] } })
    await flushPromises()

    expect(adminApi.uploadDocument).toHaveBeenCalledWith(file, 'faq')
    expect(wrapper.text()).toContain('indexed')
    expect(wrapper.text()).toContain('FAQ')
  })

  it('shows a spinner while embedding is in progress', async () => {
    let resolveUpload
    adminApi.uploadDocument.mockReturnValue(
      new Promise((resolve) => (resolveUpload = resolve)),
    )
    const wrapper = await mountPage()
    await signIn(wrapper)

    const file = new File(['body'], 'guide.txt')
    const dropPromise = wrapper
      .find('[data-test="dropzone"]')
      .trigger('drop', { dataTransfer: { files: [file] } })
    await flushPromises()

    expect(wrapper.text()).toContain('generating vector embeddings')

    resolveUpload(document({ id: 2 }))
    await flushPromises()
    await dropPromise
    expect(wrapper.text()).not.toContain('generating vector embeddings')
  })

  it('rejects unsupported file types with an error toast and no API call', async () => {
    const wrapper = await mountPage()
    await signIn(wrapper)

    const file = new File(['PDF!'], 'manual.pdf', { type: 'application/pdf' })
    await wrapper
      .find('[data-test="dropzone"]')
      .trigger('drop', { dataTransfer: { files: [file] } })
    await flushPromises()

    expect(adminApi.uploadDocument).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('not supported')
  })

  it('shows an error toast when indexing fails', async () => {
    const err = new Error('Could not generate embeddings')
    err.response = { status: 400, data: { message: 'Could not generate embeddings (is OPENAI_API_KEY set?)' } }
    adminApi.uploadDocument.mockRejectedValue(err)
    const wrapper = await mountPage()
    await signIn(wrapper)

    const file = new File(['x'], 'x.txt')
    await wrapper
      .find('[data-test="dropzone"]')
      .trigger('drop', { dataTransfer: { files: [file] } })
    await flushPromises()

    expect(wrapper.text()).toContain('OPENAI_API_KEY')
  })

  it('deletes a document and shows a success toast', async () => {
    adminApi.fetchDocuments.mockResolvedValue([document()])
    adminApi.deleteDocument.mockResolvedValue(undefined)
    const wrapper = await mountPage()
    await signIn(wrapper)

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Delete')
      .trigger('click')
    await flushPromises()

    expect(adminApi.deleteDocument).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('removed from the knowledge base')
  })

  it('shows an error toast when deletion fails', async () => {
    adminApi.fetchDocuments.mockResolvedValue([document()])
    const err = new Error('offline')
    err.response = { status: 500, data: { message: 'Database unavailable' } }
    adminApi.deleteDocument.mockRejectedValue(err)
    const wrapper = await mountPage()
    await signIn(wrapper)

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Delete')
      .trigger('click')
    await flushPromises()

    // The backend error message surfaces in the toast
    expect(wrapper.text()).toContain('Database unavailable')
  })

  it('returns to the sign-in gate when a request hits a 401', async () => {
    adminApi.uploadDocument.mockRejectedValue(authError(401))
    const wrapper = await mountPage()
    await signIn(wrapper)

    const file = new File(['x'], 'x.txt')
    await wrapper
      .find('[data-test="dropzone"]')
      .trigger('drop', { dataTransfer: { files: [file] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Admin sign in')
    expect(wrapper.text()).toContain('Session expired')
  })
})
