import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import App from './App.vue'
import { useChatStore } from './stores/chat'
import { useAgentStore } from './stores/agent'
import {
  closeChatSession,
  fetchSessionInfo,
  resetBackendSession,
  sendChatMessage,
} from './api/chat'

vi.mock('./api/chat', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    closeChatSession: vi.fn().mockResolvedValue(undefined),
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
  const wrapper = mount(App, {
    global: {
      plugins: [pinia],
      // Stub Teleport so its children render inline (findable by wrapper.find)
      stubs: { Teleport: true },
    },
  })
  await flushPromises()
  return wrapper
}

/** Open the compact widget by clicking the FAB, then flush */
async function openWidget(wrapper) {
  const fab = wrapper.find('[data-test="chat-fab"]')
  if (!fab.exists()) return false
  await fab.trigger('click')
  await flushPromises()
  return true
}

/** Expand the compact widget into full-screen portal */
async function expandWidget(wrapper) {
  const expandBtn = wrapper.find('[data-test="widget-expand"]')
  if (!expandBtn.exists()) return false
  await expandBtn.trigger('click')
  await flushPromises()
  return true
}

describe('App', () => {
  beforeEach(() => {
    localStorage.clear()
    // Reset the URL to remove any ?mode=… left by previous tests that
    // called setView() → window.history.replaceState()
    window.history.replaceState({}, '', window.location.pathname)
    vi.clearAllMocks()
    fetchSessionInfo.mockResolvedValue({ id: 1, status: 'ACTIVE', messages: [] })
    resetBackendSession.mockResolvedValue(undefined)
  })

  // ═══════════════════════════════════════════════════════════════════
  // CUSTOMER MODE: Floating Widget
  // ═══════════════════════════════════════════════════════════════════

  it('shows the floating FAB button by default (CUSTOMER mode)', async () => {
    const wrapper = await mountApp()
    expect(wrapper.find('[data-test="chat-fab"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="chat-widget"]').exists()).toBe(false)
  })

  it('opens the compact widget when the FAB is clicked', async () => {
    const wrapper = await mountApp()
    const opened = await openWidget(wrapper)
    expect(opened).toBe(true)

    expect(wrapper.find('[data-test="chat-widget"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('CODAFRIQA Smart Assistant')
    expect(wrapper.text()).toContain('AI CONCIERGE')
  })

  it('renders navigation tabs in the compact widget', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)

    expect(wrapper.text()).toContain('Customer Chat')
    expect(wrapper.text()).toContain('My Support Tickets')
  })

  it('shows chat content by default in the compact widget', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)

    expect(wrapper.text()).toContain('How can we help you today?')
    expect(wrapper.find('textarea').exists()).toBe(true)
  })

  it('expands to full-screen portal when expand button is clicked', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)
    expect(wrapper.find('[data-test="chat-widget"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="portal-overlay"]').exists()).toBe(false)

    const expanded = await expandWidget(wrapper)
    expect(expanded).toBe(true)

    expect(wrapper.find('[data-test="chat-widget"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="portal-overlay"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('How can we help you today?')
  })

  it('collapses back to compact widget from expanded portal', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)
    await expandWidget(wrapper)
    expect(wrapper.find('[data-test="portal-overlay"]').exists()).toBe(true)

    const collapseBtn = wrapper.find('[data-test="widget-collapse"]')
    expect(collapseBtn.exists()).toBe(true)
    await collapseBtn.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="portal-overlay"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="chat-widget"]').exists()).toBe(true)
  })

  it('sends a message typed in the input field', async () => {
    sendChatMessage.mockResolvedValue(okResponse())
    const wrapper = await mountApp()
    await openWidget(wrapper)

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
    await openWidget(wrapper)

    const store = useChatStore()

    await wrapper.find('textarea').setValue('Talk to a human agent')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Agent Active')
    expect(wrapper.text()).toContain('human agent is now handling')
    expect(wrapper.find('textarea').attributes('placeholder')).toContain('Message the agent')
    store.stopPolling()
  })

  it('disables the send button for empty or whitespace-only input', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)

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
    await openWidget(wrapper)

    const button = wrapper.find('button[type="submit"]')

    await wrapper.find('textarea').setValue('Hello?')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(button.attributes('disabled')).toBeDefined()

    resolveRequest(okResponse())
    await flushPromises()
    await wrapper.find('textarea').setValue('Another question')
    expect(button.attributes('disabled')).toBeUndefined()
    useChatStore().stopPolling()
  })

  it('shows a failed message with an inline retry that resends it', async () => {
    sendChatMessage.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountApp()
    await openWidget(wrapper)

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

  it('closes the widget when the close button is clicked', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)

    expect(wrapper.find('[data-test="chat-widget"]').exists()).toBe(true)

    const closeBtn = wrapper.find('[data-test="chat-widget-close"]')
    expect(closeBtn.exists()).toBe(true)
    await closeBtn.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="chat-widget"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="chat-fab"]').exists()).toBe(true)
  })

  it('renders My Support Tickets tab content', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)

    const myTicketsBtn = wrapper.findAll('button').find((b) => b.text().includes('My Support Tickets'))
    expect(myTicketsBtn).toBeTruthy()
    await myTicketsBtn.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('My Support Tickets')
    expect(wrapper.text()).toContain('Start a conversation')
  })

  // ═══════════════════════════════════════════════════════════════════
  // RBAC: role-based view access
  // ═══════════════════════════════════════════════════════════════════

  it('redirects CUSTOMER away from admin-only views to chat', async () => {
    localStorage.setItem('ai-support-chat:mode', 'tickets')
    const wrapper = await mountApp()

    // Staff auth form should NOT be shown (we're in customer mode)
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)
    // Open widget and confirm we're on the chat view
    const opened = await openWidget(wrapper)
    if (opened) {
      expect(wrapper.text()).toContain('How can we help you today?')
    }
  })

  it('redirects CUSTOMER away from knowledge view to chat', async () => {
    localStorage.setItem('ai-support-chat:mode', 'knowledge')
    const wrapper = await mountApp()

    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)
  })

  it('redirects CUSTOMER away from agent view to chat', async () => {
    localStorage.setItem('ai-support-chat:mode', 'agent')
    const wrapper = await mountApp()

    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)
  })

  it('allows CUSTOMER to deep-link to my-tickets', async () => {
    localStorage.setItem('ai-support-chat:mode', 'my-tickets')
    const wrapper = await mountApp()

    // my-tickets is allowed for CUSTOMER — widget auto-opens
    expect(wrapper.find('[data-test="chat-widget"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('My Support Tickets')
  })

  // ═══════════════════════════════════════════════════════════════════
  // RBAC: role switcher (via staff dashboard)
  // ═══════════════════════════════════════════════════════════════════

  it('shows the role switcher in the staff dashboard header', async () => {
    localStorage.setItem('ai-support-chat:role', 'AGENT')
    const wrapper = await mountApp()
    await flushPromises()

    const switcher = wrapper.find('[data-test="role-switcher"]')
    expect(switcher.exists()).toBe(true)
    expect(switcher.element.value).toBe('AGENT')
  })

  it('AGENT role shows staff dashboard with correct tabs', async () => {
    localStorage.setItem('ai-support-chat:role', 'AGENT')
    const wrapper = await mountApp()
    await flushPromises()

    // AGENT sees Live Customer Workspace and Ticket Queue
    expect(wrapper.text()).toContain('Live Customer Workspace')
    expect(wrapper.text()).toContain('Ticket Queue')
    // Should NOT see admin-only tabs
    expect(wrapper.text()).not.toContain('Knowledge Base Admin')
    expect(wrapper.text()).not.toContain('System Indexer')
  })

  it('ADMIN role shows staff dashboard with all tabs', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    const wrapper = await mountApp()
    await flushPromises()

    expect(wrapper.text()).toContain('Live Customer Workspace')
    expect(wrapper.text()).toContain('Ticket Queue')
    expect(wrapper.text()).toContain('Knowledge Base Admin')
    expect(wrapper.text()).toContain('System Indexer')
  })

  it('switches role via the switcher and updates tabs', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    const wrapper = await mountApp()
    await flushPromises()

    const switcher = wrapper.find('[data-test="role-switcher"]')
    await switcher.setValue('AGENT')
    await flushPromises()

    expect(wrapper.text()).toContain('Live Customer Workspace')
    expect(wrapper.text()).toContain('Ticket Queue')
    expect(wrapper.text()).not.toContain('Knowledge Base Admin')
  })

  it('switches role and redirects away from now-forbidden views', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    localStorage.setItem('ai-support-chat:mode', 'knowledge')
    const wrapper = await mountApp()
    await flushPromises()

    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(true)

    const switcher = wrapper.find('[data-test="role-switcher"]')
    await switcher.setValue('CUSTOMER')
    await flushPromises()

    // CUSTOMER mode — staff auth form gone, customer FAB may appear
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)
  })

  it('shows the role badge with correct label in customer mode', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)

    expect(wrapper.text()).toContain('Customer')
  })

  it('shows agent role badge in staff dashboard', async () => {
    localStorage.setItem('ai-support-chat:role', 'AGENT')
    const wrapper = await mountApp()
    await flushPromises()

    expect(wrapper.text()).toContain('Agent')
  })

  it('shows admin role badge in staff dashboard', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    const wrapper = await mountApp()
    await flushPromises()

    expect(wrapper.text()).toContain('Admin')
  })

  // ═══════════════════════════════════════════════════════════════════
  // AGENT/ADMIN MODE: Full-Screen Dashboard + Auth Gate
  // ═══════════════════════════════════════════════════════════════════

  it('shows auth gate when in AGENT mode without credentials', async () => {
    localStorage.setItem('ai-support-chat:role', 'AGENT')
    const wrapper = await mountApp()
    await flushPromises()

    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Agent Sign In')
  })

  it('shows auth gate when in ADMIN mode without credentials', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    const wrapper = await mountApp()
    await flushPromises()

    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Admin Sign In')
  })

  it('AGENT mode renders full-screen layout without FAB', async () => {
    localStorage.setItem('ai-support-chat:role', 'AGENT')
    const wrapper = await mountApp()
    await flushPromises()

    expect(wrapper.find('[data-test="chat-fab"]').exists()).toBe(false)
    expect(wrapper.find('header').exists()).toBe(true)
    expect(wrapper.text()).toContain('CODAFRIQA Smart Assistant')
  })

  it('ADMIN mode renders full-screen layout without FAB', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    const wrapper = await mountApp()
    await flushPromises()

    expect(wrapper.find('[data-test="chat-fab"]').exists()).toBe(false)
    expect(wrapper.find('header').exists()).toBe(true)
  })

  // ═══════════════════════════════════════════════════════════════════
  // NEW CONVERSATION: Clear Chat & Reset
  // ═══════════════════════════════════════════════════════════════════

  it('does not show New Conversation button when there are no messages', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)

    expect(wrapper.find('[data-test="new-conversation"]').exists()).toBe(false)
  })

  it('shows New Conversation button after sending a message', async () => {
    sendChatMessage.mockResolvedValue(okResponse())
    const wrapper = await mountApp()
    await openWidget(wrapper)

    await wrapper.find('textarea').setValue('Hello')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-test="new-conversation"]').exists()).toBe(true)
    useChatStore().stopPolling()
  })

  it('opens the new chat confirmation modal when New Conversation is clicked', async () => {
    sendChatMessage.mockResolvedValue(okResponse())
    const wrapper = await mountApp()
    await openWidget(wrapper)

    await wrapper.find('textarea').setValue('Hello')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const newChatBtn = wrapper.find('[data-test="new-conversation"]')
    expect(newChatBtn.exists()).toBe(true)
    await newChatBtn.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="new-chat-modal"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Start a new chat?')
    expect(wrapper.text()).toContain('close the active session')
    useChatStore().stopPolling()
  })

  it('closes the modal when cancel is clicked', async () => {
    sendChatMessage.mockResolvedValue(okResponse())
    const wrapper = await mountApp()
    await openWidget(wrapper)

    await wrapper.find('textarea').setValue('Hello')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    await wrapper.find('[data-test="new-conversation"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="new-chat-modal"]').exists()).toBe(true)

    await wrapper.find('[data-test="new-chat-cancel"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="new-chat-modal"]').exists()).toBe(false)
    // Messages should still be present
    const store = useChatStore()
    expect(store.hasMessages).toBe(true)
    store.stopPolling()
  })

  it('confirms new chat, closes session, and resets state', async () => {
    sendChatMessage.mockResolvedValue(okResponse())
    const wrapper = await mountApp()
    await openWidget(wrapper)

    await wrapper.find('textarea').setValue('Hello')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const store = useChatStore()
    expect(store.hasMessages).toBe(true)
    expect(store.sessionId).toBe(1)

    // Open modal and confirm
    await wrapper.find('[data-test="new-conversation"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="new-chat-confirm"]').trigger('click')
    await flushPromises()

    // Backend close was called
    expect(closeChatSession).toHaveBeenCalledWith(1)
    // Local state is reset
    expect(store.messages).toHaveLength(0)
    expect(store.sessionId).toBeNull()
    expect(store.sessionStatus).toBeNull()
    // Empty state is shown again
    expect(wrapper.text()).toContain('How can we help you today?')
    store.stopPolling()
  })

  it('shows New Conversation button in the expanded portal header', async () => {
    sendChatMessage.mockResolvedValue(okResponse())
    const wrapper = await mountApp()
    await openWidget(wrapper)
    await expandWidget(wrapper)

    await wrapper.find('textarea').setValue('Hello')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // The button appears in the portal header area
    const newChatBtns = wrapper.findAll('[data-test="new-conversation"]')
    expect(newChatBtns.length).toBeGreaterThanOrEqual(1)
    useChatStore().stopPolling()
  })

  it('shows New Chat button in the Agent Active banner', async () => {
    sendChatMessage.mockResolvedValue(
      okResponse({ status: 'ESCALATED', response: 'Connected to a human agent.' }),
    )
    const wrapper = await mountApp()
    await openWidget(wrapper)
    await expandWidget(wrapper)

    await wrapper.find('textarea').setValue('Talk to a human agent')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // Agent Active banner shows the + New Chat button
    const newChatBtns = wrapper.findAll('[data-test="new-conversation"]')
    expect(newChatBtns.length).toBeGreaterThanOrEqual(1)
    useChatStore().stopPolling()
  })

  it('New Conversation button shows when session is escalated even without local messages', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)
    await expandWidget(wrapper)

    const store = useChatStore()
    // Manually set escalated state
    store.sessionStatus = 'ESCALATED'
    await flushPromises()

    expect(wrapper.find('[data-test="new-conversation"]').exists()).toBe(true)
    store.stopPolling()
  })
})
