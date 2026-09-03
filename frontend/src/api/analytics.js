import axios from 'axios'

/**
 * Axios client for analytics and export endpoints.
 */
const analyticsClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
})

/**
 * Set the HTTP Basic credentials used for all requests.
 */
export function setAnalyticsAuth(username, password) {
  analyticsClient.defaults.headers.common.Authorization =
    `Basic ${btoa(`${username}:${password}`)}`
}

/**
 * Clear credentials.
 */
export function clearAnalyticsAuth() {
  delete analyticsClient.defaults.headers.common.Authorization
}

// ─── Analytics Endpoints ─────────────────────────────────────────────

/**
 * Get dashboard metrics.
 * GET /api/analytics/dashboard?startDate=...&endDate=...
 */
export async function getDashboardMetrics({ startDate, endDate } = {}) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  const { data } = await analyticsClient.get('/analytics/dashboard', { params })
  return data
}

/**
 * Get metrics by category.
 * GET /api/analytics/category/{category}
 */
export async function getMetricsByCategory(category, { startDate, endDate } = {}) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  const { data } = await analyticsClient.get(`/analytics/category/${category}`, { params })
  return data
}

/**
 * Get metrics by agent.
 * GET /api/analytics/agent/{agent}
 */
export async function getMetricsByAgent(agent, { startDate, endDate } = {}) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  const { data } = await analyticsClient.get(`/analytics/agent/${agent}`, { params })
  return data
}

/**
 * Get daily trend data.
 * GET /api/analytics/trend?startDate=...&endDate=...
 */
export async function getDailyTrend({ startDate, endDate } = {}) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  const { data } = await analyticsClient.get('/analytics/trend', { params })
  return data
}

/**
 * Get summary statistics.
 * GET /api/analytics/summary
 */
export async function getAnalyticsSummary() {
  const { data } = await analyticsClient.get('/analytics/summary')
  return data
}

// ─── Export Endpoints ────────────────────────────────────────────────

/**
 * Export tickets to CSV.
 * GET /api/export/tickets/csv?status=...&priority=...
 */
export async function exportTicketsCsv({ status, priority, category } = {}) {
  const params = {}
  if (status) params.status = status
  if (priority) params.priority = priority
  if (category) params.category = category
  
  const response = await analyticsClient.get('/export/tickets/csv', { 
    params,
    responseType: 'blob'
  })
  
  // Trigger download
  const url = window.URL.createObjectURL(new Blob([response.data]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `tickets_export_${Date.now()}.csv`)
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

/**
 * Export tickets to PDF.
 * GET /api/export/tickets/pdf?status=...&priority=...
 */
export async function exportTicketsPdf({ status, priority, category } = {}) {
  const params = {}
  if (status) params.status = status
  if (priority) params.priority = priority
  if (category) params.category = category
  
  const response = await analyticsClient.get('/export/tickets/pdf', { 
    params,
    responseType: 'blob'
  })
  
  // Trigger download
  const url = window.URL.createObjectURL(new Blob([response.data]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `tickets_export_${Date.now()}.pdf`)
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

// ─── Audit Log Endpoints ─────────────────────────────────────────────

/**
 * Get audit logs with pagination.
 * GET /api/audit?page=0&size=20
 */
export async function getAuditLogs(page = 0, size = 20) {
  const { data } = await analyticsClient.get('/audit', { params: { page, size } })
  return data
}

/**
 * Get filtered audit logs.
 * GET /api/audit/filter?actionType=...&actorEmail=...
 */
export async function getFilteredAuditLogs({ 
  actionType, actorEmail, resourceType, startDate, endDate, page = 0, size = 20 
} = {}) {
  const params = { page, size }
  if (actionType) params.actionType = actionType
  if (actorEmail) params.actorEmail = actorEmail
  if (resourceType) params.resourceType = resourceType
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  
  const { data } = await analyticsClient.get('/audit/filter', { params })
  return data
}

/**
 * Get audit log statistics.
 * GET /api/audit/stats
 */
export async function getAuditLogStats() {
  const { data } = await analyticsClient.get('/audit/stats')
  return data
}

/**
 * Get available action types.
 * GET /api/audit/action-types
 */
export async function getAuditActionTypes() {
  const { data } = await analyticsClient.get('/audit/action-types')
  return data
}

/**
 * Export audit logs to CSV.
 * GET /api/export/audit/csv?startDate=...&endDate=...
 */
export async function exportAuditLogsCsv({ startDate, endDate } = {}) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  
  const response = await analyticsClient.get('/export/audit/csv', { 
    params,
    responseType: 'blob'
  })
  
  // Trigger download
  const url = window.URL.createObjectURL(new Blob([response.data]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `audit_logs_export_${Date.now()}.csv`)
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

/**
 * Export audit logs to PDF.
 * GET /api/export/audit/pdf?startDate=...&endDate=...
 */
export async function exportAuditLogsPdf({ startDate, endDate } = {}) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  
  const response = await analyticsClient.get('/export/audit/pdf', { 
    params,
    responseType: 'blob'
  })
  
  // Trigger download
  const url = window.URL.createObjectURL(new Blob([response.data]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `audit_logs_export_${Date.now()}.pdf`)
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}
