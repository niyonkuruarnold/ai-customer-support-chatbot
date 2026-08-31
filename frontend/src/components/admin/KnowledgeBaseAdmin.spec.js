import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

// Mock global fetch for the /api/users/me call in the agent store login
const originalFetch = global.fetch

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

/** Switch to the Upload Files tab so the dropzone is visible. */
async function switchToUploadTab(wrapper) {
  await wrapper.find('[data-test="upload-tab-file"]').trigger('click')
  await flushPromises()
}

describe('KnowledgeBaseAdmin', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    useToasts().clear()
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ id: 1, email: 'admin', role: 'ADMIN' }),
    })
    adminApi.fetchDocuments.mockResolvedValue([])
    adminApi.fetchChunks.mockResolvedValue([])
    agentApi.fetchTickets.mockResolvedValue([])
  })

  afterEach(() => {
    global.fetch = originalFetch
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
    expect(wrapper.find('[data-test="paste-section"]').exists()).toBe(false)
  })

  it('uploads a dropped .md file and shows a success toast', async () => {
    adminApi.uploadDocument.mockResolvedValue(document({ id: 2, chunkCount: 3 }))
    adminApi.fetchDocuments.mockResolvedValue([document(), document({ id: 2, title: 'FAQ', chunkCount: 3 })])
    const wrapper = await mountPage()
    await signIn(wrapper)
    await switchToUploadTab(wrapper)

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
    await switchToUploadTab(wrapper)

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
    await switchToUploadTab(wrapper)

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
    await switchToUploadTab(wrapper)

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
    await switchToUploadTab(wrapper)

    const file = new File(['x'], 'x.txt')
    await wrapper
      .find('[data-test="dropzone"]')
      .trigger('drop', { dataTransfer: { files: [file] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Admin sign in')
    expect(wrapper.text()).toContain('Session expired')
  })

  // ─── Paste Content tab ──────────────────────────────────────────────

  it('shows the paste content tab by default after sign-in', async () => {
    const wrapper = await mountPage()
    await signIn(wrapper)

    expect(wrapper.find('[data-test="paste-section"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="paste-textarea"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="paste-submit"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="dropzone"]').exists()).toBe(false)
  })

  it('switches between paste and upload tabs', async () => {
    const wrapper = await mountPage()
    await signIn(wrapper)

    // Default: paste tab
    expect(wrapper.find('[data-test="paste-section"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="dropzone"]').exists()).toBe(false)

    // Switch to upload tab
    await wrapper.find('[data-test="upload-tab-file"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="dropzone"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="paste-section"]').exists()).toBe(false)

    // Switch back to paste tab
    await wrapper.find('[data-test="upload-tab-paste"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="paste-section"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="dropzone"]').exists()).toBe(false)
  })

  it('disables Save & Index button when textarea is empty', async () => {
    const wrapper = await mountPage()
    await signIn(wrapper)

    const btn = wrapper.find('[data-test="paste-submit"]')
    expect(btn.attributes('disabled')).toBeDefined()

    await wrapper.find('[data-test="paste-textarea"]').setValue('Some content')
    await flushPromises()
    expect(btn.attributes('disabled')).toBeUndefined()
  })

  it('sends pasted content as a Blob file via uploadFile', async () => {
    adminApi.uploadDocument.mockResolvedValue(document({ id: 2, chunkCount: 1, title: 'My Policies' }))
    adminApi.fetchDocuments.mockResolvedValue([document(), document({ id: 2, title: 'My Policies' })])
    const wrapper = await mountPage()
    await signIn(wrapper)

    await wrapper.find('[data-test="paste-title"]').setValue('My Policies')
    await wrapper.find('[data-test="paste-textarea"]').setValue('# Policy\nAll employees must…')
    await wrapper.find('[data-test="paste-form"]').trigger('submit')
    await flushPromises()

    // uploadDocument should have been called with a File whose name is derived from the title
    expect(adminApi.uploadDocument).toHaveBeenCalledTimes(1)
    const [fileArg, titleArg] = adminApi.uploadDocument.mock.calls[0]
    expect(titleArg).toBe('My Policies')
    expect(fileArg).toBeInstanceOf(File)
    expect(fileArg.name).toBe('company_policies.md')
    expect(fileArg.type).toBe('text/markdown')

    expect(wrapper.text()).toContain('indexed')
    expect(wrapper.text()).toContain('My Policies')
  })

  it('shows an error when pasted content is empty on submit', async () => {
    const wrapper = await mountPage()
    await signIn(wrapper)

    await wrapper.find('[data-test="paste-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Paste some content first')
    expect(adminApi.uploadDocument).not.toHaveBeenCalled()
  })

  it('clears textarea and title after successful paste index', async () => {
    adminApi.uploadDocument.mockResolvedValue(document({ id: 2 }))
    adminApi.fetchDocuments.mockResolvedValue([document(), document({ id: 2 })])
    const wrapper = await mountPage()
    await signIn(wrapper)

    await wrapper.find('[data-test="paste-title"]').setValue('Title')
    await wrapper.find('[data-test="paste-textarea"]').setValue('Content')
    await wrapper.find('[data-test="paste-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-test="paste-textarea"]').element.value).toBe('')
    expect(wrapper.find('[data-test="paste-title"]').element.value).toBe('')
  })
})
