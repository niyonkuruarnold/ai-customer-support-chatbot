<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAgentStore } from '../../stores/agent'
import { useMaintenanceStore } from '../../stores/maintenance'
import MaintenanceHistory from './MaintenanceHistory.vue'

const emit = defineEmits(['switch-to-chat'])

const agentStore = useAgentStore()
const maintenanceStore = useMaintenanceStore()

// Auth state
const username = ref('')
const password = ref('')
const loginLoading = ref(false)
const loginError = ref('')

// Modal state
const showMaintenanceModal = ref(false)
const selectedToolForMaintenance = ref(null)

// Tab state
const activeTab = ref('all')

// Filter state
const filterStatus = ref('')

const isAuthenticated = computed(() => agentStore.authenticated)

const filteredTools = computed(() => {
  let tools = maintenanceStore.tools
  if (filterStatus.value) {
    tools = tools.filter((t) => t.status === filterStatus.value)
  }
  return tools
})

const toolCountByStatus = computed(() => ({
  all: maintenanceStore.tools.length,
  AVAILABLE: maintenanceStore.availableTools.length,
  BORROWED: maintenanceStore.borrowedTools.length,
  IN_MAINTENANCE: maintenanceStore.toolsInMaintenance.length,
}))

onMounted(() => {
  if (isAuthenticated.value && agentStore.userId) {
    maintenanceStore.fetchTools(agentStore.userId)
  }
})

async function handleLogin() {
  loginLoading.value = true
  loginError.value = ''
  try {
    await agentStore.login(username.value, password.value)
    agentStore.stopPolling()
    username.value = ''
    password.value = ''
    if (agentStore.userId) {
      await maintenanceStore.fetchTools(agentStore.userId)
    }
  } catch (err) {
    loginError.value =
      err?.status === 401
        ? 'Invalid credentials. Use the Spring Security user (default admin / admin123).'
        : 'Could not reach the backend. Is it running on port 8080?'
  } finally {
    loginLoading.value = false
  }
}

function handleLogout() {
  agentStore.logout()
  maintenanceStore.clearSelectedTool()
}

function openMaintenanceModal(tool) {
  maintenanceStore.selectTool(tool)
  selectedToolForMaintenance.value = tool
  showMaintenanceModal.value = true
}

function closeMaintenanceModal() {
  showMaintenanceModal.value = false
  selectedToolForMaintenance.value = null
  maintenanceStore.clearSelectedTool()
}

async function handleStatusChanged() {
  // Refresh tools list
  if (agentStore.userId) {
    await maintenanceStore.fetchTools(agentStore.userId)
  }
}

async function handleToggleMaintenance(tool) {
  try {
    if (tool.status === 'IN_MAINTENANCE') {
      await maintenanceStore.completeToolMaintenance(tool.id)
    } else if (tool.status === 'AVAILABLE') {
      await maintenanceStore.updateToolAvailability(tool.id, 'IN_MAINTENANCE')
    }
    if (agentStore.userId) {
      await maintenanceStore.fetchTools(agentStore.userId)
    }
  } catch (err) {
    console.error('Failed to toggle maintenance:', err)
  }
}

const statusLabel = {
  AVAILABLE: 'Available',
  BORROWED: 'Borrowed',
  IN_MAINTENANCE: 'In Maintenance',
}

const statusClass = {
  AVAILABLE: 'bg-emerald-100 text-emerald-700',
  BORROWED: 'bg-blue-100 text-blue-700',
  IN_MAINTENANCE: 'bg-amber-100 text-amber-700',
}

function formatDate(d) {
  if (!d) return '—'
  const date = new Date(d)
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleDateString([], { year: 'numeric', month: 'short', day: 'numeric' })
}
</script>

<template>
  <div class="flex h-dvh flex-col bg-slate-100 font-sans text-slate-900">
    <!-- Header -->
    <header class="z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div class="mx-auto flex max-w-5xl items-center gap-3 px-4 py-3">
        <div
          class="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-orange-500 to-amber-600 text-white shadow-md"
          aria-hidden="true"
        >
          🔧
        </div>
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-base font-semibold">Owner Dashboard</h1>
          <p class="truncate text-xs text-slate-500">
            <template v-if="isAuthenticated">
              Tool management &amp; maintenance tracking · {{ toolCountByStatus.all }} tool{{ toolCountByStatus.all === 1 ? '' : 's' }}
            </template>
            <template v-else>Tool management &amp; maintenance tracking</template>
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
        <h2 class="text-lg font-semibold text-slate-800">Sign in</h2>
        <p class="mt-1 text-sm leading-relaxed text-slate-500">
          Sign in to manage your tools and track maintenance.
        </p>
        <label class="mt-4 block text-sm font-medium text-slate-700">
          Username
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            required
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-orange-400 focus:ring-2 focus:ring-orange-100"
          />
        </label>
        <label class="mt-3 block text-sm font-medium text-slate-700">
          Password
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-orange-400 focus:ring-2 focus:ring-orange-100"
          />
        </label>
        <p v-if="loginError" class="mt-3 text-sm text-red-600" role="alert">
          {{ loginError }}
        </p>
        <button
          type="submit"
          :disabled="loginLoading"
          class="mt-5 w-full rounded-lg bg-orange-600 py-2.5 text-sm font-semibold text-white transition enabled:hover:bg-orange-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {{ loginLoading ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
    </div>

    <!-- Main content -->
    <main v-else class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto max-w-5xl space-y-6 p-6">

        <!-- Status summary cards -->
        <div class="grid grid-cols-4 gap-3">
          <button
            type="button"
            @click="filterStatus = ''"
            class="rounded-xl border p-4 text-left transition"
            :class="filterStatus === '' ? 'border-orange-300 bg-orange-50' : 'border-slate-200 bg-white hover:bg-slate-50'"
          >
            <p class="text-2xl font-bold text-slate-800">{{ toolCountByStatus.all }}</p>
            <p class="text-xs text-slate-500">All Tools</p>
          </button>
          <button
            type="button"
            @click="filterStatus = 'AVAILABLE'"
            class="rounded-xl border p-4 text-left transition"
            :class="filterStatus === 'AVAILABLE' ? 'border-emerald-300 bg-emerald-50' : 'border-slate-200 bg-white hover:bg-slate-50'"
          >
            <p class="text-2xl font-bold text-emerald-600">{{ toolCountByStatus.AVAILABLE }}</p>
            <p class="text-xs text-slate-500">Available</p>
          </button>
          <button
            type="button"
            @click="filterStatus = 'BORROWED'"
            class="rounded-xl border p-4 text-left transition"
            :class="filterStatus === 'BORROWED' ? 'border-blue-300 bg-blue-50' : 'border-slate-200 bg-white hover:bg-slate-50'"
          >
            <p class="text-2xl font-bold text-blue-600">{{ toolCountByStatus.BORROWED }}</p>
            <p class="text-xs text-slate-500">Borrowed</p>
          </button>
          <button
            type="button"
            @click="filterStatus = 'IN_MAINTENANCE'"
            class="rounded-xl border p-4 text-left transition"
            :class="filterStatus === 'IN_MAINTENANCE' ? 'border-amber-300 bg-amber-50' : 'border-slate-200 bg-white hover:bg-slate-50'"
          >
            <p class="text-2xl font-bold text-amber-600">{{ toolCountByStatus.IN_MAINTENANCE }}</p>
            <p class="text-xs text-slate-500">In Maintenance</p>
          </button>
        </div>

        <!-- Loading state -->
        <div
          v-if="maintenanceStore.isLoading && !maintenanceStore.tools.length"
          class="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white p-8 text-sm text-slate-400"
        >
          <svg class="size-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          Loading tools…
        </div>

        <!-- Empty state -->
        <div
          v-else-if="filteredTools.length === 0"
          class="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-400"
        >
          {{ filterStatus ? 'No tools with this status.' : 'No tools registered yet.' }}
        </div>

        <!-- Tools grid -->
        <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div
            v-for="tool in filteredTools"
            :key="tool.id"
            class="rounded-xl border border-slate-200 bg-white p-4 transition hover:shadow-sm"
          >
            <div class="flex items-start justify-between">
              <div class="min-w-0 flex-1">
                <h3 class="truncate font-medium text-slate-800">{{ tool.name }}</h3>
                <p class="mt-0.5 text-xs text-slate-400">Tool #{{ tool.id }} · {{ tool.category }}</p>
              </div>
              <span
                class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium"
                :class="statusClass[tool.status]"
              >
                {{ statusLabel[tool.status] }}
              </span>
            </div>

            <p v-if="tool.description" class="mt-2 line-clamp-2 text-xs text-slate-500">
              {{ tool.description }}
            </p>

            <p class="mt-2 text-xs text-slate-400">
              Added {{ formatDate(tool.createdAt) }}
            </p>

            <!-- Actions -->
            <div class="mt-3 flex gap-2">
              <button
                type="button"
                @click="openMaintenanceModal(tool)"
                class="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 hover:text-orange-600"
              >
                🔧 Maintenance
              </button>
              <button
                v-if="tool.status === 'AVAILABLE' || tool.status === 'IN_MAINTENANCE'"
                type="button"
                @click="handleToggleMaintenance(tool)"
                class="rounded-lg px-3 py-1.5 text-xs font-medium transition"
                :class="
                  tool.status === 'IN_MAINTENANCE'
                    ? 'border border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100'
                    : 'border border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100'
                "
              >
                {{ tool.status === 'IN_MAINTENANCE' ? '✓ Done' : '⚠ Maintenance' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- Maintenance History Modal -->
    <MaintenanceHistory
      v-if="selectedToolForMaintenance"
      :tool="selectedToolForMaintenance"
      :visible="showMaintenanceModal"
      @close="closeMaintenanceModal"
      @status-changed="handleStatusChanged"
    />
  </div>
</template>
