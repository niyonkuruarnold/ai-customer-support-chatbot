import { ref } from 'vue'

const STORAGE_KEY = 'codafriqa_chat_session_id'

/**
 * Manages the customer chat session ID in localStorage.
 *
 * - `initSession()` reads the persisted ID or generates a new one (prefixed `sess_`).
 * - `clearSession()` removes the stored ID and resets reactive state.
 */
export function useCustomerChatSession() {
  const sessionId = ref<string | null>(null)

  function initSession(): string {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      sessionId.value = stored
      return stored
    }
    const id = `sess_${crypto.randomUUID()}`
    localStorage.setItem(STORAGE_KEY, id)
    sessionId.value = id
    return id
  }

  function clearSession(): void {
    localStorage.removeItem(STORAGE_KEY)
    sessionId.value = null
  }

  return { sessionId, initSession, clearSession }
}
