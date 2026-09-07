<script setup>
import { ref, onMounted } from 'vue'
import { getAuditLogsV1, getFilteredAuditLogsV1, exportAuditLogsCsv, exportAuditLogsPdf } from '../../api/admin'

const props = defineProps({
  embedded: { type: Boolean, default: false },
})

const loading = ref(false)
const error = ref('')
const logs = ref([])
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(20)

// Filter state
const filters = ref({
  actionType: '',
  actorEmail: '',
  actorRole: '',
  resourceType: '',
  startDate: '',
  endDate: '',
})

// Available action types
const actionTypes = [
  'LOGIN', 'LOGOUT', 'ROLE_UPDATE', 'TICKET_ASSIGN',
  'DATA_EXPORT', 'KNOWLEDGE_PUBLISH', 'STATUS_CHANGE', 'CUSTOM',
]

// Action type badge styles
const actionBadgeClass = {
  LOGIN: 'bg-emerald-100 text-emerald-700',
  LOGOUT: 'bg-slate-100 text-slate-600',
  ROLE_UPDATE: 'bg-purple-100 text-purple-700',
  TICKET_ASSIGN: 'bg-blue-100 text-blue-700',
  DATA_EXPORT: 'bg-amber-100 text-amber-700',
  KNOWLEDGE_PUBLISH: 'bg-indigo-100 text-indigo-700',
  STATUS_CHANGE: 'bg-cyan-100 text-cyan-700',
  CUSTOM: 'bg-slate-100 text-slate-600',
}

// Role badge styles
const roleBadgeClass = {
  ADMIN: 'bg-violet-100 text-violet-700',
  AGENT: 'bg-sky-100 text-sky-700',
  CUSTOMER: 'bg-slate-100 text-slate-600',
  SYSTEM: 'bg-orange-100 text-orange-700',
}

function getActionBadge(type) {
  return actionBadgeClass[type] || 'bg-slate-100 text-slate-600'
}

function getRoleBadge(role) {
  return roleBadgeClass[role] || 'bg-slate-100 text-slate-600'
}

function formatTime(timestamp) {
  if (!timestamp) return '—'
  const date = new Date(timestamp)
  return date.toLocaleString([], {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

// ── Data loading ──────────────────────────────────────────────────
async function loadLogs() {
  loading.value = true
  error.value = ''
  try {
    const hasFilters = filters.value.actionType || filters.value.actorEmail ||
      filters.value.actorRole || filters.value.resourceType ||
      filters.value.startDate || filters.value.endDate

    let result
    if (hasFilters) {
      result = await getFilteredAuditLogsV1({
        ...filters.value,
        page: currentPage.value,
        size: pageSize.value,
      })
    } else {
      result = await getAuditLogsV1(currentPage.value, pageSize.value)
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

function applyFilters() {
  currentPage.value = 0
  loadLogs()
}

function clearFilters() {
  filters.value = { actionType: '', actorEmail: '', actorRole: '', resourceType: '', startDate: '', endDate: '' }
  currentPage.value = 0
  loadLogs()
}

function goToPage(page) {
  if (page >= 0 && page < totalPages.value) {
    currentPage.value = page
    loadLogs()
  }
}

// ── Export handlers ────────────────────────────────────────────────
function handleExportCsv() {
  exportAuditLogsCsv(filters.value).catch(() => { error.value = 'Failed to export CSV' })
}
function handleExportPdf() {
  exportAuditLogsPdf(filters.value).catch(() => { error.value = 'Failed to export PDF' })
}

onMounted(loadLogs)
</script>

<template>
  <div class="audit-log-viewer">
    <!-- Header -->
    <div class="mb-6 flex flex-wrap items-center justify-between gap-4">
      <div>
        <h2 class="text-xl font-bold text-slate-800">Audit Logs</h2>
        <p class="text-sm text-slate-500">System activity and security events</p>
      </div>
      <div class="flex items-center gap-3">
        <button @click="handleExportCsv"
          class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-emerald-700">
          📊 Export CSV
        </button>
        <button @click="handleExportPdf"
          class="inline-flex items-center gap-1.5 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700">
          📄 Export PDF
        </button>
      </div>
    </div>

    <!-- Filters -->
    <div class="mb-6 rounded-xl border border-slate-200 bg-white p-4">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-6">
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">Action Type</label>
          <select v-model="filters.actionType"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            @change="applyFilters">
            <option value="">All Actions</option>
            <option v-for="action in actionTypes" :key="action" :value="action">
              {{ action.replace('_', ' ') }}
            </option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">Actor Email</label>
          <input v-model="filters.actorEmail" type="text" placeholder="Filter by actor…"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            @keyup.enter="applyFilters" />
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">Actor Role</label>
          <select v-model="filters.actorRole"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            @change="applyFilters">
            <option value="">All Roles</option>
            <option value="ADMIN">Admin</option>
            <option value="AGENT">Agent</option>
            <option value="CUSTOMER">Customer</option>
            <option value="SYSTEM">System</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">Resource Type</label>
          <select v-model="filters.resourceType"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            @change="applyFilters">
            <option value="">All Resources</option>
            <option value="TICKET">Ticket</option>
            <option value="USER">User</option>
            <option value="DOCUMENT">Document</option>
            <option value="EXPORT">Export</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">Start Date</label>
          <input v-model="filters.startDate" type="datetime-local"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            @change="applyFilters" />
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-600">End Date</label>
          <input v-model="filters.endDate" type="datetime-local"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            @change="applyFilters" />
        </div>
      </div>
      <div class="mt-4 flex items-center gap-2">
        <button @click="applyFilters"
          class="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-700">
          Apply Filters
        </button>
        <button @click="clearFilters"
          class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-50">
          Clear
        </button>
        <span class="ml-auto text-sm text-slate-400">
          {{ totalElements }} total entries
        </span>
      </div>
    </div>

    <!-- Error -->
    <div v-if="error" class="mb-6 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
      {{ error }}
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-16">
      <svg class="size-6 animate-spin text-indigo-500" viewBox="0 0 24 24" fill="none">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      <span class="ml-3 text-sm text-slate-500">Loading audit logs…</span>
    </div>

    <!-- Table -->
    <div v-else class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead class="border-b border-slate-200 bg-slate-50">
            <tr>
              <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Timestamp</th>
              <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Actor</th>
              <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Role</th>
              <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Action</th>
              <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Description</th>
              <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Resource</th>
              <th class="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Status</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="log in logs" :key="log.id" class="transition hover:bg-slate-50">
              <td class="whitespace-nowrap px-4 py-3 text-slate-500 tabular-nums">
                {{ formatTime(log.timestamp) }}
              </td>
              <td class="px-4 py-3">
                <p class="font-medium text-slate-800">{{ log.actorEmail || '—' }}</p>
                <p v-if="log.ipAddress" class="text-xs text-slate-400">{{ log.ipAddress }}</p>
              </td>
              <td class="px-4 py-3">
                <span class="inline-flex rounded-full px-2 py-0.5 text-[11px] font-semibold"
                  :class="getRoleBadge(log.actorRole)">
                  {{ log.actorRole || '—' }}
                </span>
              </td>
              <td class="px-4 py-3">
                <span class="inline-flex rounded-full px-2.5 py-0.5 text-[11px] font-semibold"
                  :class="getActionBadge(log.actionType)">
                  {{ (log.actionType || '').replace('_', ' ') }}
                </span>
              </td>
              <td class="max-w-xs truncate px-4 py-3 text-slate-600">
                {{ log.description || '—' }}
              </td>
              <td class="px-4 py-3">
                <span v-if="log.resourceType" class="text-slate-600">
                  {{ log.resourceType }}
                  <span v-if="log.resourceId" class="text-slate-400">#{{ log.resourceId }}</span>
                </span>
                <span v-else class="text-slate-300">—</span>
              </td>
              <td class="px-4 py-3">
                <span v-if="log.success" class="inline-flex items-center gap-1 text-emerald-600">
                  <svg class="size-4" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                  </svg>
                  OK
                </span>
                <span v-else class="inline-flex items-center gap-1 text-red-600">
                  <svg class="size-4" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
                  </svg>
                  FAIL
                </span>
              </td>
            </tr>
            <tr v-if="logs.length === 0">
              <td colspan="7" class="px-4 py-12 text-center text-sm text-slate-400">
                No audit logs found matching the current filters.
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
          <button @click="goToPage(0)" :disabled="currentPage === 0"
            class="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">
            ««
          </button>
          <button @click="goToPage(currentPage - 1)" :disabled="currentPage === 0"
            class="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">
            ← Prev
          </button>
          <button @click="goToPage(currentPage + 1)" :disabled="currentPage >= totalPages - 1"
            class="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">
            Next →
          </button>
          <button @click="goToPage(totalPages - 1)" :disabled="currentPage >= totalPages - 1"
            class="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">
            »»
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
