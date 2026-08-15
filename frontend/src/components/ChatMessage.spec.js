import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ChatMessage from './ChatMessage.vue'

function makeMessage(overrides = {}) {
  return {
    id: 'm1',
    role: 'user',
    content: 'Hello there',
    timestamp: 1700000000000,
    status: 'sent',
    ...overrides,
  }
}

describe('ChatMessage', () => {
  it('renders the message content', () => {
    const wrapper = mount(ChatMessage, {
      props: { message: makeMessage() },
    })
    expect(wrapper.text()).toContain('Hello there')
  })

  it('renders user messages with user styling and reversed layout', () => {
    const wrapper = mount(ChatMessage, {
      props: { message: makeMessage({ role: 'user' }) },
    })
    expect(wrapper.find('.flex-row-reverse').exists()).toBe(true)
    expect(wrapper.find('.bg-gradient-to-br').exists()).toBe(true)
    expect(wrapper.text()).toContain('You')
  })

  it('renders assistant messages on the left with assistant styling', () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: makeMessage({ role: 'assistant', content: 'Sure, one moment!' }),
      },
    })
    expect(wrapper.find('.flex-row-reverse').exists()).toBe(false)
    expect(wrapper.find('.bg-white').exists()).toBe(true)
    expect(wrapper.text()).toContain('Sure, one moment!')
    expect(wrapper.text()).toContain('Support AI')
  })

  it('renders agent messages with agent styling and label', () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: makeMessage({
          role: 'agent',
          content: 'I am Sarah, your support agent.',
        }),
      },
    })
    expect(wrapper.text()).toContain('I am Sarah, your support agent.')
    expect(wrapper.text()).toContain('Support Agent')
    expect(wrapper.find('.from-emerald-500').exists()).toBe(true)
    expect(wrapper.find('.flex-row-reverse').exists()).toBe(false)
  })

  it('shows a retry chip for failed messages and emits retry with the id', async () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: makeMessage({ status: 'failed', error: 'boom' }),
      },
    })

    const retryButton = wrapper.find('[data-test="retry"]')
    expect(retryButton.exists()).toBe(true)
    expect(wrapper.text()).toContain('Failed to send')

    await retryButton.trigger('click')
    expect(wrapper.emitted('retry')).toEqual([['m1']])
  })

  it('does not render a retry chip for successful messages', () => {
    const wrapper = mount(ChatMessage, {
      props: { message: makeMessage() },
    })
    expect(wrapper.find('[data-test="retry"]').exists()).toBe(false)
  })
})
