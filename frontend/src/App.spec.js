import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import App from './App.vue'
import { useChatStore } from './stores/chat'
import {
  fetchSessionInfo,
  resetBackendSession,
  sendChatMessage,
} from './api/chat'

vi.mock('./api/chat', async (importOriginal) => {
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
    response: 'How can I help?',
    sessionId: 1,
    status: 'ACTIVE',
    ...overrides,
  }
}

async function mountApp() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(App, { global: { plugins: [pinia] } })
  await flushPromises()
  return wrapper
}

describe('App', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    fetchSessionInfo.mockResolvedValue({ id: 1, status: 'ACTIVE', messages: [] })
    resetBackendSession.mockResolvedValue(undefined)
  })

  it('renders the chat shell', async () => {
    const wrapper = await mountApp()
    expect(wrapper.text()).toContain('AI Customer Support')
    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(wrapper.find('button[type="submit"]').exists()).toBe(true)
  })

  it('sends a message typed in the input field', async () => {
    sendChatMessage.mockResolvedValue(okResponse())
    const wrapper = await mountApp()
    const store = useChatStore()

    await wrapper.find('textarea').setValue('I need help')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(sendChatMessage).toHaveBeenCalledWith('I need help', null)
    expect(store.messages).toHaveLength(2)
    expect(store.messages[0].content).toBe('I need help')
    expect(store.messages[1].content).toBe('How can I help?')
    expect(store.sessionId).toBe(1)
    expect(wrapper.find('textarea').element.value).toBe('')
    store.stopPolling()
  })

  it('switches to Agent Active mode when the session is escalated', async () => {
    sendChatMessage.mockResolvedValue(
      okResponse({ status: 'ESCALATED', response: 'Connected to a human agent.' }),
    )
    const wrapper = await mountApp()
    const store = useChatStore()

    await wrapper.find('textarea').setValue('Talk to a human agent')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // UI switches from "AI Support" to "Agent Active" mode
    expect(wrapper.text()).toContain('Agent Active')
    expect(wrapper.text()).toContain('AI assistant is paused')
    expect(wrapper.find('textarea').attributes('placeholder')).toContain('Message the agent')
    store.stopPolling()
  })

  it('disables the send button for empty or whitespace-only input', async () => {
    const wrapper = await mountApp()
    const button = wrapper.find('button[type="submit"]')

    expect(button.attributes('disabled')).toBeDefined()

    await wrapper.find('textarea').setValue('   ')
    expect(button.attributes('disabled')).toBeDefined()

    await wrapper.find('textarea').setValue('A valid question')
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('keeps the send button disabled while a request is in flight', async () => {
    let resolveRequest
    sendChatMessage.mockReturnValue(
      new Promise((resolve) => (resolveRequest = resolve)),
    )
    const wrapper = await mountApp()
    const button = wrapper.find('button[type="submit"]')

    await wrapper.find('textarea').setValue('Hello?')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // Disabled while the request is in flight (input is non-empty)
    expect(button.attributes('disabled')).toBeDefined()

    // Once it resolves, the input is cleared, so re-type to prove loading no
    // longer blocks sending
    resolveRequest(okResponse())
    await flushPromises()
    await wrapper.find('textarea').setValue('Another question')
    expect(button.attributes('disabled')).toBeUndefined()
    useChatStore().stopPolling()
  })

  it('shows a failed message with an inline retry that resends it', async () => {
    sendChatMessage.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountApp()
    const store = useChatStore()

    await wrapper.find('textarea').setValue('Hello?')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const failed = store.messages[0]
    expect(failed.status).toBe('failed')
    expect(wrapper.find('[data-test="retry"]').exists()).toBe(true)

    sendChatMessage.mockResolvedValueOnce(okResponse({ response: 'Back online!' }))
    await wrapper.find('[data-test="retry"]').trigger('click')
    await flushPromises()

    expect(store.messages).toHaveLength(2)
    expect(store.messages[1].content).toBe('Back online!')
    store.stopPolling()
  })

  it('clears the conversation from the header with a two-step confirm', async () => {
    sendChatMessage.mockResolvedValue(okResponse())
    const wrapper = await mountApp()
    const store = useChatStore()

    await wrapper.find('textarea').setValue('Hello')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(store.messages).toHaveLength(2)
    store.stopPolling()

    const clearButton = wrapper
      .findAll('button')
      .find((b) => b.text().includes('Clear chat'))

    expect(clearButton).toBeTruthy()

    // First click arms the confirm state; the button changes label
    await clearButton.trigger('click')
    expect(clearButton.text()).toContain('Confirm')

    // Second click actually clears
    await clearButton.trigger('click')
    expect(store.messages).toHaveLength(0)
    expect(store.sessionId).toBeNull()
    expect(wrapper.text()).toContain('How can we help you today?')
  })

  it('renders the agent workspace when agent mode is enabled', async () => {
    localStorage.setItem('ai-support-chat:mode', 'agent')

    const wrapper = await mountApp()

    expect(wrapper.text()).toContain('Agent Workspace')
    expect(wrapper.text()).toContain('Agent sign in')
  })

  it('renders the knowledge base admin when knowledge mode is enabled', async () => {
    localStorage.setItem('ai-support-chat:mode', 'knowledge')

    const wrapper = await mountApp()

    expect(wrapper.text()).toContain('Knowledge Base Admin')
    expect(wrapper.text()).toContain('Admin sign in')
  })

  it('renders the ticket dashboard when tickets mode is enabled', async () => {
    localStorage.setItem('ai-support-chat:mode', 'tickets')

    const wrapper = await mountApp()

    expect(wrapper.text()).toContain('Ticket Dashboard')
    expect(wrapper.text()).toContain('Admin sign in')
  })

  it('switches to the ticket dashboard from the chat header', async () => {
    const wrapper = await mountApp()

    const ticketsButton = wrapper
      .findAll('button')
      .find((b) => b.text().includes('Tickets'))

    expect(ticketsButton).toBeTruthy()
    await ticketsButton.trigger('click')

    expect(wrapper.text()).toContain('Ticket Dashboard')
  })
})
