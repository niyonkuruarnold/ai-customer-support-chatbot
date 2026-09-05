<script setup>
import { ref, onMounted, computed } from 'vue'
import { 
  getAuditLogs, 
  getFilteredAuditLogs, 
  getAuditLogStats,
  exportAuditLogsCsv,
  exportAuditLogsPdf 
} from '../../api/analytics'

const props = defineProps({
  embedded: { type: Boolean, default: false },
})

const loading = ref(false)
const error = ref('')
const logs = ref([])
const stats = ref(null)
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(20)

// Filter state
const filters = ref({
  actionType: '',
  actorEmail: '',
  resourceType: '',
  startDate: '',
  endDate: '',
})

// Available action types
const actionTypes = ref([
  'LOGIN', 'LOGOUT', 'ROLE_UPDATE', 'TICKET_ASSIGN', 
  'DATA_EXPORT', 'KNOWLEDGE_PUBLISH', 'CUSTOM'
])

// Action type colors
const actionColors = {
  LOGIN: 'bg-emerald-100 text-emerald-700',
  LOGOUT: 'bg-slate-100 text-slate-700',
  ROLE_UPDATE: 'bg-purple-100 text-purple-700',
  TICKET_ASSIGN: 'bg-blue-100 text-blue-700',
  DATA_EXPORT: 'bg-amber-100 text-amber-700',
  KNOWLEDGE_PUBLISH: 'bg-indigo-100 text-indigo-700',
  CUSTOM: 'bg-slate-100 text-slate-700',
}

function getActionColor(actionType) {
  return actionColors[actionType] || 'bg-slate-100 text-slate-700'
}

// Format timestamp
function formatTime(timestamp) {
  if (!timestamp) return '—'
  const date = new Date(timestamp)
  return date.toLocaleString([], {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// Load logs
async function loadLogs() {
  loading.value = true
  error.value = ''
  try {
    const hasFilters = filters.value.actionType || filters.value.actorEmail || 
                       filters.value.resourceType || filters.value.startDate || filters.value.endDate
    
    let result
    if (hasFilters) {
      result = await getFilteredAuditLogs({
        ...filters.value,
        page: currentPage.value,
        size: pageSize.value,
      })
    } else {
      result = await getAuditLogs(currentPage.value, pageSize.value)
    }
    
    logs.value = result.content || []
    totalPages.value = result.totalPages || 0
    totalElements.value = result.totalElements || 0
  } catch (err) {
    error.value = 'Failed to load audit logs'
    console.error(err)
  } finally {
    loading.value = false
  }
}

// Load stats
async function loadStats() {
  try {
    stats.value = await getAuditLogStats()
  } catch (err) {
    console.error('Failed to load audit stats:', err)
  }
}

// Apply filters
function applyFilters() {
  currentPage.value = 0
  loadLogs()
}

// Clear filters
function clearFilters() {
  filters.value = {
    actionType: '',
    actorEmail: '',
    resourceType: '',
    startDate: '',
    endDate: '',
  }
  currentPage.value = 0
  loadLogs()
}

// Pagination
function goToPage(page) {
  if (page >= 0 && page < totalPages.value) {
    currentPage.value = page
    loadLogs()
  }
}

// Export functions
async function handleExportCsv() {
  try {
    await exportAuditLogsCsv(filters.value)
  } catch (err) {
    error.value = 'Failed to export CSV'
  }
}

async function handleExportPdf() {
  try {
    await exportAuditLogsPdf(filters.value)
  } catch (err) {
    error.value = 'Failed to export PDF'
  }
}

onMounted(() => {
  loadLogs()
  loadStats()
})
</script>

<template>
  <div class="audit-log-viewer">
    <!-- Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h2 class="text-xl font-bold text-slate-800">Audit Logs</h2>
        <p class="text-sm text-slate-500">System activity and security events</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="handleExportCsv"
          class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700"
        >
          📊 Export CSV
        </button>
        <button
          @click="handleExportPdf"
          class="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700"
        >
          📄 Export PDF
        </button>
      </div>
    </div>

    <!-- Stats Cards -->
    <div v-if="stats" class="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
      <div class="rounded-lg border border-slate-200 bg-white p-4">
        <p class="text-sm text-slate-500">Last 24 Hours</p>
        <p class="mt-1 text-2xl font-bold text-slate-800">{{ stats.last24Hours }}</p>
      </div>
      <div class="rounded-lg border border-slate-200 bg-white p-4">
        <p class="text-sm text-slate-500">Last 7 Days</p>
        <p class="mt-1 text-2xl font-bold text-slate-800">{{ stats.last7Days }}</p>
      </div>
      <div class="rounded-lg border border-slate-200 bg-white p-4">
        <p class="text-sm text-slate-500">Login Events</p>
        <p class="mt-1 text-2xl font-bold text-emerald-600">{{ stats.loginEvents }}</p>
      </div>
      <div class="rounded-lg border border-slate-200 bg-white p-4">
        <p class="text-sm text-slate-500">Data Exports</p>
        <p class="mt-1 text-2xl font-bold text-amber-600">{{ stats.exportEvents }}</p>
      </div>
    </div>

    <!-- Filters -->
    <div class="mb-6 rounded-xl border border-slate-200 bg-white p-4">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">Action Type</label>
          <select
            v-model="filters.actionType"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            @change="applyFilters"
          >
            <option value="">All Actions</option>
            <option v-for="action in actionTypes" :key="action" :value="action">
              {{ action.replace('_', ' ') }}
            </option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">Actor Email</label>
          <input
            v-model="filters.actorEmail"
            type="text"
            placeholder="Filter by actor..."
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            @keyup.enter="applyFilters"
          />
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">Resource Type</label>
          <select
            v-model="filters.resourceType"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            @change="applyFilters"
          >
            <option value="">All Resources</option>
            <option value="TICKET">Ticket</option>
            <option value="USER">User</option>
            <option value="DOCUMENT">Document</option>
            <option value="EXPORT">Export</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">Start Date</label>
          <input
            v-model="filters.startDate"
            type="datetime-local"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            @change="applyFilters"
          />
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">End Date</label>
          <input
            v-model="filters.endDate"
            type="datetime-local"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            @change="applyFilters"
          />
        </div>
      </div>
      <div class="mt-4 flex items-center gap-2">
        <button
          @click="applyFilters"
          class="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
        >
          Apply Filters
        </button>
        <button
          @click="clearFilters"
          class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50"
        >
          Clear
        </button>
        <span class="ml-auto text-sm text-slate-500">
          {{ totalElements }} total entries
        </span>
      </div>
    </div>

    <!-- Error State -->
    <div v-if="error" class="mb-6 rounded-lg bg-red-50 p-4 text-red-700">
      {{ error }}
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="text-slate-500">Loading audit logs...</div>
    </div>

    <!-- Logs Table -->
    <div v-else class="rounded-xl border border-slate-200 bg-white shadow-sm">
      <div class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead class="border-b border-slate-200 bg-slate-50">
            <tr>
              <th class="px-4 py-3 font-semibold text-slate-600">Timestamp</th>
              <th class="px-4 py-3 font-semibold text-slate-600">Actor</th>
              <th class="px-4 py-3 font-semibold text-slate-600">Action</th>
              <th class="px-4 py-3 font-semibold text-slate-600">Description</th>
              <th class="px-4 py-3 font-semibold text-slate-600">Resource</th>
              <th class="px-4 py-3 font-semibold text-slate-600">Status</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="log in logs" :key="log.id" class="hover:bg-slate-50">
              <td class="whitespace-nowrap px-4 py-3 text-slate-600">
                {{ formatTime(log.timestamp) }}
              </td>
              <td class="px-4 py-3">
                <div>
                  <p class="font-medium text-slate-800">{{ log.actorEmail }}</p>
                  <p v-if="log.ipAddress" class="text-xs text-slate-400">{{ log.ipAddress }}</p>
                </div>
              </td>
              <td class="px-4 py-3">
                <span
                  class="inline-flex rounded-full px-2 py-1 text-xs font-medium"
                  :class="getActionColor(log.actionType)"
                >
                  {{ log.actionType.replace('_', ' ') }}
                </span>
              </td>
              <td class="max-w-xs truncate px-4 py-3 text-slate-600">
                {{ log.description }}
              </td>
              <td class="px-4 py-3">
                <span v-if="log.resourceType" class="text-slate-600">
                  {{ log.resourceType }}
                  <span v-if="log.resourceId" class="text-slate-400">#{{ log.resourceId }}</span>
                </span>
                <span v-else class="text-slate-400">—</span>
              </td>
              <td class="px-4 py-3">
                <span v-if="log.success" class="text-emerald-600">✓</span>
                <span v-else class="text-red-600">✗</span>
              </td>
            </tr>
            <tr v-if="logs.length === 0">
              <td colspan="6" class="px-4 py-8 text-center text-slate-400">
                No audit logs found
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="flex items-center justify-between border-t border-slate-200 px-4 py-3">
        <p class="text-sm text-slate-500">
          Page {{ currentPage + 1 }} of {{ totalPages }}
        </p>
        <div class="flex items-center gap-2">
          <button
            @click="goToPage(currentPage - 1)"
            :disabled="currentPage === 0"
            class="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
          >
            ← Previous
          </button>
          <button
            @click="goToPage(currentPage + 1)"
            :disabled="currentPage >= totalPages - 1"
            class="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
          >
            Next →
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@reference "../../style.css";
.audit-log-viewer {
  @apply p-6;
}
</style>
