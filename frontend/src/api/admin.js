import axios from 'axios'

/**
 * Axios client for the authenticated admin endpoints (knowledge base).
 * Uses the same HTTP Basic credentials as the agent workspace — the agent
 * store's login()/logout() keep this client in sync, so one sign-in covers
 * both areas. Credentials stay in memory only.
 */
const adminClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  // Do NOT set a global Content-Type header here — axios must be free to
  // set multipart/form-data with the correct boundary when sending FormData
  // (file uploads). For JSON payloads axios auto-sets application/json.
  timeout: 120000,
})

/** Set the HTTP Basic credentials used for all admin requests. */
export function setAdminAuth(username, password) {
  adminClient.defaults.headers.common.Authorization =
    `Basic ${btoa(`${username}:${password}`)}`
}

/** Clear admin credentials (logout). */
export function clearAdminAuth() {
  delete adminClient.defaults.headers.common.Authorization
}

/** Normalize 401 responses into an error carrying `status = 401`. */
function wrapAuthError(err) {
  if (err.response && err.response.status === 401) {
    const authError = new Error('Admin authentication required')
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

/**
 * Upload a support document (.txt/.md/.pdf) and index it.
 *
 * POST /admin/documents/upload (multipart: file + optional title)
 * @param {File} file
 * @param {string} [title]
 */
export function uploadDocument(file, title) {
  const form = new FormData()
  form.append('file', file)
  if (title) form.append('title', title)
  return request(
    async () =>
      (await adminClient.post('/admin/documents/upload', form)).data,
  )
}

/**
 * Index raw pasted FAQ/support text (no v1 knowledge-base equivalent —
 * the documents namespace is used by the agent workspace KB tab).
 * POST /admin/documents/text  ({ title, content })
 */
export function addTextDocument(title, content) {
  return request(
    async () =>
      (await adminClient.post('/admin/documents/text', { title, content })).data,
  )
}

/** GET /v1/admin/knowledge-base -> KnowledgeDocumentDto[] */
export function fetchDocuments() {
  return request(
    async () => (await adminClient.get('/v1/admin/knowledge-base')).data,
  )
}

/** GET /admin/documents/chunks -> KnowledgeChunkDto[] (no v1 equivalent) */
export function fetchChunks() {
  return request(async () => (await adminClient.get('/admin/documents/chunks')).data)
}

/** DELETE /v1/admin/knowledge-base/{id} */
export function deleteDocument(id) {
  return request(
    async () => adminClient.delete(`/v1/admin/knowledge-base/${id}`),
  )
}

/**
 * List support tickets for the admin dashboard, with optional filters and
 * pagination. GET /v1/tickets?status=&priority=&assignedAgentId=&page=&size=
 * @returns {Promise<PageResponse>} { content, page, size, totalElements, totalPages, last }
 */
export function fetchTickets({ status, priority, assignedAgentId, page = 0, size = 10 } = {}) {
  const params = { page, size }
  if (status) params.status = status
  if (priority) params.priority = priority
  if (assignedAgentId != null && assignedAgentId !== '') {
    params.assignedAgentId = assignedAgentId
  }
  return request(
    async () => (await adminClient.get('/v1/tickets', { params })).data,
  )
}

/** POST /v1/tickets/{id}/close -> TicketDto (RESOLVED -> CLOSED) */
export function closeTicket(id) {
  return request(async () => (await adminClient.post(`/v1/tickets/${id}/close`)).data)
}

/** PATCH /v1/tickets/{id}/status  ({ status }) -> TicketDto */
export function updateTicketStatus(id, status) {
  return request(
    async () => (await adminClient.patch(`/v1/tickets/${id}/status`, { status })).data,
  )
}

/** PATCH /v1/tickets/{id}/agent  ({ assignedAgent }) -> TicketDto */
export function updateTicketAgent(id, assignedAgent) {
  return request(
    async () =>
      (await adminClient.patch(`/v1/tickets/${id}/agent`, { assignedAgent })).data,
  )
}

/** DELETE /v1/tickets/{id} -> 204 No Content */
export function deleteTicket(id) {
  return request(async () => adminClient.delete(`/v1/tickets/${id}`))
}

// ─── New Ticket Operations ───────────────────────────────────────────

/**
 * Update ticket status with new state machine.
 * POST /api/tickets/{id}/status  ({ status, reason? })
 * @param {number} id
 * @param {string} status
 * @param {string} [reason]
 */
export function updateTicketStatusNew(id, status, reason = null) {
  return request(
    async () => (await adminClient.post(`/tickets/${id}/status`, { status, reason })).data,
  )
}

/**
 * Update ticket priority.
 * POST /api/tickets/{id}/priority  ({ priority })
 * @param {number} id
 * @param {string} priority - LOW, MEDIUM, HIGH, URGENT
 */
export function updateTicketPriority(id, priority) {
  return request(
    async () => (await adminClient.post(`/tickets/${id}/priority`, { priority })).data,
  )
}

/**
 * Reassign ticket to a different agent.
 * POST /api/tickets/{id}/assign  ({ assignedAgent })
 * @param {number} id
 * @param {string} assignedAgent
 */
export function reassignTicket(id, assignedAgent) {
  return request(
    async () => (await adminClient.post(`/tickets/${id}/assign`, { assignedAgent })).data,
  )
}

/**
 * Add a note (public or internal) to a ticket.
 * POST /api/tickets/{id}/notes  ({ content, isInternal })
 * @param {number} id
 * @param {string} content
 * @param {boolean} isInternal
 */
export function addTicketNote(id, content, isInternal = true) {
  return request(
    async () => (await adminClient.post(`/tickets/${id}/notes`, { content, isInternal })).data,
  )
}

/**
 * Reopen a resolved or closed ticket.
 * POST /api/tickets/{id}/reopen  ({ reason? })
 * @param {number} id
 * @param {string} [reason]
 */
export function reopenTicket(id, reason = null) {
  return request(
    async () => (await adminClient.post(`/tickets/${id}/reopen`, { reason })).data,
  )
}

/**
 * Get ticket activity logs (timeline).
 * GET /api/tickets/{id}/activity?customerOnly=false
 * @param {number} id
 * @param {boolean} customerOnly
 */
export function getTicketActivityLogs(id, customerOnly = false) {
  return request(
    async () => (await adminClient.get(`/tickets/${id}/activity`, { params: { customerOnly } })).data,
  )
}

/**
 * Get ticket statistics.
 * GET /api/tickets/stats
 */
export function getTicketStats() {
  return request(
    async () => (await adminClient.get('/tickets/stats')).data,
  )
}
