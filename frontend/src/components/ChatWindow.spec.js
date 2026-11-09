import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ChatWindow from './ChatWindow.vue'
import { useChatStore } from '../stores/chat'
import { fetchSessionInfo, sendChatMessage } from '../api/chat'

vi.mock('../api/chat', async (importOriginal) => {
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

async function mountWidget() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(ChatWindow, { global: { plugins: [pinia] } })
  await flushPromises()
  return wrapper
}

describe('ChatWindow', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    fetchSessionInfo.mockResolvedValue({ id: 1, status: 'ACTIVE', messages: [] })
  })

  it('starts collapsed with a launcher bubble and no panel', async () => {
    const wrapper = await mountWidget()

    expect(wrapper.find('[data-test="chat-launcher"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="chat-window"]').exists()).toBe(false)
    useChatStore().stopPolling()
  })

  it('expands into the chat panel when the launcher is clicked', async () => {
    const wrapper = await mountWidget()

    await wrapper.find('[data-test="chat-launcher"]').trigger('click')

    expect(wrapper.find('[data-test="chat-window"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="chat-launcher"]').exists()).toBe(false)
    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(wrapper.text()).toContain('AI Customer Support')
    useChatStore().stopPolling()
  })

  it('collapses back to the launcher from the minimize button', async () => {
    const wrapper = await mountWidget()

    await wrapper.find('[data-test="chat-launcher"]').trigger('click')
    await wrapper.find('[data-test="chat-close"]').trigger('click')

    expect(wrapper.find('[data-test="chat-launcher"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="chat-window"]').exists()).toBe(false)
    useChatStore().stopPolling()
  })

  it('sends a typed message and renders the AI response', async () => {
    sendChatMessage.mockResolvedValue(okResponse())
    const wrapper = await mountWidget()
    await wrapper.find('[data-test="chat-launcher"]').trigger('click')

    await wrapper.find('textarea').setValue('I need help')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(sendChatMessage).toHaveBeenCalledWith('I need help', null)
    const store = useChatStore()
    expect(store.messages).toHaveLength(2)
    expect(store.messages[1].content).toBe('How can I help?')
    expect(wrapper.find('textarea').element.value).toBe('')
    store.stopPolling()
  })

  it('shows an inline retry for failed messages and resends them', async () => {
    sendChatMessage.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountWidget()
    const store = useChatStore()
    await wrapper.find('[data-test="chat-launcher"]').trigger('click')

    await wrapper.find('textarea').setValue('Hello?')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(store.messages[0].status).toBe('failed')
    expect(wrapper.find('[data-test="retry"]').exists()).toBe(true)

    sendChatMessage.mockResolvedValueOnce(okResponse({ response: 'Back online!' }))
    await wrapper.find('[data-test="retry"]').trigger('click')
    await flushPromises()

    expect(store.messages).toHaveLength(2)
    expect(store.messages[1].content).toBe('Back online!')
    store.stopPolling()
  })

  it('shows Agent Active status in the header when the session is escalated', async () => {
    const wrapper = await mountWidget()
    await wrapper.find('[data-test="chat-launcher"]').trigger('click')

    const store = useChatStore()
    store.sessionStatus = 'ESCALATED'
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Agent Active')
    expect(wrapper.text()).toContain('AI paused')
    store.stopPolling()
  })
})
