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
 * Get ticket activity logs (timeline) — legacy endpoint.
 * GET /api/tickets/{id}/activity?customerOnly=false
 * @param {number} id
 * @param {boolean} customerOnly
 */
export function getTicketActivityLogs(id, customerOnly = false) {
  return request(
    async () => (await adminClient.get(`/tickets/${id}/activity`, { params: { customerOnly } })).data,
  )
}

// ─── V1 Ticket Lifecycle API (Section 6.5) ──────────────────────────

/**
 * Get ticket activity logs via the v1 timeline endpoint.
 * GET /api/v1/tickets/{id}/activity-logs?customerOnly=false
 * @param {number} id
 * @param {boolean} [customerOnly]
 */
export function getTicketActivityLogsV1(id, customerOnly = false) {
  return request(
    async () => (await adminClient.get(`/v1/tickets/${id}/activity-logs`, { params: { customerOnly } })).data,
  )
}

/**
 * Update ticket status via the v1 state-machine endpoint.
 * PATCH /api/v1/tickets/{id}/status
 * @param {number} id
 * @param {string} status
 * @param {string} [reason]
 */
export function updateTicketStatusV1(id, status, reason = null) {
  const body = { status }
  if (reason) body.reason = reason
  return request(
    async () => (await adminClient.patch(`/v1/tickets/${id}/status`, body)).data,
  )
}

/**
 * Create a ticket from conversation context.
 * POST /api/v1/tickets
 * @param {object} payload - { userId, sessionId, subject, description, conversationId?, category?, priority? }
 */
export function createTicket(payload) {
  return request(
    async () => (await adminClient.post('/v1/tickets', payload)).data,
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

// ─── V1 Analytics API (Section 6.9) ────────────────────────────────

/**
 * Get Section 6.9 operational metrics.
 * GET /api/v1/analytics/metrics?startDate=...&endDate=...
 * @param {object} opts - { startDate, endDate }
 */
export function getOperationalMetrics({ startDate, endDate } = {}) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  return request(
    async () => (await adminClient.get('/v1/analytics/metrics', { params })).data,
  )
}

/**
 * Get daily trend data.
 * GET /api/analytics/trend?startDate=...&endDate=...
 */
export function getDailyTrend({ startDate, endDate } = {}) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  return request(
    async () => (await adminClient.get('/analytics/trend', { params })).data,
  )
}

// ─── V1 Analytics Export (Section 6.9/6.10) ────────────────────────

/**
 * Export analytics as CSV via v1 endpoint.
 * GET /api/v1/analytics/export/csv?startDate=...&endDate=...
 */
export function exportAnalyticsCsv({ startDate, endDate } = {}) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  return request(
    async () => {
      const response = await adminClient.get('/v1/analytics/export/csv', {
        params,
        responseType: 'blob',
      })
      downloadBlob(response.data, `analytics_report_${Date.now()}.csv`)
    },
  )
}

/**
 * Export analytics as PDF via v1 endpoint.
 * GET /api/v1/analytics/export/pdf?startDate=...&endDate=...
 */
export function exportAnalyticsPdf({ startDate, endDate } = {}) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  return request(
    async () => {
      const response = await adminClient.get('/v1/analytics/export/pdf', {
        params,
        responseType: 'blob',
      })
      downloadBlob(response.data, `analytics_report_${Date.now()}.pdf`)
    },
  )
}

// ─── V1 Audit Log API (Section 6.10) ───────────────────────────────

/**
 * Get audit logs via the admin v1 endpoint.
 * GET /api/v1/admin/audit-logs?page=0&size=50
 */
export function getAuditLogsV1(page = 0, size = 50) {
  return request(
    async () => (await adminClient.get('/v1/admin/audit-logs', { params: { page, size } })).data,
  )
}

/**
 * Get filtered audit logs via the v1 endpoint.
 * GET /api/v1/audit/filter?actionType=...&actorEmail=...
 */
export function getFilteredAuditLogsV1({
  actionType, actorEmail, actorRole, resourceType, startDate, endDate, page = 0, size = 50,
} = {}) {
  const params = { page, size }
  if (actionType) params.actionType = actionType
  if (actorEmail) params.actorEmail = actorEmail
  if (actorRole) params.actorRole = actorRole
  if (resourceType) params.resourceType = resourceType
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  return request(
    async () => (await adminClient.get('/v1/audit/filter', { params })).data,
  )
}

// ─── Helpers ───────────────────────────────────────────────────────

/** Trigger a browser file download from a Blob. */
function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob instanceof Blob ? blob : new Blob([blob]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', filename)
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}
