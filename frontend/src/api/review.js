import axios from 'axios'

/**
 * Axios client for the tool review API.
 * Uses HTTP Basic auth — credentials kept in memory and synced via the
 * agent store's login/logout.
 */
const reviewClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
})

/** Set HTTP Basic credentials for all review requests. */
export function setReviewAuth(username, password) {
  reviewClient.defaults.headers.common.Authorization =
    `Basic ${btoa(`${username}:${password}`)}`
}

/** Clear credentials (logout). */
export function clearReviewAuth() {
  delete reviewClient.defaults.headers.common.Authorization
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
 * Submit a review for a completed tool borrow.
 * POST /v1/reviews
 * @param {{ toolId: number, reviewerId: number, reservationId: number, rating: number, comment?: string }} payload
 */
export function submitReview(payload) {
  return request(async () => (await reviewClient.post('/v1/reviews', payload)).data)
}

/**
 * Get a single review.
 * GET /v1/reviews/:id
 */
export function getReview(id) {
  return request(async () => (await reviewClient.get(`/v1/reviews/${id}`)).data)
}

/**
 * Get all reviews for a specific tool.
 * GET /v1/reviews/tool/:toolId
 */
export function getReviewsByTool(toolId) {
  return request(async () => (await reviewClient.get(`/v1/reviews/tool/${toolId}`)).data)
}

/**
 * Get all reviews by a specific user.
 * GET /v1/reviews/user/:userId
 */
export function getReviewsByUser(userId) {
  return request(async () => (await reviewClient.get(`/v1/reviews/user/${userId}`)).data)
}

/**
 * Get average rating for a tool.
 * GET /v1/reviews/tool/:toolId/average
 */
export function getAverageRatingForTool(toolId) {
  return request(async () => (await reviewClient.get(`/v1/reviews/tool/${toolId}/average`)).data)
}

/**
 * Get average rating for a user.
 * GET /v1/reviews/user/:userId/average
 */
export function getAverageRatingForUser(userId) {
  return request(async () => (await reviewClient.get(`/v1/reviews/user/${userId}/average`)).data)
}

/**
 * Check if a reservation has been reviewed.
 * GET /v1/reviews/reservation/:reservationId/status
 */
export function checkReviewStatus(reservationId) {
  return request(async () => (await reviewClient.get(`/v1/reviews/reservation/${reservationId}/status`)).data)
}
