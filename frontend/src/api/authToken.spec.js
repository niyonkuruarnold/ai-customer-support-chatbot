import axios from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { attachBearerToken, attachUnauthorizedHandler, clearAccessToken, registerUnauthorizedHandler, setAccessToken } from './authToken'

describe('Bearer auth request interceptor', () => {
  afterEach(() => clearAccessToken())

  it('uses the latest token on each request and removes it after logout', async () => {
    const client = axios.create()
    attachBearerToken(client)
    const sentConfigs = []
    client.defaults.adapter = async (config) => {
      sentConfigs.push(config)
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }

    setAccessToken('admin-token')
    await client.get('/first')
    setAccessToken('agent-token')
    await client.get('/second')
    clearAccessToken()
    await client.get('/third')

    expect(sentConfigs[0].headers.Authorization).toBe('Bearer admin-token')
    expect(sentConfigs[1].headers.Authorization).toBe('Bearer agent-token')
    expect(sentConfigs[2].headers.Authorization).toBeUndefined()
  })
})

describe('401 response interceptor', () => {
  afterEach(() => clearAccessToken())

  /** Client whose adapter always fails with the given HTTP status. */
  function failingClient(status) {
    const client = axios.create()
    attachBearerToken(client)
    client.defaults.adapter = async () => {
      const error = new Error(`Request failed with status code ${status}`)
      error.response = { status, data: {}, headers: {}, config: {} }
      throw error
    }
    return client
  }

  it('notifies registered handlers on 401, and stops once unregistered', async () => {
    const handler = vi.fn()
    const unregister = registerUnauthorizedHandler(handler)

    await failingClient(401).get('/agent/tickets').catch(() => {})
    expect(handler).toHaveBeenCalledTimes(1)

    unregister()
    await failingClient(401).get('/agent/tickets').catch(() => {})
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('notifies handlers for non-bearer Axios clients too', async () => {
    const handler = vi.fn()
    const unregister = registerUnauthorizedHandler(handler)
    const client = axios.create()
    attachUnauthorizedHandler(client)
    client.defaults.adapter = async () => {
      const error = new Error('Request failed with status code 401')
      error.response = { status: 401, data: {}, headers: {}, config: {} }
      throw error
    }

    await client.get('/chat/session').catch(() => {})

    expect(handler).toHaveBeenCalledTimes(1)
    unregister()
  })

  it('ignores non-401 failures and still rejects the original error', async () => {
    const handler = vi.fn()
    const unregister = registerUnauthorizedHandler(handler)

    const failure = failingClient(500).get('/agent/tickets').catch((err) => err)
    await expect(failure).resolves.toMatchObject({ response: { status: 500 } })
    expect(handler).not.toHaveBeenCalled()

    unregister()
  })
})