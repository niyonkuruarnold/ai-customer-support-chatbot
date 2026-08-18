import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useReviewStore } from './review'

// Mock the API module
vi.mock('../api/review', () => ({
  submitReview: vi.fn(),
  getReview: vi.fn(),
  getReviewsByTool: vi.fn(),
  getReviewsByUser: vi.fn(),
  getAverageRatingForTool: vi.fn(),
  getAverageRatingForUser: vi.fn(),
  checkReviewStatus: vi.fn(),
}))

import {
  submitReview,
  getReviewsByTool,
  getReviewsByUser,
  getAverageRatingForTool,
  getAverageRatingForUser,
  checkReviewStatus,
} from '../api/review'

describe('Review Store', () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useReviewStore()
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('has empty initial state', () => {
      expect(store.toolReviews).toEqual([])
      expect(store.userReviews).toEqual([])
      expect(store.toolAverage).toBeNull()
      expect(store.userAverage).toBeNull()
      expect(store.reviewStatusMap.size).toBe(0)
      expect(store.isLoading).toBe(false)
      expect(store.error).toBeNull()
    })
  })

  describe('getters', () => {
    it('isReviewed returns false for unknown reservation', () => {
      expect(store.isReviewed(1)).toBe(false)
    })

    it('isReviewed returns true for reviewed reservation', () => {
      store.reviewStatusMap.set(1, true)
      expect(store.isReviewed(1)).toBe(true)
    })

    it('formattedToolAverage returns null when no average', () => {
      expect(store.formattedToolAverage).toBeNull()
    })

    it('formattedToolAverage returns formatted value', () => {
      store.toolAverage = { averageRating: 4.567, reviewCount: 10 }
      expect(store.formattedToolAverage).toBe('4.6')
    })
  })

  describe('actions', () => {
    describe('fetchToolReviews', () => {
      it('loads reviews for a tool', async () => {
        const mockReviews = [
          { id: 1, toolId: 1, rating: 5, comment: 'Great!' },
        ]
        getReviewsByTool.mockResolvedValue(mockReviews)

        await store.fetchToolReviews(1)

        expect(store.toolReviews).toEqual(mockReviews)
        expect(store.isLoading).toBe(false)
        expect(store.error).toBeNull()
      })

      it('handles errors', async () => {
        getReviewsByTool.mockRejectedValue(new Error('Network error'))

        await store.fetchToolReviews(1)

        expect(store.toolReviews).toEqual([])
        expect(store.error).toBe('Network error')
        expect(store.isLoading).toBe(false)
      })
    })

    describe('fetchUserReviews', () => {
      it('loads reviews by user', async () => {
        const mockReviews = [
          { id: 1, reviewerId: 10, rating: 4, comment: 'Nice tool' },
        ]
        getReviewsByUser.mockResolvedValue(mockReviews)

        await store.fetchUserReviews(10)

        expect(store.userReviews).toEqual(mockReviews)
      })
    })

    describe('fetchToolAverage', () => {
      it('loads average rating for a tool', async () => {
        const mockAvg = { averageRating: 4.5, reviewCount: 8 }
        getAverageRatingForTool.mockResolvedValue(mockAvg)

        await store.fetchToolAverage(1)

        expect(store.toolAverage).toEqual(mockAvg)
      })

      it('handles errors gracefully', async () => {
        getAverageRatingForTool.mockRejectedValue(new Error('Not found'))

        await store.fetchToolAverage(1)

        expect(store.toolAverage).toBeNull()
        expect(store.error).toBe('Not found')
      })
    })

    describe('fetchUserAverage', () => {
      it('loads average rating for a user', async () => {
        const mockAvg = { averageRating: 4.0, reviewCount: 5 }
        getAverageRatingForUser.mockResolvedValue(mockAvg)

        await store.fetchUserAverage(10)

        expect(store.userAverage).toEqual(mockAvg)
      })
    })

    describe('checkReservationReviewStatus', () => {
      it('returns true if reservation is reviewed', async () => {
        checkReviewStatus.mockResolvedValue({ reservationId: 1, reviewed: true })

        const result = await store.checkReservationReviewStatus(1)

        expect(result).toBe(true)
        expect(store.isReviewed(1)).toBe(true)
      })

      it('returns false if reservation is not reviewed', async () => {
        checkReviewStatus.mockResolvedValue({ reservationId: 1, reviewed: false })

        const result = await store.checkReservationReviewStatus(1)

        expect(result).toBe(false)
        expect(store.isReviewed(1)).toBe(false)
      })
    })

    describe('submitNewReview', () => {
      it('submits a review successfully', async () => {
        const mockReview = {
          id: 1,
          toolId: 1,
          reviewerId: 10,
          reservationId: 100,
          rating: 5,
          comment: 'Excellent!',
        }
        submitReview.mockResolvedValue(mockReview)
        getAverageRatingForTool.mockResolvedValue({ averageRating: 5.0, reviewCount: 1 })

        const payload = {
          toolId: 1,
          reviewerId: 10,
          reservationId: 100,
          rating: 5,
          comment: 'Excellent!',
        }

        const result = await store.submitNewReview(payload)

        expect(result).toEqual(mockReview)
        expect(store.userReviews).toContainEqual(mockReview)
        expect(store.isReviewed(100)).toBe(true)
        expect(store.toolAverage).toEqual({ averageRating: 5.0, reviewCount: 1 })
        expect(store.isLoading).toBe(false)
      })

      it('handles submission errors', async () => {
        submitReview.mockRejectedValue({
          response: { data: { message: 'Already reviewed' } },
        })

        const payload = {
          toolId: 1,
          reviewerId: 10,
          reservationId: 100,
          rating: 5,
        }

        await expect(store.submitNewReview(payload)).rejects.toThrow()
        expect(store.error).toBe('Already reviewed')
        expect(store.isLoading).toBe(false)
      })
    })

    describe('clearError', () => {
      it('clears the error state', () => {
        store.error = 'Some error'
        store.clearError()
        expect(store.error).toBeNull()
      })
    })
  })
})
