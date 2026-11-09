import { defineStore } from 'pinia'
import {
  approveReservation,
  checkAvailability,
  checkoutReservation,
  createReservation,
  getMyReservations,
  rejectReservation,
  returnReservation,
} from '../api/reservation'

/**
 * Reservation state store for the tool borrowing system.
 *
 * Tracks the current user's reservations and provides actions for
 * creating, approving, checking out, and returning tools.
 */
export const useReservationStore = defineStore('reservation', {
  state: () => ({
    /** @type {Array} All reservations for the current borrower */
    reservations: [],
    /** @type {Object|null} Last availability check result */
    availability: null,
    /** Loading indicator for async operations */
    isLoading: false,
    /** Last error message */
    error: null,
  }),

  getters: {
    /** Active reservations (PENDING, APPROVED, CHECKED_OUT) */
    activeReservations: (state) =>
      state.reservations.filter((r) =>
        ['PENDING', 'APPROVED', 'CHECKED_OUT'].includes(r.status),
      ),

    /** Past reservations (RETURNED, REJECTED) */
    pastReservations: (state) =>
      state.reservations.filter((r) =>
        ['RETURNED', 'REJECTED'].includes(r.status),
      ),

    /** Reservations waiting for owner approval */
    pendingApprovals: (state) =>
      state.reservations.filter((r) => r.status === 'PENDING'),

    /** Currently checked out tools */
    checkedOut: (state) =>
      state.reservations.filter((r) => r.status === 'CHECKED_OUT'),
  },

  actions: {
    /** Load all reservations for the current borrower. */
    async fetchMyReservations(borrowerId) {
      this.isLoading = true
      this.error = null
      try {
        this.reservations = await getMyReservations(borrowerId)
      } catch (err) {
        this.error = err.message || 'Failed to load reservations'
      } finally {
        this.isLoading = false
      }
    },

    /** Check if a tool is available for a date range. */
    async checkToolAvailability(toolId, startDate, endDate) {
      this.error = null
      try {
        this.availability = await checkAvailability(toolId, startDate, endDate)
        return this.availability.available
      } catch (err) {
        this.error = err.message || 'Failed to check availability'
        this.availability = null
        return false
      }
    },

    /** Create a new reservation request. */
    async requestReservation(payload) {
      this.isLoading = true
      this.error = null
      try {
        const created = await createReservation(payload)
        this.reservations.unshift(created)
        return created
      } catch (err) {
        this.error =
          err.response?.data?.message ||
          err.message ||
          'Failed to create reservation'
        throw err
      } finally {
        this.isLoading = false
      }
    },

    /** Approve a pending reservation (tool owner action). */
    async approve(id) {
      this.error = null
      try {
        const updated = await approveReservation(id)
        this.replaceInList(updated)
        return updated
      } catch (err) {
        this.error = err.message || 'Failed to approve reservation'
        throw err
      }
    },

    /** Reject a pending reservation. */
    async reject(id) {
      this.error = null
      try {
        const updated = await rejectReservation(id)
        this.replaceInList(updated)
        return updated
      } catch (err) {
        this.error = err.message || 'Failed to reject reservation'
        throw err
      }
    },

    /** Check out a tool (mark as picked up). */
    async checkout(id) {
      this.error = null
      try {
        const updated = await checkoutReservation(id)
        this.replaceInList(updated)
        return updated
      } catch (err) {
        this.error = err.message || 'Failed to check out tool'
        throw err
      }
    },

    /** Return a tool. */
    async returnTool(id) {
      this.error = null
      try {
        const updated = await returnReservation(id)
        this.replaceInList(updated)
        return updated
      } catch (err) {
        this.error = err.message || 'Failed to return tool'
        throw err
      }
    },

    /** Replace a reservation in the local list (after a state transition). */
    replaceInList(updated) {
      const idx = this.reservations.findIndex((r) => r.id === updated.id)
      if (idx !== -1) {
        this.reservations[idx] = updated
      }
    },

    /** Clear any stored error. */
    clearError() {
      this.error = null
    },
  },
})
