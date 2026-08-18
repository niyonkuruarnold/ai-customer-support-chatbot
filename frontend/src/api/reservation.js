import axios from 'axios'

/**
 * Axios client for the tool reservation API.
 * Uses HTTP Basic auth — credentials kept in memory and synced via the
 * agent store's login/logout.
 */
const reservationClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
})

/** Set HTTP Basic credentials for all reservation requests. */
export function setReservationAuth(username, password) {
  reservationClient.defaults.headers.common.Authorization =
    `Basic ${btoa(`${username}:${password}`)}`
}

/** Clear credentials (logout). */
export function clearReservationAuth() {
  delete reservationClient.defaults.headers.common.Authorization
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

/**
 * Create a new tool reservation.
 * POST /v1/reservations
 * @param {{ toolId: number, borrowerId: number, startDate: string, endDate: string, notes?: string }} payload
 */
export function createReservation(payload) {
  return request(async () => (await reservationClient.post('/v1/reservations', payload)).data)
}

/**
 * Check tool availability for a date range.
 * GET /v1/reservations/availability?toolId=&startDate=&endDate=
 */
export function checkAvailability(toolId, startDate, endDate) {
  return request(async () =>
    (await reservationClient.get('/v1/reservations/availability', {
      params: { toolId, startDate, endDate },
    })).data,
  )
}

/**
 * Get a single reservation.
 * GET /v1/reservations/:id
 */
export function getReservation(id) {
  return request(async () => (await reservationClient.get(`/v1/reservations/${id}`)).data)
}

/**
 * Get all reservations for a borrower.
 * GET /v1/reservations/my/:borrowerId
 */
export function getMyReservations(borrowerId) {
  return request(async () =>
    (await reservationClient.get(`/v1/reservations/my/${borrowerId}`)).data,
  )
}

/**
 * Approve a reservation.
 * POST /v1/reservations/:id/approve
 */
export function approveReservation(id) {
  return request(async () =>
    (await reservationClient.post(`/v1/reservations/${id}/approve`)).data,
  )
}

/**
 * Reject a reservation.
 * POST /v1/reservations/:id/reject
 */
export function rejectReservation(id) {
  return request(async () =>
    (await reservationClient.post(`/v1/reservations/${id}/reject`)).data,
  )
}

/**
 * Check out a reservation (mark as picked up).
 * POST /v1/reservations/:id/checkout
 */
export function checkoutReservation(id) {
  return request(async () =>
    (await reservationClient.post(`/v1/reservations/${id}/checkout`)).data,
  )
}

/**
 * Return a reservation (mark as returned).
 * POST /v1/reservations/:id/return
 */
export function returnReservation(id) {
  return request(async () =>
    (await reservationClient.post(`/v1/reservations/${id}/return`)).data,
  )
}
