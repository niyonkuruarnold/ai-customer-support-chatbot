import { defineStore } from 'pinia'
import {
  submitReview,
  getReviewsByTool,
  getReviewsByUser,
  getAverageRatingForTool,
  getAverageRatingForUser,
  checkReviewStatus,
} from '../api/review'

/**
 * Review state store for the tool rating system.
 *
 * Tracks reviews for tools and provides actions for
 * submitting reviews, fetching ratings, and checking review status.
 */
export const useReviewStore = defineStore('review', {
  state: () => ({
    /** @type {Array} Reviews for the currently viewed tool */
    toolReviews: [],
    /** @type {Array} Reviews submitted by the current user */
    userReviews: [],
    /** @type {Object|null} Average rating for the currently viewed tool */
    toolAverage: null,
    /** @type {Object|null} Average rating for the current user */
    userAverage: null,
    /** @type {Map} Review status by reservation ID */
    reviewStatusMap: new Map(),
    /** Loading indicator for async operations */
    isLoading: false,
    /** Last error message */
    error: null,
  }),

  getters: {
    /** Get review status for a specific reservation */
    isReviewed: (state) => (reservationId) => {
      return state.reviewStatusMap.get(reservationId) || false
    },

    /** Get average rating as a formatted number */
    formattedToolAverage: (state) => {
      if (!state.toolAverage || state.toolAverage.averageRating === null) return null
      return state.toolAverage.averageRating.toFixed(1)
    },
  },

  actions: {
    /** Load all reviews for a specific tool. */
    async fetchToolReviews(toolId) {
      this.isLoading = true
      this.error = null
      try {
        this.toolReviews = await getReviewsByTool(toolId)
      } catch (err) {
        this.error = err.message || 'Failed to load reviews'
      } finally {
        this.isLoading = false
      }
    },

    /** Load all reviews submitted by a user. */
    async fetchUserReviews(userId) {
      this.isLoading = true
      this.error = null
      try {
        this.userReviews = await getReviewsByUser(userId)
      } catch (err) {
        this.error = err.message || 'Failed to load user reviews'
      } finally {
        this.isLoading = false
      }
    },

    /** Load average rating for a tool. */
    async fetchToolAverage(toolId) {
      this.error = null
      try {
        this.toolAverage = await getAverageRatingForTool(toolId)
      } catch (err) {
        this.error = err.message || 'Failed to load average rating'
        this.toolAverage = null
      }
    },

    /** Load average rating for a user. */
    async fetchUserAverage(userId) {
      this.error = null
      try {
        this.userAverage = await getAverageRatingForUser(userId)
      } catch (err) {
        this.error = err.message || 'Failed to load user average rating'
        this.userAverage = null
      }
    },

    /** Check if a reservation has been reviewed. */
    async checkReservationReviewStatus(reservationId) {
      this.error = null
      try {
        const result = await checkReviewStatus(reservationId)
        this.reviewStatusMap.set(reservationId, result.reviewed)
        return result.reviewed
      } catch (err) {
        this.error = err.message || 'Failed to check review status'
        return false
      }
    },

    /** Submit a review for a completed tool borrow. */
    async submitNewReview(payload) {
      this.isLoading = true
      this.error = null
      try {
        const created = await submitReview(payload)
        // Add to user reviews if loaded
        this.userReviews.unshift(created)
        // Mark reservation as reviewed
        this.reviewStatusMap.set(payload.reservationId, true)
        // Refresh tool average after submitting
        await this.fetchToolAverage(payload.toolId)
        return created
      } catch (err) {
        this.error =
          err.response?.data?.message ||
          err.message ||
          'Failed to submit review'
        throw err
      } finally {
        this.isLoading = false
      }
    },

    /** Clear any stored error. */
    clearError() {
      this.error = null
    },
  },
})
