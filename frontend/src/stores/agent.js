import { defineStore } from 'pinia'
import * as agentApi from '../api/agent'
import * as adminApi from '../api/admin'
import { setMaintenanceAuth, clearMaintenanceAuth } from '../api/maintenance'
import { setAnalyticsAuth, clearAnalyticsAuth } from '../api/analytics'
import { setReservationAuth, clearReservationAuth } from '../api/reservation'
import { setReviewAuth, clearReviewAuth } from '../api/review'
import { API_BASE } from '../api/client'

// How often the agent workspace refreshes the ticket queue + the open
// conversation, so new escalations and customer messages appear live.
const POLL_INTERVAL_MS = 5000

/**
 * Agent workspace store.
 *
 * Agent credentials are held in the Axios client (in memory) and never
 * persisted. A 401 from any request flips `authenticated` back to false so
 * the workspace shows the sign-in form again.
 *
 * While authenticated, the store polls the backend (structured polling —
 * the same mechanism the customer chat uses) so the queue picks up newly
 * escalated tickets and the open conversation picks up new customer
 * messages without a manual refresh.
 */
export const useAgentStore = defineStore('agent', {
  state: () => ({
    agentName: '',
    authenticated: false,
    userId: null,
    userRole: (() => {
      const stored = localStorage.getItem('ai-support-chat:role')
      // Migrate legacy 'USER' role to 'CUSTOMER'
      if (stored === 'USER') {
        localStorage.setItem('ai-support-chat:role', 'CUSTOMER')
        return 'CUSTOMER'
      }
      return stored || 'CUSTOMER'
    })(),
    tickets: [],
    activeTicket: null, // AgentTicketDetailDto
    loading: false,
    activeLoading: false,
    error: null,
    activeError: null,
    pollTimer: null,
  }),

  getters: {
    isAdmin: (state) => state.userRole === 'ADMIN',
    isAgent: (state) => state.userRole === 'AGENT',
    isUser: (state) => !state.userRole || state.userRole === 'CUSTOMER',
    activeMessages: (state) => state.activeTicket?.messages ?? [],
    activeNotes: (state) => state.activeTicket?.internalNotes ?? [],
    activeSummary: (state) => state.activeTicket?.aiSummary ?? '',
    activeSentiment: (state) => state.activeTicket?.sentiment ?? null,
    activeStatus: (state) => state.activeTicket?.status ?? null,
    activeIsAssigned: (state) => Boolean(state.activeTicket?.assignedAgent),
    escalatedCount: (state) =>
      state.tickets.filter((t) => t.status === 'ESCALATED').length,
  },

  actions: {
    /** Exchange credentials for a Bearer token, then load the user's workspace. */
    async login(username, password) {
      this.stopPolling()
      this.authenticated = false
      this.tickets = []
      this.activeTicket = null
      this.error = null
      try {
        const tokenResponse = await fetch(`${API_BASE}/auth/token`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password }),
        })
        if (!tokenResponse.ok) {
          const error = new Error('Authentication failed')
          error.status = tokenResponse.status
          throw error
        }
        const { accessToken } = await tokenResponse.json()
        if (!accessToken) throw new Error('Authentication response did not include an access token')

        agentApi.setAgentAuth(accessToken)
        adminApi.setAdminAuth(accessToken)
        setMaintenanceAuth(accessToken)
        setAnalyticsAuth(accessToken)
        setReservationAuth(accessToken)
        setReviewAuth(accessToken)
        this.agentName = username

        const profileResponse = await fetch(`${API_BASE}/users/me`, {
          headers: { Authorization: `Bearer ${accessToken}` },
        })
        if (!profileResponse.ok) {
          const error = new Error('Could not load the authenticated user profile')
          error.status = profileResponse.status
          throw error
        }
        const profile = await profileResponse.json()
        this.userId = profile.id || null
        this.userRole = profile.role || 'AGENT'
        localStorage.setItem('ai-support-chat:role', this.userRole)
        if (this.userRole !== 'CUSTOMER') {
          await this.fetchTickets({ throwOnError: true })
          this.authenticated = true
          this.startPolling()
        } else {
          this.authenticated = true
        }
      } catch (error) {
        this.logout()
        throw error
      }
    },

    /** Dev-only: switch role without backend auth. */
    setUserRole(role) {
      this.userRole = role
      localStorage.setItem('ai-support-chat:role', role)
    },

    logout() {
      this.stopPolling()
      agentApi.clearAgentAuth()
      adminApi.clearAdminAuth()
      clearMaintenanceAuth()
      clearAnalyticsAuth()
      clearReservationAuth()
      clearReviewAuth()
      this.authenticated = false
      this.agentName = ''
      this.userId = null
      this.userRole = 'CUSTOMER'
      try { localStorage.setItem('ai-support-chat:role', 'CUSTOMER') } catch { /* ignore */ }
      this.tickets = []
      this.activeTicket = null
      this.error = null
      this.activeError = null
    },

    /**
     * Begin refreshing the queue + open conversation every few seconds.
     * No-op while unauthenticated or already polling.
     */
    startPolling() {
      this.stopPolling()
      if (!this.authenticated) return
      this.pollTimer = setInterval(() => this.pollActive(), POLL_INTERVAL_MS)
    },

    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },

    /**
     * One polling tick: refresh the queue and, when a ticket is open,
     * re-fetch its transcript so new customer messages appear live.
     * Failures are silent — the next tick retries.
     */
    async pollActive() {
      if (!this.authenticated) return
      try {
        this.tickets = await agentApi.fetchTickets()
      } catch (err) {
        this.handleAuthFailure(err)
        return
      }
      if (this.activeTicket) {
        try {
          const fresh = await agentApi.fetchTicketDetail(this.activeTicket.id)
          // Only swap the ticket when something actually changed, so the
          // conversation feed doesn't re-render and scroll on every tick.
          if (
            fresh.status !== this.activeTicket.status ||
            JSON.stringify(fresh.messages) !== JSON.stringify(this.activeTicket.messages) ||
            JSON.stringify(fresh.internalNotes ?? []) !==
              JSON.stringify(this.activeTicket.internalNotes ?? [])
          ) {
            this.activeTicket = fresh
          }
        } catch (err) {
          this.handleAuthFailure(err)
        }
      }
    },

    async fetchTickets({ throwOnError = false } = {}) {
      this.loading = true
      this.error = null
      try {
        this.tickets = await agentApi.fetchTickets()
      } catch (err) {
        this.handleAuthFailure(err)
        if (throwOnError) throw err
      } finally {
        this.loading = false
      }
    },

    async openTicket(id) {
      this.activeLoading = true
      this.activeError = null
      try {
        this.activeTicket = await agentApi.fetchTicketDetail(id)
      } catch (err) {
        this.handleAuthFailure(err)
        this.activeError =
          err?.status === 401
            ? 'Session expired — please log in again.'
            : 'Could not load this ticket.'
      } finally {
        this.activeLoading = false
      }
    },

    async takeOver() {
      const ticket = this.activeTicket
      if (!ticket) return
      try {
        this.activeTicket = await agentApi.takeOverTicket(ticket.id)
        await this.refreshList()
        return true
      } catch (err) {
        this.handleAuthFailure(err)
        this.activeError = 'Takeover failed. Please try again.'
        return false
      }
    },

    async sendReply(text) {
      const content = (text ?? '').trim()
      const ticket = this.activeTicket
      if (!ticket || !content) return false
      try {
        this.activeTicket = await agentApi.sendAgentReply(ticket.id, content)
        await this.refreshList()
        return true
      } catch (err) {
        this.handleAuthFailure(err)
        this.activeError = 'Reply failed. Please try again.'
        return false
      }
    },

    async addNote(content) {
      const text = (content ?? '').trim()
      const ticket = this.activeTicket
      if (!ticket || !text) return
      try {
        this.activeTicket = await agentApi.addTicketNote(ticket.id, text)
      } catch (err) {
        this.handleAuthFailure(err)
        this.activeError = 'Could not save the note.'
      }
    },

    async resolve() {
      const ticket = this.activeTicket
      if (!ticket) return
      try {
        this.activeTicket = await agentApi.resolveTicket(ticket.id)
        await this.refreshList()
      } catch (err) {
        this.handleAuthFailure(err)
        this.activeError = 'Could not resolve the ticket.'
      }
    },

    async refreshList() {
      try {
        this.tickets = await agentApi.fetchTickets()
      } catch {
        // Keep the current list; the next refresh will retry
      }
    },

    handleAuthFailure(err) {
      if (err?.status === 401) {
        this.stopPolling()
        this.authenticated = false
        this.agentName = ''
        agentApi.clearAgentAuth()
        adminApi.clearAdminAuth()
        clearMaintenanceAuth()
        clearAnalyticsAuth()
      }
    },
  },
})
