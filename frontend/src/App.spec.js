import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import App from './App.vue'
import { useChatStore } from './stores/chat'
import { useAgentStore } from './stores/agent'
import { fetchSystemHealth } from './api/health'
import {
  closeChatSession,
  fetchSessionInfo,
  resetBackendSession,
  sendChatMessage,
} from './api/chat'
import { attachBearerToken } from './api/authToken'

vi.mock('./api/chat', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    closeChatSession: vi.fn().mockResolvedValue(undefined),
    fetchSessionInfo: vi.fn(),
    fetchSuggestedQuestions: vi.fn().mockResolvedValue([]),
    resetBackendSession: vi.fn(),
    sendChatMessage: vi.fn(),
  }
})

vi.mock('./api/agent', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    fetchTickets: vi.fn().mockResolvedValue([]),
    setAgentAuth: vi.fn(),
  }
})

vi.mock('./api/health', () => ({
  fetchSystemHealth: vi.fn(),
}))

// The staff dashboards fetch data on mount. Against a live backend these
// requests answer 401 (no credentials in tests), which flips
// `agentStore.authenticated` mid-navigation and unmounts the dashboard.
// Stub the mount-time endpoints so view switching is deterministic.
vi.mock('./api/admin', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    fetchTickets: vi.fn().mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      last: true,
    }),
    fetchDocuments: vi.fn().mockResolvedValue([]),
    fetchChunks: vi.fn().mockResolvedValue([]),
    getAuditLogsV1: vi.fn().mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
    }),
    getFilteredAuditLogsV1: vi.fn().mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
    }),
    getOperationalMetrics: vi.fn().mockResolvedValue({}),
    getDailyTrend: vi.fn().mockResolvedValue([]),
  }
})

vi.mock('./api/maintenance', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    getAllTools: vi.fn().mockResolvedValue([]),
    getToolsByOwner: vi.fn().mockResolvedValue([]),
  }
})

// The Live Customer Workspace opens a STOMP/SockJS connection on mount.
// Keep it stubbed here — these tests cover navbar state, not websockets
// (useWebSocket has its own spec).
vi.mock('./composables/useWebSocket', async () => {
  const { ref } = await import('vue')
  return {
    useWebSocket: () => ({
      isConnected: ref(false),
      isConnecting: ref(false),
      connectionQuality: ref('good'),
      error: ref(null),
      reconnectAttempts: ref(0),
      lastConnectedAt: ref(null),
      connect: vi.fn(),
      disconnect: vi.fn(),
      subscribe: vi.fn(() => 'sub-test'),
      unsubscribe: vi.fn(),
      send: vi.fn(),
      sendChatMessage: vi.fn(),
      subscribeToSession: vi.fn(),
      subscribeToAgentChannel: vi.fn(),
    }),
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

/** Open the navbar account pill and pick an option from its menu. */
async function selectAccount(wrapper, email) {
  await wrapper.find('[data-test="account-switcher"]').trigger('click')
  await wrapper.find(`[data-test="account-option"][data-value="${email}"]`).trigger('click')
  await flushPromises()
}

/** Switch accounts via the dropdown, then complete the password prompt. */
async function switchAccountAndSignIn(wrapper, email, password = 'Password123!') {
  await selectAccount(wrapper, email)
  await wrapper.find('input[autocomplete="current-password"]').setValue(password)
  await wrapper.find('[data-test="staff-auth-form"]').trigger('submit')
  await flushPromises()
}

/** Complete the staff sign-in modal (username is pre-filled by App.vue). */
async function completeSignIn(wrapper, password = 'Password123!') {
  await wrapper.find('input[autocomplete="current-password"]').setValue(password)
  await wrapper.find('[data-test="staff-auth-form"]').trigger('submit')
  await flushPromises()
}

describe('App', () => {
  beforeEach(() => {
    localStorage.clear()
    // Reset the URL to remove any ?mode=… left by previous tests that
    // called setView() → window.history.replaceState()
    window.history.replaceState({}, '', window.location.pathname)
    vi.clearAllMocks()
    let loginUsername = ''
    vi.stubGlobal('fetch', vi.fn(async (url, options = {}) => {
      if (String(url).endsWith('/auth/token')) {
        loginUsername = JSON.parse(options.body).username
        return { ok: true, json: async () => ({ accessToken: 'test-access-token' }) }
      }
      const role = loginUsername.startsWith('admin@') ? 'ADMIN' : loginUsername.startsWith('customer@') ? 'CUSTOMER' : 'AGENT'
      return { ok: true, json: async () => ({ id: 1, email: loginUsername, role }) }
    }))
    fetchSessionInfo.mockResolvedValue({ id: 1, status: 'ACTIVE', messages: [] })
    resetBackendSession.mockResolvedValue(undefined)
    fetchSystemHealth.mockResolvedValue({ status: 'UP', components: { database: { status: 'UP' } } })
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
  // Demo account switcher (via staff dashboard)
  // ═══════════════════════════════════════════════════════════════════

  it('shows a compact role pill and keeps emails inside the open menu', async () => {
    localStorage.setItem('ai-support-chat:role', 'AGENT')
    const wrapper = await mountApp()
    await flushPromises()

    // Pill: icon + clean label only — no email in the navbar header
    const pill = wrapper.find('[data-test="account-switcher"]')
    expect(pill.exists()).toBe(true)
    expect(pill.text()).toContain('🎧')
    expect(pill.text()).toContain('Agent')
    expect(pill.text()).not.toContain('@codafriqa.local')
    // Full email available as the pill's native tooltip
    expect(pill.attributes('title')).toBe('agent@codafriqa.local')

    // Menu: the three seeded accounts, email as secondary muted text
    await pill.trigger('click')
    expect(wrapper.find('[data-test="account-menu"]').exists()).toBe(true)
    const options = wrapper.findAll('[data-test="account-option"]')
    expect(options.map((option) => option.attributes('data-value'))).toEqual([
      'admin@codafriqa.local',
      'agent@codafriqa.local',
      'customer@codafriqa.local',
    ])
    expect(options[0].text()).toContain('Admin')
    expect(options[0].text()).toContain('admin@codafriqa.local')
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

  it('requires the agent password and opens the workspace once verified', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    const wrapper = await mountApp()
    await flushPromises()

    await selectAccount(wrapper, 'agent@codafriqa.local')

    // No silent login: the sign-in modal opens with the username pre-filled
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(true)
    expect(wrapper.find('input[autocomplete="username"]').element.value).toBe(
      'agent@codafriqa.local',
    )
    expect(
      globalThis.fetch.mock.calls.some(([url]) => String(url).endsWith('/auth/token')),
    ).toBe(false)

    await completeSignIn(wrapper)

    // The password went through the real token endpoint
    const tokenCall = globalThis.fetch.mock.calls.find(([url]) => String(url).endsWith('/auth/token'))
    expect(JSON.parse(tokenCall[1].body)).toEqual({
      username: 'agent@codafriqa.local',
      password: 'Password123!',
    })
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Live Customer Workspace')
    expect(wrapper.text()).toContain('Ticket Queue')
    expect(wrapper.text()).not.toContain('Knowledge Base Admin')
    expect(window.location.search).toContain('mode=agent')
    useAgentStore().stopPolling()
  })

  it('authenticates the customer account and routes to chat', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    localStorage.setItem('ai-support-chat:mode', 'knowledge')
    const wrapper = await mountApp()
    await flushPromises()

    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(true)

    await selectAccount(wrapper, 'customer@codafriqa.local')

    expect(window.location.search).not.toContain('mode=knowledge')
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="chat-fab"]').exists()).toBe(true)
  })

  it('authenticates the admin account after the password check and routes to Analytics', async () => {
    localStorage.setItem('ai-support-chat:role', 'AGENT')
    const wrapper = await mountApp()
    await flushPromises()

    await switchAccountAndSignIn(wrapper, 'admin@codafriqa.local')

    expect(wrapper.find('[data-test="account-switcher"]').text()).toContain('Admin')
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)
    expect(window.location.search).toContain('mode=analytics')
    expect(wrapper.text()).toContain('Service performance metrics and insights')
    useAgentStore().stopPolling()
  })

  it('switches from the customer widget to the Admin workspace after the password check', async () => {
    const wrapper = await mountApp()
    await openWidget(wrapper)
    await expandWidget(wrapper)

    await switchAccountAndSignIn(wrapper, 'admin@codafriqa.local')

    // Navigation guard inputs: role (Pinia + localStorage) + view mode (URL)
    expect(localStorage.getItem('ai-support-chat:role')).toBe('ADMIN')
    expect(useAgentStore().userRole).toBe('ADMIN')
    expect(useAgentStore().accessToken).toBeTruthy()
    expect(window.location.search).toContain('mode=analytics')

    // Top navigation switched to the staff workspace tabs
    expect(wrapper.text()).toContain('Live Customer Workspace')
    expect(wrapper.text()).toContain('Ticket Queue')
    expect(wrapper.text()).toContain('Analytics')
    expect(wrapper.text()).toContain('Audit Logs')
    expect(wrapper.text()).toContain('Knowledge Base Admin')
    expect(wrapper.text()).toContain('System Indexer')
    expect(wrapper.text()).not.toContain('My Support Tickets')

    // …and we are no longer in the customer chat UI
    expect(wrapper.find('[data-test="chat-fab"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)
    useAgentStore().stopPolling()
  })

  it('keeps the Admin workspace selected when the credential exchange fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')))
    const wrapper = await mountApp()
    await openWidget(wrapper)
    await expandWidget(wrapper)

    await selectAccount(wrapper, 'admin@codafriqa.local')

    // Never bounced back to Customer Chat
    expect(wrapper.find('[data-test="chat-fab"]').exists()).toBe(false)
    expect(localStorage.getItem('ai-support-chat:role')).toBe('ADMIN')
    expect(useAgentStore().userRole).toBe('ADMIN')
    expect(window.location.search).toContain('mode=analytics')

    // Staff tabs are reachable behind the sign-in modal
    expect(wrapper.text()).toContain('Live Customer Workspace')
    expect(wrapper.text()).toContain('Audit Logs')
    expect(wrapper.text()).toContain('System Indexer')

    // Submitting the password reports the failure on the modal itself
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(true)
    await completeSignIn(wrapper)
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Could not reach the backend')
    expect(useAgentStore().userRole).toBe('ADMIN')
  })

  it('shows the in-page sign-in card when a protected API answers 401', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    const wrapper = await mountApp()
    await flushPromises()

    // Sign in through the password prompt (username pre-filled for ADMIN)
    await completeSignIn(wrapper)

    // Session is live: dashboard rendered, no sign-in card
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)

    // A protected endpoint rejects the (expired) bearer token. The backend
    // replies with JSON and no WWW-Authenticate header, so the browser shows
    // no native Basic dialog — the app's own interceptor takes over.
    const client = axios.create()
    attachBearerToken(client)
    client.defaults.adapter = async () => {
      const error = new Error('Request failed with status code 401')
      error.response = { status: 401, data: {}, headers: {}, config: {} }
      throw error
    }
    await client.get('/agent/tickets').catch(() => {})
    await flushPromises()

    // Custom sign-in card replaces the workspace…
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Admin Sign In')
    expect(wrapper.text()).toContain('session has expired')
    // …without bouncing back to Customer Chat
    expect(wrapper.find('[data-test="chat-fab"]').exists()).toBe(false)
    expect(window.location.search).toContain('mode=analytics')

    useAgentStore().stopPolling()
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
    expect(wrapper.text()).toContain('System Status: Healthy')
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

  it('routes manual Admin sign-in to the Admin dashboard', async () => {
    localStorage.setItem('ai-support-chat:role', 'AGENT')
    const wrapper = await mountApp()

    await wrapper.find('input[autocomplete="username"]').setValue('admin@codafriqa.local')
    await wrapper.find('input[autocomplete="current-password"]').setValue('Password123!')
    await wrapper.find('[data-test="staff-auth-form"]').trigger('submit')
    await flushPromises()

    expect(useAgentStore().userRole).toBe('ADMIN')
    expect(window.location.search).toContain('mode=analytics')
    expect(wrapper.text()).toContain('Service performance metrics and insights')
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)
    useAgentStore().stopPolling()
  })

  it('routes manual Agent sign-in to the Agent workspace', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    const wrapper = await mountApp()

    await wrapper.find('input[autocomplete="username"]').setValue('agent@codafriqa.local')
    await wrapper.find('input[autocomplete="current-password"]').setValue('Password123!')
    await wrapper.find('[data-test="staff-auth-form"]').trigger('submit')
    await flushPromises()

    expect(useAgentStore().userRole).toBe('AGENT')
    expect(window.location.search).toContain('mode=agent')
    expect(wrapper.text()).toContain('Live Customer Workspace')
    expect(wrapper.text()).not.toContain('Knowledge Base Admin')
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(false)
    useAgentStore().stopPolling()
  })

  it('keeps the selected staff sign-in form visible after rejected credentials', async () => {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    const wrapper = await mountApp()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))

    await wrapper.find('input[autocomplete="username"]').setValue('wrong-user')
    await wrapper.find('input[autocomplete="current-password"]').setValue('wrong-password')
    await wrapper.find('[data-test="staff-auth-form"]').trigger('submit')
    await flushPromises()

    expect(useAgentStore().userRole).toBe('ADMIN')
    expect(wrapper.find('[data-test="staff-auth-form"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Admin Sign In')
    expect(wrapper.text()).toContain('Invalid credentials')
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

  // ═══════════════════════════════════════════════════════════════════
  // STAFF TOP NAVBAR: switching views without a page reload
  // ═══════════════════════════════════════════════════════════════════

  /** Mount the full-screen staff dashboard as an authenticated ADMIN. */
  async function mountStaffAdmin() {
    localStorage.setItem('ai-support-chat:role', 'ADMIN')
    const wrapper = await mountApp()
    await flushPromises()
    await completeSignIn(wrapper)
    return wrapper
  }

  function navButton(wrapper, label) {
    return wrapper
      .find('header nav')
      .findAll('button')
      .find((b) => b.text().includes(label))
  }

  async function clickNav(wrapper, label) {
    const btn = navButton(wrapper, label)
    expect(btn, `navbar button "${label}" should exist`).toBeTruthy()
    await btn.trigger('click')
    await flushPromises()
    return navButton(wrapper, label) // re-find: nodes may have been re-rendered
  }

  it('navigates to every ADMIN view from the Ticket Queue without a reload', async () => {
    const wrapper = await mountStaffAdmin()

    const tabs = [
      ['Analytics', 'analytics', 'Service performance metrics and insights'],
      ['Audit Logs', 'audit', 'System activity and security events'],
      ['Knowledge Base Admin', 'knowledge', 'Document management'],
      ['System Indexer', 'owner', 'In Maintenance'],
      ['Ticket Queue', 'tickets', 'Ticket lifecycle'],
    ]

    // Start from the Ticket Queue view
    await clickNav(wrapper, 'Ticket Queue')
    expect(wrapper.text()).toContain('Ticket lifecycle')

    for (const [label, mode, marker] of tabs) {
      const btn = await clickNav(wrapper, label)

      // 1. The active tab state updates
      expect(btn.classes(), `"${label}" active state`).toContain('bg-red-600')
      // 2. The URL query reflects the new mode (no reload — history API only)
      expect(window.location.search, `URL after "${label}"`).toContain(`mode=${mode}`)
      // 3. The view content actually switched
      expect(wrapper.text(), `"${label}" view content`).toContain(marker)
    }

    useAgentStore().stopPolling()
  })

  it('navigates away from the Live Customer Workspace to every other view', async () => {
    const wrapper = await mountStaffAdmin()

    // Enter the workspace first
    await clickNav(wrapper, 'Live Customer Workspace')
    expect(wrapper.text()).toContain('Select a ticket') // workspace empty state

    const tabs = [
      ['Ticket Queue', 'tickets', 'Ticket lifecycle'],
      ['Analytics', 'analytics', 'Service performance metrics and insights'],
      ['Audit Logs', 'audit', 'System activity and security events'],
      ['Knowledge Base Admin', 'knowledge', 'Document management'],
      ['System Indexer', 'owner', 'In Maintenance'],
      ['Live Customer Workspace', 'agent', 'Select a ticket'],
    ]

    for (const [label, mode, marker] of tabs) {
      const btn = await clickNav(wrapper, label)
      expect(btn.classes(), `"${label}" active state`).toContain('bg-red-600')
      expect(window.location.search, `URL after "${label}"`).toContain(`mode=${mode}`)
      expect(wrapper.text(), `"${label}" view content`).toContain(marker)
    }

    useAgentStore().stopPolling()
  })

  it('deep-links ?mode=analytics for an ADMIN without bouncing to chat', async () => {
    window.history.replaceState({}, '', `${window.location.pathname}?mode=analytics`)
    localStorage.setItem('ai-support-chat:role', 'ADMIN')

    const wrapper = await mountApp()
    await flushPromises()
    await completeSignIn(wrapper)

    expect(wrapper.text()).toContain('Service performance metrics and insights')
    expect(navButton(wrapper, 'Analytics').classes()).toContain('bg-red-600')
    useAgentStore().stopPolling()
  })
})
