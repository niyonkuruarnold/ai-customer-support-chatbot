import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useMaintenanceStore } from './maintenance'

// Mock the API module
vi.mock('../api/maintenance', () => ({
  getToolsByOwner: vi.fn(),
  updateToolStatus: vi.fn(),
  createMaintenanceLog: vi.fn(),
  getMaintenanceLogsByTool: vi.fn(),
  completeMaintenance: vi.fn(),
  getMaintenanceStats: vi.fn(),
}))

import {
  getToolsByOwner,
  updateToolStatus,
  createMaintenanceLog,
  getMaintenanceLogsByTool,
  completeMaintenance,
  getMaintenanceStats,
} from '../api/maintenance'

describe('Maintenance Store', () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useMaintenanceStore()
    vi.clearAllMocks()
  })

  const mockTool = {
    id: 1,
    name: 'Drill',
    description: 'Power drill',
    category: 'Power Tools',
    ownerId: 10,
    status: 'AVAILABLE',
    createdAt: '2024-01-01T00:00:00',
    updatedAt: '2024-01-01T00:00:00',
  }

  const mockMaintenanceTool = {
    ...mockTool,
    id: 2,
    name: 'Saw',
    status: 'IN_MAINTENANCE',
  }

  const mockLog = {
    id: 1,
    toolId: 1,
    serviceDate: '2024-01-15',
    description: 'Blade replacement',
    cost: 25.50,
    nextServiceDue: '2024-02-15',
    createdAt: '2024-01-15T10:00:00',
    updatedAt: '2024-01-15T10:00:00',
  }

  describe('initial state', () => {
    it('has empty initial state', () => {
      expect(store.tools).toEqual([])
      expect(store.maintenanceLogs).toEqual([])
      expect(store.currentToolStats).toBeNull()
      expect(store.selectedTool).toBeNull()
      expect(store.isLoading).toBe(false)
      expect(store.error).toBeNull()
    })
  })

  describe('getters', () => {
    it('toolsInMaintenance filters correctly', () => {
      store.tools = [mockTool, mockMaintenanceTool]
      expect(store.toolsInMaintenance).toHaveLength(1)
      expect(store.toolsInMaintenance[0].id).toBe(2)
    })

    it('availableTools filters correctly', () => {
      store.tools = [mockTool, mockMaintenanceTool]
      expect(store.availableTools).toHaveLength(1)
      expect(store.availableTools[0].id).toBe(1)
    })

    it('borrowedTools filters correctly', () => {
      const borrowedTool = { ...mockTool, id: 3, status: 'BORROWED' }
      store.tools = [mockTool, mockMaintenanceTool, borrowedTool]
      expect(store.borrowedTools).toHaveLength(1)
      expect(store.borrowedTools[0].id).toBe(3)
    })
  })

  describe('actions', () => {
    describe('fetchTools', () => {
      it('loads tools for an owner', async () => {
        getToolsByOwner.mockResolvedValue([mockTool, mockMaintenanceTool])

        await store.fetchTools(10)

        expect(store.tools).toHaveLength(2)
        expect(store.isLoading).toBe(false)
        expect(store.error).toBeNull()
      })

      it('handles errors', async () => {
        getToolsByOwner.mockRejectedValue(new Error('Network error'))

        await store.fetchTools(10)

        expect(store.tools).toEqual([])
        expect(store.error).toBe('Network error')
        expect(store.isLoading).toBe(false)
      })
    })

    describe('updateToolAvailability', () => {
      it('updates tool status successfully', async () => {
        const updatedTool = { ...mockTool, status: 'IN_MAINTENANCE' }
        updateToolStatus.mockResolvedValue(updatedTool)
        store.tools = [mockTool]

        const result = await store.updateToolAvailability(1, 'IN_MAINTENANCE')

        expect(result.status).toBe('IN_MAINTENANCE')
        expect(store.tools[0].status).toBe('IN_MAINTENANCE')
      })

      it('handles errors', async () => {
        updateToolStatus.mockRejectedValue({
          response: { data: { message: 'Invalid transition' } },
        })

        await expect(store.updateToolAvailability(1, 'BORROWED')).rejects.toThrow()
        expect(store.error).toBe('Invalid transition')
      })
    })

    describe('addMaintenanceLog', () => {
      it('creates a maintenance log successfully', async () => {
        createMaintenanceLog.mockResolvedValue(mockLog)
        getMaintenanceLogsByTool.mockResolvedValue([mockLog])

        const payload = {
          toolId: 1,
          serviceDate: '2024-01-15',
          description: 'Blade replacement',
          cost: 25.50,
        }

        const result = await store.addMaintenanceLog(payload)

        expect(result).toEqual(mockLog)
        expect(store.maintenanceLogs).toHaveLength(1)
        expect(store.isLoading).toBe(false)
      })

      it('handles errors', async () => {
        createMaintenanceLog.mockRejectedValue({
          response: { data: { message: 'Tool not found' } },
        })

        await expect(store.addMaintenanceLog({
          toolId: 999,
          serviceDate: '2024-01-15',
          description: 'Test',
        })).rejects.toThrow()
        expect(store.error).toBe('Tool not found')
        expect(store.isLoading).toBe(false)
      })
    })

    describe('fetchMaintenanceLogs', () => {
      it('loads logs for a tool', async () => {
        getMaintenanceLogsByTool.mockResolvedValue([mockLog])

        await store.fetchMaintenanceLogs(1)

        expect(store.maintenanceLogs).toEqual([mockLog])
      })

      it('handles errors', async () => {
        getMaintenanceLogsByTool.mockRejectedValue(new Error('Not found'))

        await store.fetchMaintenanceLogs(1)

        expect(store.maintenanceLogs).toEqual([])
        expect(store.error).toBe('Not found')
      })
    })

    describe('completeToolMaintenance', () => {
      it('completes maintenance successfully', async () => {
        const availableTool = { ...mockMaintenanceTool, status: 'AVAILABLE' }
        completeMaintenance.mockResolvedValue(availableTool)
        store.tools = [mockMaintenanceTool]

        const result = await store.completeToolMaintenance(2)

        expect(result.status).toBe('AVAILABLE')
        expect(store.tools[0].status).toBe('AVAILABLE')
      })

      it('handles errors', async () => {
        completeMaintenance.mockRejectedValue({
          response: { data: { message: 'Tool not in maintenance' } },
        })

        await expect(store.completeToolMaintenance(2)).rejects.toThrow()
        expect(store.error).toBe('Tool not in maintenance')
      })
    })

    describe('fetchToolStats', () => {
      it('loads stats for a tool', async () => {
        const stats = { toolId: 1, logCount: 5, lastServiceDate: '2024-01-15' }
        getMaintenanceStats.mockResolvedValue(stats)

        await store.fetchToolStats(1)

        expect(store.currentToolStats).toEqual(stats)
      })

      it('handles errors gracefully', async () => {
        getMaintenanceStats.mockRejectedValue(new Error('Not found'))

        await store.fetchToolStats(1)

        expect(store.currentToolStats).toBeNull()
        expect(store.error).toBe('Not found')
      })
    })

    describe('selectTool / clearSelectedTool', () => {
      it('selects a tool and clears logs', () => {
        store.maintenanceLogs = [mockLog]
        store.currentToolStats = { logCount: 5 }

        store.selectTool(mockTool)

        expect(store.selectedTool).toEqual(mockTool)
        expect(store.maintenanceLogs).toEqual([])
        expect(store.currentToolStats).toBeNull()
      })

      it('clears selected tool', () => {
        store.selectedTool = mockTool
        store.maintenanceLogs = [mockLog]

        store.clearSelectedTool()

        expect(store.selectedTool).toBeNull()
        expect(store.maintenanceLogs).toEqual([])
        expect(store.currentToolStats).toBeNull()
      })
    })

    describe('replaceToolInList', () => {
      it('replaces tool in list', () => {
        store.tools = [mockTool, mockMaintenanceTool]
        const updatedTool = { ...mockTool, name: 'Updated Drill' }

        store.replaceToolInList(updatedTool)

        expect(store.tools[0].name).toBe('Updated Drill')
        expect(store.tools).toHaveLength(2)
      })

      it('does nothing if tool not found', () => {
        store.tools = [mockTool]

        store.replaceToolInList({ id: 999, name: 'Nonexistent' })

        expect(store.tools).toHaveLength(1)
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
