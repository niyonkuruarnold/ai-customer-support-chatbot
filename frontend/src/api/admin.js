import axios from 'axios'

/**
 * Axios client for the authenticated admin endpoints (knowledge base).
 * Uses the same HTTP Basic credentials as the agent workspace — the agent
 * store's login()/logout() keep this client in sync, so one sign-in covers
 * both areas. Credentials stay in memory only.
 */
const adminClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' },
  // Embedding + indexing of large documents can take a while
  timeout: 120000,
})

/** Set the HTTP Basic credentials used for all admin requests. */
export function setAdminAuth(username, password) {
  adminClient.defaults.headers.common.Authorization =
    `Basic ${btoa(`${username}:${password}`)}`
}

/** Clear admin credentials (logout). */
export function clearAdminAuth() {
  delete adminClient.defaults.headers.common.Authorization
}

/** Normalize 401 responses into an error carrying `status = 401`. */
function wrapAuthError(err) {
  if (err.response && err.response.status === 401) {
    const authError = new Error('Admin authentication required')
    authError.status = 401
    return authError
  }
  return err
}

async function request(fn) {
  try {
    return await fn()
  } catch (err) {
    throw wrapAuthError(err)
  }
}

/**
 * Upload a support document (.txt/.md/.pdf) and index it.
 *
 * POST /admin/documents/upload (multipart: file + optional title)
 * @param {File} file
 * @param {string} [title]
 */
export function uploadDocument(file, title) {
  const form = new FormData()
  form.append('file', file)
  if (title) form.append('title', title)
  return request(
    async () => (await adminClient.post('/admin/documents/upload', form)).data,
  )
}

/**
 * Index raw pasted FAQ/support text.
 * POST /admin/documents/text  ({ title, content })
 */
export function addTextDocument(title, content) {
  return request(
    async () =>
      (await adminClient.post('/admin/documents/text', { title, content })).data,
  )
}

/** GET /admin/documents -> KnowledgeDocumentDto[] */
export function fetchDocuments() {
  return request(async () => (await adminClient.get('/admin/documents')).data)
}

/** GET /admin/documents/chunks -> KnowledgeChunkDto[] */
export function fetchChunks() {
  return request(async () => (await adminClient.get('/admin/documents/chunks')).data)
}

/** DELETE /admin/documents/{id} */
export function deleteDocument(id) {
  return request(async () => adminClient.delete(`/admin/documents/${id}`))
}
