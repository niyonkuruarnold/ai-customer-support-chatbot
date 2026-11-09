import { defineStore } from 'pinia'
import * as adminApi from '../api/admin'

/**
 * Knowledge base store (RAG document manager).
 *
 * Auth is shared with the agent workspace: the agent store's login()/logout()
 * sets/clears the admin Basic credentials, so this store never manages
 * credentials itself. A 401 from any request flips `needsAuth` so the UI can
 * prompt the user to sign in again.
 */
export const useKnowledgeBaseStore = defineStore('knowledgeBase', {
  state: () => ({
    documents: [],
    chunks: [],
    loading: false,
    uploading: false,
    error: null,
    needsAuth: false,
  }),

  getters: {
    totalChunks: (state) => state.chunks.length,
    /** Chunks belonging to a document id (for per-document previews). */
    chunksFor: (state) => (documentId) =>
      state.chunks.filter((c) => c.documentId === documentId),
  },

  actions: {
    /** Load documents + indexed chunks. */
    async fetchAll() {
      this.loading = true
      this.error = null
      try {
        const [documents, chunks] = await Promise.all([
          adminApi.fetchDocuments(),
          adminApi.fetchChunks(),
        ])
        this.documents = documents
        this.chunks = chunks
      } catch (err) {
        this.handleError(err)
      } finally {
        this.loading = false
      }
    },

    /**
     * Upload + index a file. Returns true on success, false on failure.
     * @param {File} file
     * @param {string} [title]
     */
    async uploadFile(file, title) {
      this.uploading = true
      this.error = null
      try {
        await adminApi.uploadDocument(file, title)
        await this.fetchAll()
        return true
      } catch (err) {
        this.handleError(err)
        return false
      } finally {
        this.uploading = false
      }
    },

    /**
     * Index raw pasted text. Returns true on success, false on failure.
     * @param {string} title
     * @param {string} content
     */
    async addText(title, content) {
      this.uploading = true
      this.error = null
      try {
        await adminApi.addTextDocument(title, content)
        await this.fetchAll()
        return true
      } catch (err) {
        this.handleError(err)
        return false
      } finally {
        this.uploading = false
      }
    },

    /** Delete a document and its chunks from the vector store. */
    async removeDocument(id) {
      this.error = null
      try {
        await adminApi.deleteDocument(id)
        await this.fetchAll()
        return true
      } catch (err) {
        this.handleError(err)
        return false
      }
    },

    handleError(err) {
      if (err?.status === 401) {
        this.needsAuth = true
        this.error = 'Session expired — please sign in again.'
        return
      }
      if (err?.response?.data?.message) {
        this.error = err.response.data.message
        return
      }
      this.error =
        err?.message || 'Something went wrong. Please try again.'
    },

    clearError() {
      this.error = null
      this.needsAuth = false
    },
  },
})
