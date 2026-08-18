import { defineStore } from 'pinia'
import {
  getToolsByOwner,
  updateToolStatus,
  createMaintenanceLog,
  getMaintenanceLogsByTool,
  completeMaintenance,
  getMaintenanceStats,
} from '../api/maintenance'

/**
 * Maintenance state store for tool management and service records.
 *
 * Tracks tools, maintenance logs, and provides actions for
 * creating maintenance records, updating tool status, and completing maintenance.
 */
export const useMaintenanceStore = defineStore('maintenance', {
  state: () => ({
    /** @type {Array} All tools owned by the current user */
    tools: [],
    /** @type {Array} Maintenance logs for the currently selected tool */
    maintenanceLogs: [],
    /** @type {Object|null} Maintenance stats for the currently selected tool */
    currentToolStats: null,
    /** @type {Object|null} Currently selected tool for maintenance view */
    selectedTool: null,
    /** Loading indicator for async operations */
    isLoading: false,
    /** Last error message */
    error: null,
  }),

  getters: {
    /** Tools that are currently in maintenance */
    toolsInMaintenance: (state) =>
      state.tools.filter((t) => t.status === 'IN_MAINTENANCE'),

    /** Tools that are available for borrowing */
    availableTools: (state) =>
      state.tools.filter((t) => t.status === 'AVAILABLE'),

    /** Tools that are currently borrowed */
    borrowedTools: (state) =>
      state.tools.filter((t) => t.status === 'BORROWED'),
  },

  actions: {
    /** Load all tools for the current owner. */
    async fetchTools(ownerId) {
      this.isLoading = true
      this.error = null
      try {
        this.tools = await getToolsByOwner(ownerId)
      } catch (err) {
        this.error = err.message || 'Failed to load tools'
      } finally {
        this.isLoading = false
      }
    },

    /** Update a tool's availability status. */
    async updateToolAvailability(toolId, status) {
      this.error = null
      try {
        const updated = await updateToolStatus(toolId, status)
        this.replaceToolInList(updated)
        return updated
      } catch (err) {
        this.error =
          err.response?.data?.message ||
          err.message ||
          'Failed to update tool status'
        throw err
      }
    },

    /** Create a new maintenance log entry. */
    async addMaintenanceLog(payload) {
      this.isLoading = true
      this.error = null
      try {
        const created = await createMaintenanceLog(payload)
        // Refresh logs for this tool
        await this.fetchMaintenanceLogs(payload.toolId)
        // Refresh tools list to reflect status change
        return created
      } catch (err) {
        this.error =
          err.response?.data?.message ||
          err.message ||
          'Failed to create maintenance log'
        throw err
      } finally {
        this.isLoading = false
      }
    },

    /** Load maintenance logs for a specific tool. */
    async fetchMaintenanceLogs(toolId) {
      this.isLoading = true
      this.error = null
      try {
        this.maintenanceLogs = await getMaintenanceLogsByTool(toolId)
      } catch (err) {
        this.error = err.message || 'Failed to load maintenance logs'
      } finally {
        this.isLoading = false
      }
    },

    /** Complete maintenance for a tool (set to AVAILABLE). */
    async completeToolMaintenance(toolId) {
      this.error = null
      try {
        const updated = await completeMaintenance(toolId)
        this.replaceToolInList(updated)
        return updated
      } catch (err) {
        this.error =
          err.response?.data?.message ||
          err.message ||
          'Failed to complete maintenance'
        throw err
      }
    },

    /** Load maintenance stats for a tool. */
    async fetchToolStats(toolId) {
      this.error = null
      try {
        this.currentToolStats = await getMaintenanceStats(toolId)
      } catch (err) {
        this.error = err.message || 'Failed to load maintenance stats'
        this.currentToolStats = null
      }
    },

    /** Set the currently selected tool for maintenance view. */
    selectTool(tool) {
      this.selectedTool = tool
      this.maintenanceLogs = []
      this.currentToolStats = null
    },

    /** Clear selected tool. */
    clearSelectedTool() {
      this.selectedTool = null
      this.maintenanceLogs = []
      this.currentToolStats = null
    },

    /** Replace a tool in the local list (after status update). */
    replaceToolInList(updated) {
      const idx = this.tools.findIndex((t) => t.id === updated.id)
      if (idx !== -1) {
        this.tools[idx] = updated
      }
    },

    /** Clear any stored error. */
    clearError() {
      this.error = null
    },
  },
})
