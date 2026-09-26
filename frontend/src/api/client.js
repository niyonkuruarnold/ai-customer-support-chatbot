/**
 * Shared API base-URL resolution for every axios client / fetch call.
 *
 * - `VITE_API_BASE_URL` wins when set (e.g. http://localhost:8080/api to hit
 *   the backend directly).
 * - Otherwise the same-origin `/api` path is used, which is proxied by the
 *   Vite dev server (`/api` → localhost:8080, see vite.config.js) and by the
 *   production Nginx server (`/api/` → backend, see nginx.conf). Relative
 *   requests are same-origin, so they also avoid CORS entirely.
 */
const configured = import.meta.env.VITE_API_BASE_URL

export const API_BASE = (
  typeof configured === 'string' && configured.trim() !== ''
    ? configured
    : '/api'
).replace(/\/+$/, '')

/**
 * True when API_BASE is an absolute http(s) URL (direct backend access).
 * Same-origin `/api` means any websocket URL must also stay same-origin.
 */
export const API_IS_ABSOLUTE = /^https?:\/\//i.test(API_BASE)

/** Origin of an absolute API_BASE (`http://localhost:8080/api` → `http://localhost:8080`), else ''. */
export const API_ORIGIN = API_IS_ABSOLUTE ? API_BASE.replace(/\/api\/?$/i, '') : ''
