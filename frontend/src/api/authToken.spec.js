import axios from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { attachBearerToken, clearAccessToken, setAccessToken } from './authToken'

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