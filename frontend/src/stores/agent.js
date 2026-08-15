import { defineStore } from 'pinia'
import * as agentApi from '../api/agent'

/**
 * Agent workspace store.
 *
 * Agent credentials are held in the Axios client (in memory) and never
 * persisted. A 401 from any request flips `authenticated` back to false so
 * the workspace shows the sign-in form again.
 */
export const useAgentStore = defineStore('agent', {
  state: () => ({
    agentName: '',
    authenticated: false,
    tickets: [],
    activeTicket: null, // AgentTicketDetailDto
    loading: false,
    activeLoading: false,
    error: null,
    activeError: null,
  }),

  getters: {
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
      this.agentName = username
      this.error = null
      await this.fetchTickets({ throwOnError: true })
      this.authenticated = true
    },

    logout() {
      agentApi.clearAgentAuth()
      this.authenticated = false
      this.agentName = ''
      this.tickets = []
      this.activeTicket = null
      this.error = null
      this.activeError = null
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
        this.authenticated = false
        this.agentName = ''
        agentApi.clearAgentAuth()
      }
    },
  },
})
