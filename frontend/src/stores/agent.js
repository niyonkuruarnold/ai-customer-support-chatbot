import { defineStore } from 'pinia'
import * as agentApi from '../api/agent'
import * as adminApi from '../api/admin'
import { setMaintenanceAuth, clearMaintenanceAuth } from '../api/maintenance'

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
    /** Authenticate with HTTP Basic and load the ticket queue. */
    async login(username, password) {
      agentApi.setAgentAuth(username, password)
      // The knowledge base manager reuses the same Basic credentials
      adminApi.setAdminAuth(username, password)
      setMaintenanceAuth(username, password)
      this.agentName = username
      this.error = null
      // Fetch the authenticated user's role from the backend
      try {
        const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
        const me = await fetch(`${apiBase}/users/me`, {
          headers: { Authorization: `Basic ${btoa(`${username}:${password}`)}` },
        })
        if (me.ok) {
          const profile = await me.json()
          this.userId = profile.id || null
          this.userRole = profile.role || 'AGENT'
          localStorage.setItem('ai-support-chat:role', this.userRole)
        } else {
          // Fallback: assume AGENT if /api/users/me is unavailable
          this.userRole = 'AGENT'
          localStorage.setItem('ai-support-chat:role', this.userRole)
        }
      } catch {
        this.userRole = 'AGENT'
        localStorage.setItem('ai-support-chat:role', this.userRole)
      }
      await this.fetchTickets({ throwOnError: true })
      this.authenticated = true
      this.startPolling()
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
      }
    },
  },
})
