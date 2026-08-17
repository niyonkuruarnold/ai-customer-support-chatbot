import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useKnowledgeBaseStore } from './knowledgeBase'
import * as adminApi from '../api/admin'

vi.mock('../api/admin', () => ({
  setAdminAuth: vi.fn(),
  clearAdminAuth: vi.fn(),
  uploadDocument: vi.fn(),
  addTextDocument: vi.fn(),
  fetchDocuments: vi.fn(),
  fetchChunks: vi.fn(),
  deleteDocument: vi.fn(),
}))

function document(overrides = {}) {
  return {
    id: 1,
    title: 'Shipping policy',
    sourceType: 'TEXT',
    fileName: null,
    chunkCount: 2,
    createdAt: '2026-08-16T09:00:00',
    ...overrides,
  }
}

function chunk(overrides = {}) {
  return {
    id: 10,
    documentId: 1,
    title: 'Shipping policy',
    sourceType: 'TEXT',
    chunkIndex: 0,
    content: 'Orders ship within 24 hours.',
    createdAt: '2026-08-16T09:00:01',
    ...overrides,
  }
}

function authError(status = 401) {
  const err = new Error('Unauthorized')
  err.status = status
  return err
}

function messageError(status, message) {
  const err = new Error(message)
  err.response = { status, data: { message } }
  return err
}

describe('knowledge base store', () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    store = useKnowledgeBaseStore()
  })

  describe('fetchAll', () => {
    it('loads documents and chunks together', async () => {
      adminApi.fetchDocuments.mockResolvedValue([document()])
      adminApi.fetchChunks.mockResolvedValue([chunk()])

      await store.fetchAll()

      expect(store.documents).toHaveLength(1)
      expect(store.chunks).toHaveLength(1)
      expect(store.totalChunks).toBe(1)
      expect(store.chunksFor(1)).toHaveLength(1)
      expect(store.chunksFor(99)).toHaveLength(0)
      expect(store.loading).toBe(false)
    })

    it('flags needsAuth on a 401', async () => {
      adminApi.fetchDocuments.mockRejectedValue(authError(401))

      await store.fetchAll()

      expect(store.needsAuth).toBe(true)
      expect(store.error).toContain('sign in again')
    })

    it('surfaces the backend error message', async () => {
      adminApi.fetchDocuments.mockRejectedValue(
        messageError(400, 'No readable text could be extracted from this document.'),
      )

      await store.fetchAll()

      expect(store.error).toContain('No readable text')
      expect(store.needsAuth).toBe(false)
    })
  })

  describe('uploadFile', () => {
    it('uploads a file and refreshes the list on success', async () => {
      adminApi.uploadDocument.mockResolvedValue(document({ id: 2, chunkCount: 3 }))
      adminApi.fetchDocuments.mockResolvedValue([document()])
      adminApi.fetchChunks.mockResolvedValue([])

      const file = new File(['# FAQ'], 'faq.md', { type: 'text/markdown' })
      const ok = await store.uploadFile(file, 'FAQ')

      expect(ok).toBe(true)
      expect(adminApi.uploadDocument).toHaveBeenCalledWith(file, 'FAQ')
      expect(adminApi.fetchDocuments).toHaveBeenCalled()
      expect(store.uploading).toBe(false)
    })

    it('records the error and returns false on failure', async () => {
      adminApi.uploadDocument.mockRejectedValue(
        messageError(400, 'Could not generate embeddings (is OPENAI_API_KEY set?)'),
      )

      const ok = await store.uploadFile(new File(['x'], 'x.txt'), 'X')

      expect(ok).toBe(false)
      expect(store.error).toContain('OPENAI_API_KEY')
    })
  })

  describe('addText', () => {
    it('indexes pasted text and clears nothing in the store itself', async () => {
      adminApi.addTextDocument.mockResolvedValue(document())
      adminApi.fetchDocuments.mockResolvedValue([document()])
      adminApi.fetchChunks.mockResolvedValue([chunk()])

      const ok = await store.addText('Shipping policy', 'Orders ship within 24 hours.')

      expect(ok).toBe(true)
      expect(adminApi.addTextDocument).toHaveBeenCalledWith(
        'Shipping policy',
        'Orders ship within 24 hours.',
      )
      expect(store.documents).toHaveLength(1)
    })

    it('returns false when indexing fails', async () => {
      adminApi.addTextDocument.mockRejectedValue(new Error('offline'))

      const ok = await store.addText('Title', 'Body')

      expect(ok).toBe(false)
      expect(store.error).toBeTruthy()
    })
  })

  describe('removeDocument', () => {
    it('deletes the document and refreshes the list', async () => {
      adminApi.deleteDocument.mockResolvedValue(undefined)
      adminApi.fetchDocuments.mockResolvedValue([])
      adminApi.fetchChunks.mockResolvedValue([])

      const ok = await store.removeDocument(1)

      expect(ok).toBe(true)
      expect(adminApi.deleteDocument).toHaveBeenCalledWith(1)
      expect(store.documents).toHaveLength(0)
    })

    it('keeps the list and reports failure on error', async () => {
      adminApi.deleteDocument.mockRejectedValue(new Error('offline'))

      const ok = await store.removeDocument(1)

      expect(ok).toBe(false)
    })
  })
})
