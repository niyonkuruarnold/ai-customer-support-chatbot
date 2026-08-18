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

  it('renders markdown formatting in assistant responses', () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: makeMessage({
          role: 'assistant',
          content: '**Yes!** Here are the steps:\n\n1. Open settings\n2. Save',
        }),
      },
    })

    const body = wrapper.find('.markdown-body')
    expect(body.exists()).toBe(true)
    expect(body.find('strong').text()).toBe('Yes!')
    expect(body.findAll('li').length).toBe(2)
    // Raw markdown syntax must not leak through
    expect(body.text()).not.toContain('**Yes!**')
  })

  it('escapes raw HTML in assistant content', () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: makeMessage({
          role: 'assistant',
          content: 'Hello <script>alert(1)</script>',
        }),
      },
    })

    const body = wrapper.find('.markdown-body')
    expect(body.find('script').exists()).toBe(false)
    expect(body.text()).toContain('<script>alert(1)</script>')
  })

  it('renders clickable knowledge base citations for assistant messages', () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: makeMessage({
          role: 'assistant',
          content: 'Returns are accepted within 30 days.',
          ragUsed: true,
          contextReferences: [
            { documentId: 3, title: 'Returns Policy', sourceType: 'TEXT' },
            { documentId: 7, title: 'Shipping FAQ', sourceType: 'MARKDOWN' },
          ],
        }),
      },
    })

    const citations = wrapper.find('[data-test="citations"]')
    expect(citations.exists()).toBe(true)
    const links = citations.findAll('a')
    expect(links).toHaveLength(2)
    expect(links[0].text()).toContain('Returns Policy')
    expect(links[0].attributes('href')).toBe('?mode=knowledge')
    expect(links[1].text()).toContain('Shipping FAQ')
  })

  it('does not render citations when there are no context references', () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: makeMessage({ role: 'assistant', content: 'No citations here' }),
      },
    })
    expect(wrapper.find('[data-test="citations"]').exists()).toBe(false)
  })

  it('does not render user content as markdown', () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: makeMessage({ content: 'Is **this** bold?' }),
      },
    })
    expect(wrapper.find('.markdown-body').exists()).toBe(false)
    expect(wrapper.text()).toContain('Is **this** bold?')
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
