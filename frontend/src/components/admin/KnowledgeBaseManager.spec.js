import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import KnowledgeBaseManager from './KnowledgeBaseManager.vue'
import { useKnowledgeBaseStore } from '../../stores/knowledgeBase'
import { useAgentStore } from '../../stores/agent'
import * as adminApi from '../../api/admin'
import * as agentApi from '../../api/agent'

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

function chunk(overrides = {}) {
  return {
    id: 10,
    documentId: 1,
    title: 'Shipping policy',
    sourceType: 'TEXT',
    chunkIndex: 0,
    content: 'Orders ship within 24 hours.',
    createdAt: '2026-08-16T09:00:01',
    ...overrides,
  }
}

async function mountManager() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(KnowledgeBaseManager, { global: { plugins: [pinia] } })
  await flushPromises()
  return wrapper
}

describe('KnowledgeBaseManager', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    adminApi.fetchDocuments.mockResolvedValue([])
    adminApi.fetchChunks.mockResolvedValue([])
  })

  it('renders the upload zone, paste form, and empty list', async () => {
    const wrapper = await mountManager()

    expect(wrapper.text()).toContain('Knowledge Base')
    expect(wrapper.text()).toContain('Drop support files here')
    expect(wrapper.text()).toContain('Paste FAQ / support text')
    expect(wrapper.text()).toContain('No documents indexed yet')
    expect(
      wrapper.find('input[type="file"]').attributes('accept'),
    ).toContain('.pdf')
  })

  it('indexes pasted FAQ text and clears the form on success', async () => {
    adminApi.addTextDocument.mockResolvedValue(document())
    adminApi.fetchDocuments.mockResolvedValue([document()])
    adminApi.fetchChunks.mockResolvedValue([chunk()])

    const wrapper = await mountManager()
    await wrapper.find('input[type="text"]').setValue('Shipping policy')
    await wrapper.find('textarea').setValue('Orders ship within 24 hours.')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(adminApi.addTextDocument).toHaveBeenCalledWith(
      'Shipping policy',
      'Orders ship within 24 hours.',
    )
    expect(wrapper.find('input[type="text"]').element.value).toBe('')
    expect(wrapper.find('textarea').element.value).toBe('')
    expect(wrapper.text()).toContain('Shipping policy')
  })

  it('does not submit when the paste form is incomplete', async () => {
    const wrapper = await mountManager()

    await wrapper.find('textarea').setValue('Body without a title')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(adminApi.addTextDocument).not.toHaveBeenCalled()
  })

  it('uploads a dropped file and lists the new document', async () => {
    adminApi.uploadDocument.mockResolvedValue(document({ id: 2, title: 'FAQ' }))
    adminApi.fetchDocuments.mockResolvedValue([
      document({ id: 2, title: 'FAQ', sourceType: 'MARKDOWN', chunkCount: 3 }),
    ])
    adminApi.fetchChunks.mockResolvedValue([])

    const wrapper = await mountManager()
    const file = new File(['# How do I return an item?'], 'faq.md', {
      type: 'text/markdown',
    })
    await wrapper
      .find('section')
      .trigger('drop', { dataTransfer: { files: [file] } })
    await flushPromises()

    expect(adminApi.uploadDocument).toHaveBeenCalledWith(file, 'faq')
    expect(wrapper.text()).toContain('FAQ')
  })

  it('expands a document to preview its indexed chunks', async () => {
    adminApi.fetchDocuments.mockResolvedValue([document()])
    adminApi.fetchChunks.mockResolvedValue([chunk()])

    const wrapper = await mountManager()
    expect(wrapper.text()).not.toContain('Orders ship within 24 hours')

    await wrapper.find('li button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Chunk 1')
    expect(wrapper.text()).toContain('Orders ship within 24 hours')
  })

  it('deletes a document from the knowledge base', async () => {
    adminApi.fetchDocuments.mockResolvedValue([document()])
    adminApi.fetchChunks.mockResolvedValue([chunk()])
    adminApi.deleteDocument.mockResolvedValue(undefined)

    const wrapper = await mountManager()
    const deleteButton = wrapper
      .findAll('button')
      .find((b) => b.text().includes('Delete'))

    await deleteButton.trigger('click')
    await flushPromises()

    expect(adminApi.deleteDocument).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('No documents indexed yet')
  })

  it('shows a sign-in prompt when the session expires (401)', async () => {
    adminApi.fetchDocuments.mockRejectedValueOnce({ status: 401 })

    const wrapper = await mountManager()

    expect(wrapper.text()).toContain('sign in again')

    const agentStore = useAgentStore()
    agentStore.authenticated = true
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('Sign in again'))
      .trigger('click')

    expect(agentStore.authenticated).toBe(false)
    expect(agentApi.clearAgentAuth).toHaveBeenCalled()
  })
})
