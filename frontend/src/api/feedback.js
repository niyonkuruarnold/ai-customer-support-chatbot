import axios from 'axios'

/**
 * Axios instance configured to talk to the Spring Boot backend.
 */
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

/**
 * Submit CSAT feedback for a chat session.
 *
 * POST /v1/chat/conversations/{id}/feedback
 *   body: { rating, comment? }
 *   returns: { id, sessionId, rating, comment, createdAt }
 *
 * @param {number} sessionId
 * @param {number} rating (1-5)
 * @param {string} [comment]
 * @returns {Promise<Object>}
 */
export async function submitChatFeedback(sessionId, rating, comment = null) {
  const { data } = await apiClient.post(`/v1/chat/conversations/${sessionId}/feedback`, {
    rating,
    comment: comment || null,
  })
  return data
}

/**
 * Check if feedback has been submitted for a session.
 *
 * GET /v1/chat/feedback/session/{id}
 *   returns: boolean
 *
 * @param {number|string} sessionId
 * @returns {Promise<boolean>}
 */
export async function hasChatFeedback(sessionId) {
  const { data } = await apiClient.get(`/v1/chat/feedback/session/${sessionId}`)
  return data
}
