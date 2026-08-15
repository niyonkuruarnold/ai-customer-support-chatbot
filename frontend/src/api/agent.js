import axios from 'axios'

/**
 * Axios client for the authenticated agent workspace endpoints.
 * Credentials are sent via HTTP Basic and kept in memory only — the
 * frontend never persists agent credentials.
 */
const agentClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
})

/** Set the HTTP Basic credentials used for all agent requests. */
export function setAgentAuth(username, password) {
  agentClient.defaults.headers.common.Authorization =
    `Basic ${btoa(`${username}:${password}`)}`
}

/** Clear agent credentials (logout). */
export function clearAgentAuth() {
  delete agentClient.defaults.headers.common.Authorization
}

/** Normalize 401 responses into an error carrying `status = 401`. */
function wrapAuthError(err) {
  if (err.response && err.response.status === 401) {
    const authError = new Error('Agent authentication required')
    authError.status = 401
    return authError
  }
  return err
}

async function request(fn) {
  try {
    return await fn()
  } catch (err) {
    throw wrapAuthError(err)
  }
}

/** GET /agent/tickets -> AgentTicketDto[] */
export function fetchTickets() {
  return request(async () => (await agentClient.get('/agent/tickets')).data)
}

/** GET /agent/tickets/{id} -> AgentTicketDetailDto */
export function fetchTicketDetail(id) {
  return request(
    async () => (await agentClient.get(`/agent/tickets/${id}`)).data,
  )
}

/** POST /agent/tickets/{id}/takeover -> AgentTicketDetailDto */
export function takeOverTicket(id) {
  return request(
    async () => (await agentClient.post(`/agent/tickets/${id}/takeover`)).data,
  )
}

/** POST /agent/tickets/{id}/reply -> AgentTicketDetailDto */
export function sendAgentReply(id, message) {
  return request(
    async () =>
      (await agentClient.post(`/agent/tickets/${id}/reply`, { message })).data,
  )
}

/** POST /agent/tickets/{id}/notes -> AgentTicketDetailDto */
export function addTicketNote(id, content) {
  return request(
    async () =>
      (await agentClient.post(`/agent/tickets/${id}/notes`, { content })).data,
  )
}

/** POST /agent/tickets/{id}/resolve -> AgentTicketDetailDto */
export function resolveTicket(id) {
  return request(
    async () => (await agentClient.post(`/agent/tickets/${id}/resolve`)).data,
  )
}
