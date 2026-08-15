import axios from 'axios'

/**
 * Axios instance configured to talk to the Spring Boot backend.
 * The base URL can be overridden with VITE_API_BASE_URL in a .env file.
 */
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' },
  // AI responses can take a while to generate, so allow up to 60s
  timeout: 60000,
})

/**
 * Send a chat message to the backend. The backend persists the message in a
 * session (created on the first message when sessionId is null) and returns
 * the session id plus its status so the client can continue the conversation.
 *
 * POST /chat
 *   body:    { message, sessionId? }
 *   returns: { response, sessionId, status }
 *
 * @param {string} message
 * @param {string|null} sessionId
 * @returns {Promise<{response: string, sessionId: number|null, status: string}>}
 */
export async function sendChatMessage(message, sessionId) {
  const { data } = await apiClient.post('/chat', { message, sessionId })
  return data
}

/**
 * Fetch the full session state (status + transcript) for the customer
 * frontend: restores history on load and picks up AGENT replies after a
 * human handoff.
 *
 * GET /chat/session/{id}
 *   returns: { id, status, messages: [{ id, sender, content, timestamp }] }
 *
 * Throws when the session does not exist (404) so callers can fall back.
 *
 * @param {number|string} sessionId
 */
export async function fetchSessionInfo(sessionId) {
  const { data } = await apiClient.get(`/chat/session/${sessionId}`)
  return data
}

/**
 * Ask the backend to reset/clear its chat session state.
 *
 * DELETE /chat/session
 *
 * Resolves quietly when the backend has no such endpoint, so clearing the
 * UI never depends on the backend being reachable.
 *
 * @returns {Promise<void>}
 */
export async function resetBackendSession() {
  try {
    await apiClient.delete('/chat/session')
  } catch (err) {
    if (
      err.response &&
      (err.response.status === 404 || err.response.status === 405)
    ) {
      return // endpoint not implemented on this backend yet
    }
    // Intentional: a failed backend reset must not block clearing the UI
  }
}

/**
 * Turn an Axios error into a user-friendly message, preferring the
 * structured error the backend returns (GlobalExceptionHandler).
 *
 * @param {import('axios').AxiosError} err
 * @returns {string}
 */
export function buildErrorMessage(err) {
  if (!err.response) {
    return (
      'Could not reach the backend. Make sure the Spring Boot server is ' +
      'running on port 8080, then try again.'
    )
  }
  const status = err.response.status
  const data = err.response.data
  if (data && typeof data.message === 'string') {
    return `Something went wrong (${status}): ${data.message}`
  }
  return `Request failed with status ${status}. Please try again.`
}
