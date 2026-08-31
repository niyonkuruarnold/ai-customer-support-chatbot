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
 * When the answer was grounded in retrieved knowledge base context the
 * response also carries `ragUsed` and `contextReferences` (source documents
 * from the pgvector store) for citation display.
 *
 * POST /v1/chat/message
 *   body:    { message, sessionId? }
 *   returns: { response, sessionId, status, ragUsed, contextReferences }
 *
 * @param {string} message
 * @param {string|null} sessionId
 * @returns {Promise<{response: string, sessionId: number|null, status: string, ragUsed: boolean, contextReferences: Array}>}
 */
export async function sendChatMessage(message, sessionId) {
  const { data } = await apiClient.post('/v1/chat/message', { message, sessionId })
  return {
    ...data,
    // `response` is the current backend contract; `content` keeps the
    // client compatible with message-shaped chat endpoints.
    response: data.response ?? data.content ?? '',
  }
}

/**
 * Fetch the full session state (status + transcript) for the customer
 * frontend: restores history on load and picks up AGENT replies after a
 * human handoff.
 *
 * GET /v1/chat/session/{id}
 *   returns: { id, status, messages: [{ id, sender, content, timestamp }] }
 *
 * Throws when the session does not exist (404) so callers can fall back.
 *
 * @param {number|string} sessionId
 */
export async function fetchSessionInfo(sessionId) {
  const { data } = await apiClient.get(`/v1/chat/session/${sessionId}`)
  return data
}

/**
 * Ask the backend to reset/clear its chat session state.
 *
 * DELETE /v1/chat/session
 *
 * Resolves quietly when the backend has no such endpoint, so clearing the
 * UI never depends on the backend being reachable.
 *
 * @returns {Promise<void>}
 */
export async function resetBackendSession() {
  try {
    await apiClient.delete('/v1/chat/session')
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
 * Ask the backend to close/end a chat session (mark it as CLOSED).
 * Called by the customer frontend when starting a new conversation.
 *
 * POST /v1/chat/session/{id}/close
 *
 * Resolves quietly when the backend has no such endpoint, so clearing
 * the UI never depends on the backend being reachable.
 *
 * @param {number|string} sessionId
 * @returns {Promise<void>}
 */
export async function closeChatSession(sessionId) {
  if (!sessionId) return
  try {
    await apiClient.post(`/v1/chat/session/${sessionId}/close`)
  } catch (err) {
    // Intentional: a failed backend close must not block clearing the UI
  }
}

/**
 * Fetch dynamically generated suggested questions from the backend.
 *
 * The questions are extracted from the currently indexed knowledge base
 * content in the vector store, so they always reflect the latest uploaded
 * documents.  Falls back to the provided defaults if the backend is
 * unreachable.
 *
 * GET /v1/chat/suggested-questions
 *   returns: { questions: string[], fromKnowledgeBase: boolean }
 *
 * @param {string[]} fallback  default questions if the request fails
 * @returns {Promise<string[]>}
 */
export async function fetchSuggestedQuestions(fallback = []) {
  try {
    const { data } = await apiClient.get('/v1/chat/suggested-questions')
    return data?.questions?.length ? data.questions : fallback
  } catch {
    // Backend unreachable or endpoint not yet deployed — use fallbacks
    return fallback
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
  const data = err?.response?.data
  if (typeof data === 'string' && data.trim()) return data
  if (data && typeof data === 'object') {
    if (typeof data.response === 'string' && data.response.trim()) return data.response
    if (typeof data.content === 'string' && data.content.trim()) return data.content
    if (typeof data.message === 'string' && data.message.trim()) return data.message
  }
  if (typeof err?.message === 'string' && err.message.trim()) return err.message
  return 'Unknown chat request error'
}
