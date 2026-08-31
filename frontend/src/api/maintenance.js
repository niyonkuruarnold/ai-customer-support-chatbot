import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

/**
 * Axios client for the maintenance and tool management API.
 * Uses HTTP Basic auth — credentials kept in memory and synced via the
 * agent store's login/logout.
 */
const maintenanceClient = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
})

/**
 * Unauthenticated client for permitAll endpoints (e.g. GET /v1/tools).
 * Sends no Authorization header so the request always succeeds even
 * when the user has not logged in or credentials are stale.
 */
const publicClient = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
})

/** Set HTTP Basic credentials for all maintenance requests. */
export function setMaintenanceAuth(username, password) {
  maintenanceClient.defaults.headers.common.Authorization =
    `Basic ${btoa(`${username}:${password}`)}`
}

/** Clear credentials (logout). */
export function clearMaintenanceAuth() {
  delete maintenanceClient.defaults.headers.common.Authorization
}

async function request(fn) {
  try {
    return await fn()
  } catch (err) {
    if (err.response && err.response.status === 401) {
      const authError = new Error('Authentication required')
      authError.status = 401
      throw authError
    }
    throw err
  }
}

// ---- Tool endpoints ----

/**
 * Create a new tool.
 * POST /v1/tools
 */
export function createTool(payload) {
  return request(async () => (await maintenanceClient.post('/v1/tools', payload)).data)
}

/**
 * Get a single tool.
 * GET /v1/tools/:id
 */
export function getTool(id) {
  return request(async () => (await maintenanceClient.get(`/v1/tools/${id}`)).data)
}

/**
 * Get all tools for an owner.
 * GET /v1/tools/owner/:ownerId
 */
export function getToolsByOwner(ownerId) {
  return request(async () => (await maintenanceClient.get(`/v1/tools/owner/${ownerId}`)).data)
}

/**
 * Get all tools in the system.
 * GET /v1/tools — permitAll endpoint, no auth required.
 * Uses publicClient (no Authorization header) so the request always
 * succeeds even when the user has not logged in.
 */
export function getAllTools() {
  return request(async () => (await publicClient.get('/v1/tools')).data)
}

/**
 * Get all tools with a specific status.
 * GET /v1/tools/status/:status
 */
export function getToolsByStatus(status) {
  return request(async () => (await maintenanceClient.get(`/v1/tools/status/${status}`)).data)
}

/**
 * Update tool status.
 * PATCH /v1/tools/:id/status
 */
export function updateToolStatus(id, status) {
  return request(async () =>
    (await maintenanceClient.patch(`/v1/tools/${id}/status`, { status })).data,
  )
}

// ---- Maintenance Log endpoints ----

/**
 * Create a maintenance log entry.
 * POST /v1/maintenance
 */
export function createMaintenanceLog(payload) {
  return request(async () => (await maintenanceClient.post('/v1/maintenance', payload)).data)
}

/**
 * Get a single maintenance log.
 * GET /v1/maintenance/:id
 */
export function getMaintenanceLog(id) {
  return request(async () => (await maintenanceClient.get(`/v1/maintenance/${id}`)).data)
}

/**
 * Get all maintenance logs for a tool.
 * GET /v1/maintenance/tool/:toolId
 */
export function getMaintenanceLogsByTool(toolId) {
  return request(async () =>
    (await maintenanceClient.get(`/v1/maintenance/tool/${toolId}`)).data,
  )
}

/**
 * Get maintenance logs by date range.
 * GET /v1/maintenance/tool/:toolId/range?startDate=&endDate=
 */
export function getMaintenanceLogsByDateRange(toolId, startDate, endDate) {
  return request(async () =>
    (await maintenanceClient.get(`/v1/maintenance/tool/${toolId}/range`, {
      params: { startDate, endDate },
    })).data,
  )
}

/**
 * Get upcoming maintenance.
 * GET /v1/maintenance/upcoming?beforeDate=
 */
export function getUpcomingMaintenance(beforeDate) {
  return request(async () =>
    (await maintenanceClient.get('/v1/maintenance/upcoming', {
      params: { beforeDate },
    })).data,
  )
}

/**
 * Complete maintenance for a tool (restore to AVAILABLE).
 * POST /v1/maintenance/tool/:toolId/complete
 */
export function completeMaintenance(toolId) {
  return request(async () =>
    (await maintenanceClient.post(`/v1/maintenance/tool/${toolId}/complete`)).data,
  )
}

/**
 * Get maintenance stats for a tool.
 * GET /v1/maintenance/tool/:toolId/stats
 */
export function getMaintenanceStats(toolId) {
  return request(async () =>
    (await maintenanceClient.get(`/v1/maintenance/tool/${toolId}/stats`)).data,
  )
}
