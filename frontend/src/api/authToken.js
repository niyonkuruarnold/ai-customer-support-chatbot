let accessToken = null

/**
 * Handlers notified when an authenticated API client receives HTTP 401.
 *
 * This module is the shared dependency of every bearer-attaching axios
 * client, so the response interceptor installed by `attachBearerToken()`
 * is the single place that sees all session-expiry 401s. App.vue registers
 * a handler that swaps the dashboard for the in-page sign-in card — the
 * backend never sends `WWW-Authenticate` (SecurityConfig), so the browser
 * has no reason to open its native Basic-Auth dialog.
 */
const unauthorizedHandlers = new Set()
const unauthorizedClients = new WeakSet()

/**
 * Register a callback for 401 responses. Returns an unregister function.
 */
export function registerUnauthorizedHandler(handler) {
  unauthorizedHandlers.add(handler)
  return () => unauthorizedHandlers.delete(handler)
}

function notifyUnauthorized() {
  unauthorizedHandlers.forEach((handler) => {
    try {
      handler()
    } catch {
      // A broken handler must never swallow the original 401 error.
    }
  })
}

export function attachUnauthorizedHandler(client) {
  if (unauthorizedClients.has(client)) return
  unauthorizedClients.add(client)

  client.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error?.response?.status === 401) notifyUnauthorized()
      return Promise.reject(error)
    },
  )
}

export function setAccessToken(token) {
  accessToken = token
}

export function clearAccessToken() {
  accessToken = null
}

export function attachBearerToken(client) {
  client.interceptors.request.use((config) => {
    config.headers = config.headers ?? {}
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`
    } else {
      delete config.headers.Authorization
    }
    return config
  })
  // Response interceptor: surface session expiry to the app instead of
  // letting the error bubble up as a silent failed request.
  attachUnauthorizedHandler(client)
}