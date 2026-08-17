<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { closeTicket, fetchTickets } from '../../api/admin'
import { useAgentStore } from '../../stores/agent'
import { useToasts } from '../../composables/useToasts'

defineEmits(['switch-to-chat'])

const agentStore = useAgentStore()
const { toasts, push, remove } = useToasts()

const username = ref('')
const password = ref('')
const loginLoading = ref(false)
const loginError = ref('')

// Filters + pagination state
const filters = reactive({ status: '', priority: '', assignedAgentId: '' })
const page = ref(0)
const size = 10

const tickets = ref([])
const loading = ref(false)
const totalElements = ref(0)
const totalPages = ref(0)
const last = ref(true)
const loadError = ref('')
const closingId = ref(null)

const isAuthenticated = computed(() => agentStore.authenticated)

// Pageable support: first page is 0, so the UI labels are 1-based
const currentPageLabel = computed(() => (totalPages === 0 ? 0 : page.value + 1))

const STATUS_OPTIONS = ['OPEN', 'ESCALATED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
const PRIORITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']

const statusLabel = {
  OPEN: 'Open',
  ESCALATED: 'Escalated',
  IN_PROGRESS: 'In progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
}
const statusClass = {
  OPEN: 'bg-sky-100 text-sky-700',
  ESCALATED: 'bg-amber-100 text-amber-700',
  IN_PROGRESS: 'bg-indigo-100 text-indigo-700',
  RESOLVED: 'bg-emerald-100 text-emerald-700',
  CLOSED: 'bg-slate-200 text-slate-600',
}
const priorityClass = {
  LOW: 'bg-slate-100 text-slate-600',
  MEDIUM: 'bg-sky-100 text-sky-700',
  HIGH: 'bg-orange-100 text-orange-700',
  URGENT: 'bg-rose-100 text-rose-700',
}

onMounted(() => {
  if (agentStore.authenticated) load()
})

// A 401 mid-session sends the user back to the sign-in gate with a toast
watch(
  () => agentStore.authenticated,
  (authed) => {
    if (!authed && loadError.value) {
      push('error', 'Session expired — please sign in again.')
    }
  },
)

onBeforeUnmount(() => {
  agentStore.stopPolling()
})

async function handleLogin() {
  loginLoading.value = true
  loginError.value = ''
  try {
    await agentStore.login(username.value, password.value)
    agentStore.stopPolling() // dashboard has no live polling need
    username.value = ''
    password.value = ''
    await load()
  } catch (err) {
    loginError.value =
      err?.status === 401
        ? 'Invalid admin credentials. Use the Spring Security user (default admin / admin123).'
        : 'Could not reach the backend. Is it running on port 8080?'
  } finally {
    loginLoading.value = false
  }
}

function handleLogout() {
  agentStore.logout()
  tickets.value = []
  totalElements.value = 0
  totalPages.value = 0
  loadError.value = ''
}

function applyFilters() {
  page.value = 0 // any filter change restarts at the first page
  load()
}

function clearFilters() {
  filters.status = ''
  filters.priority = ''
  filters.assignedAgentId = ''
  applyFilters()
}

function goToPage(target) {
  if (target < 0 || target >= totalPages.value) return
  page.value = target
  load()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await fetchTickets({
      status: filters.status || undefined,
      priority: filters.priority || undefined,
      assignedAgentId: filters.assignedAgentId || undefined,
      page: page.value,
      size,
    })
    tickets.value = data.content ?? []
    totalElements.value = data.totalElements ?? 0
    totalPages.value = data.totalPages ?? 0
    last.value = data.last ?? true
  } catch (err) {
    if (err?.status === 401) {
      agentStore.handleAuthFailure(err)
      push('error', 'Session expired — please sign in again.')
    } else {
      loadError.value = 'Could not load tickets. Is the backend running?'
    }
  } finally {
    loading.value = false
  }
}

async function handleClose(ticket) {
  closingId.value = ticket.id
  try {
    await closeTicket(ticket.id)
    push('success', `Ticket #${ticket.id} marked as closed.`)
    await load()
  } catch (err) {
    if (err?.status === 401) {
      agentStore.handleAuthFailure(err)
      push('error', 'Session expired — please sign in again.')
    } else {
      push('error', err?.response?.data?.message || `Could not close ticket #${ticket.id}.`)
    }
  } finally {
    closingId.value = null
  }
}

function formatDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleString([], {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
}
</script>

<template>
  <div class="flex h-dvh flex-col bg-slate-100 font-sans text-slate-900">
    <!-- Toasts -->
    <div
      class="pointer-events-none fixed top-4 right-4 z-50 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2"
      aria-live="polite"
    >
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="pointer-events-auto flex items-start gap-2.5 rounded-xl border px-4 py-3 text-sm shadow-lg"
        :class="
          toast.type === 'success'
            ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
            : 'border-red-200 bg-red-50 text-red-800'
        "
        role="status"
      >
        <span class="shrink-0" aria-hidden="true">
          {{ toast.type === 'success' ? '✅' : '⚠️' }}
        </span>
        <p class="min-w-0 flex-1 leading-snug">{{ toast.message }}</p>
        <button
          type="button"
          class="shrink-0 text-xs font-semibold opacity-50 transition hover:opacity-100"
          :aria-label="'Dismiss notification'"
          @click="remove(toast.id)"
        >
          ✕
        </button>
      </div>
    </div>

    <!-- Header -->
    <header class="z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div class="mx-auto flex max-w-5xl items-center gap-3 px-4 py-3">
        <div
          class="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-emerald-500 to-teal-700 text-lg text-white shadow-md"
          aria-hidden="true"
        >
          🎫
        </div>
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-base font-semibold">Ticket Dashboard</h1>
          <p class="truncate text-xs text-slate-500">
            <template v-if="isAuthenticated">
              Signed in as
              <span class="font-medium text-slate-700">{{ agentStore.agentName }}</span>
              · {{ totalElements }} ticket{{ totalElements === 1 ? '' : 's' }}
            </template>
            <template v-else>Ticket lifecycle management</template>
          </p>
        </div>
        <button
          v-if="isAuthenticated"
          type="button"
          @click="handleLogout"
          class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 hover:text-red-600"
        >
          Log out
        </button>
        <button
          type="button"
          @click="$emit('switch-to-chat')"
          class="flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 hover:text-indigo-600"
        >
          ← Customer chat
        </button>
      </div>
    </header>

    <!-- Sign-in gate -->
    <div v-if="!isAuthenticated" class="flex flex-1 items-center justify-center p-4">
      <form
        class="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
        @submit.prevent="handleLogin"
      >
        <h2 class="text-lg font-semibold text-slate-800">Admin sign in</h2>
        <p class="mt-1 text-sm leading-relaxed text-slate-500">
          Sign in to view, filter, and manage support tickets. Uses the Spring
          Security HTTP Basic credentials.
        </p>
        <label class="mt-4 block text-sm font-medium text-slate-700">
          Username
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            required
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100"
          />
        </label>
        <label class="mt-3 block text-sm font-medium text-slate-700">
          Password
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100"
          />
        </label>
        <p v-if="loginError" class="mt-3 text-sm text-red-600" role="alert">
          {{ loginError }}
        </p>
        <button
          type="submit"
          :disabled="loginLoading"
          class="mt-5 w-full rounded-lg bg-emerald-600 py-2.5 text-sm font-semibold text-white transition enabled:hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {{ loginLoading ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
    </div>

    <!-- Ticket management -->
    <main v-else class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto max-w-5xl space-y-6 p-6">
        <div>
          <h2 class="text-lg font-semibold text-slate-800">Ticket lifecycle</h2>
          <p class="mt-1 text-sm leading-relaxed text-slate-500">
            Tickets progress through OPEN → IN_PROGRESS → RESOLVED → CLOSED
            (with ESCALATED as the human-handoff state). Automated email
            notifications are sent to the customer on opened, updated, and
            resolved events.
          </p>
        </div>

        <!-- Filters -->
        <section
          class="flex flex-wrap items-end gap-3 rounded-2xl border border-slate-200 bg-white p-4"
        >
          <label class="block text-xs font-medium text-slate-600">
            Status
            <select
              v-model="filters.status"
              data-test="filter-status"
              class="mt-1 block rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100"
              @change="applyFilters"
            >
              <option value="">All statuses</option>
              <option v-for="s in STATUS_OPTIONS" :key="s" :value="s">
                {{ statusLabel[s] }}
              </option>
            </select>
          </label>
          <label class="block text-xs font-medium text-slate-600">
            Priority
            <select
              v-model="filters.priority"
              data-test="filter-priority"
              class="mt-1 block rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100"
              @change="applyFilters"
            >
              <option value="">All priorities</option>
              <option v-for="p in PRIORITY_OPTIONS" :key="p" :value="p">
                {{ p.charAt(0) + p.slice(1).toLowerCase() }}
              </option>
            </select>
          </label>
          <label class="block text-xs font-medium text-slate-600">
            Assigned agent ID
            <input
              v-model="filters.assignedAgentId"
              data-test="filter-agent"
              type="number"
              min="1"
              placeholder="e.g. 2"
              class="mt-1 w-32 rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100"
              @change="applyFilters"
            />
          </label>
          <button
            v-if="filters.status || filters.priority || filters.assignedAgentId"
            type="button"
            @click="clearFilters"
            class="rounded-full border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 hover:text-red-600"
          >
            Clear filters
          </button>
        </section>

        <!-- Ticket table -->
        <section>
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold text-slate-800">Tickets</h3>
            <p class="text-xs text-slate-400">
              Page {{ currentPageLabel }} of {{ totalPages === 0 ? 0 : totalPages }}
            </p>
          </div>

          <div
            v-if="loading"
            class="mt-3 flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white p-8 text-sm text-slate-400"
          >
            <svg class="size-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <circle
                class="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                stroke-width="4"
              />
              <path
                class="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z"
              />
            </svg>
            Loading tickets…
          </div>

          <div
            v-else-if="loadError"
            class="mt-3 rounded-xl border border-red-200 bg-red-50 p-8 text-center text-sm text-red-700"
          >
            {{ loadError }}
          </div>

          <div
            v-else-if="tickets.length === 0"
            class="mt-3 rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-400"
          >
            No tickets match the current filters.
          </div>

          <div
            v-else
            class="mt-3 overflow-hidden rounded-xl border border-slate-200 bg-white"
          >
            <table class="w-full text-left text-sm">
              <thead class="border-b border-slate-200 bg-slate-50 text-xs text-slate-500">
                <tr>
                  <th class="px-4 py-3 font-semibold">#</th>
                  <th class="px-4 py-3 font-semibold">Subject</th>
                  <th class="px-4 py-3 font-semibold">Customer</th>
                  <th class="px-4 py-3 font-semibold">Status</th>
                  <th class="px-4 py-3 font-semibold">Priority</th>
                  <th class="px-4 py-3 font-semibold">Agent</th>
                  <th class="px-4 py-3 font-semibold">Updated</th>
                  <th class="px-4 py-3 text-right font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="ticket in tickets" :key="ticket.id" class="hover:bg-slate-50">
                  <td class="px-4 py-3 font-medium text-slate-500">#{{ ticket.id }}</td>
                  <td class="max-w-[16rem] px-4 py-3">
                    <p class="truncate font-medium text-slate-800">
                      {{ ticket.subject || 'Support request' }}
                    </p>
                    <p v-if="ticket.description" class="truncate text-xs text-slate-400">
                      {{ ticket.description }}
                    </p>
                  </td>
                  <td class="max-w-[12rem] px-4 py-3">
                    <p class="truncate text-slate-700">{{ ticket.userEmail || '—' }}</p>
                    <p class="text-xs text-slate-400">user #{{ ticket.userId ?? '—' }}</p>
                  </td>
                  <td class="px-4 py-3">
                    <span
                      class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                      :class="statusClass[ticket.status] ?? 'bg-slate-100 text-slate-600'"
                    >
                      {{ statusLabel[ticket.status] ?? ticket.status }}
                    </span>
                  </td>
                  <td class="px-4 py-3">
                    <span
                      class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                      :class="priorityClass[ticket.priority] ?? 'bg-slate-100 text-slate-600'"
                    >
                      {{ ticket.priority ?? '—' }}
                    </span>
                  </td>
                  <td class="px-4 py-3 text-xs text-slate-600">
                    {{ ticket.assignedAgent || '—' }}
                  </td>
                  <td class="px-4 py-3 text-xs whitespace-nowrap text-slate-500">
                    {{ formatDate(ticket.updatedAt) }}
                  </td>
                  <td class="px-4 py-3 text-right">
                    <button
                      v-if="ticket.status === 'RESOLVED'"
                      type="button"
                      :disabled="closingId === ticket.id"
                      data-test="close-ticket"
                      @click="handleClose(ticket)"
                      class="rounded-full border border-slate-200 px-3 py-1 text-xs font-medium text-slate-500 transition hover:border-emerald-300 hover:bg-emerald-50 hover:text-emerald-700 disabled:cursor-not-allowed disabled:opacity-40"
                      :aria-label="`Close ticket ${ticket.id}`"
                    >
                      {{ closingId === ticket.id ? 'Closing…' : 'Close' }}
                    </button>
                    <span v-else class="text-xs text-slate-300">—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Pagination -->
          <nav
            v-if="totalPages > 1"
            class="mt-4 flex items-center justify-center gap-2"
            aria-label="Ticket pages"
          >
            <button
              type="button"
              :disabled="page === 0 || loading"
              @click="goToPage(page - 1)"
              class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 transition enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              ← Prev
            </button>
            <span class="px-2 text-xs text-slate-500">
              Page {{ currentPageLabel }} of {{ totalPages }}
            </span>
            <button
              type="button"
              :disabled="last || loading"
              @click="goToPage(page + 1)"
              class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 transition enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Next →
            </button>
          </nav>
        </section>
      </div>
    </main>
  </div>
</template>
