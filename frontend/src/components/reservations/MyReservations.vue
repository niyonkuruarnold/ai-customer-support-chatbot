<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useAgentStore } from '../../stores/agent'
import { useReservationStore } from '../../stores/reservation'
import DateRangePicker from './DateRangePicker.vue'

const emit = defineEmits(['switch-to-chat'])

const agentStore = useAgentStore()
const reservationStore = useReservationStore()

// Auth state
const username = ref('')
const password = ref('')
const loginLoading = ref(false)
const loginError = ref('')

// Tab state: 'borrows' = My Active Borrows, 'requests' = My Tool Lending Requests
const activeTab = ref('borrows')

// Date picker state
const pickerRef = ref(null)
const selectedToolId = ref('')
const reservationNotes = ref('')
const pickerStart = ref('')
const pickerEnd = ref('')
const createLoading = ref(false)
const createError = ref('')

const isAuthenticated = computed(() => agentStore.authenticated)

onMounted(() => {
  if (isAuthenticated.value && agentStore.userId) {
    reservationStore.fetchMyReservations(agentStore.userId)
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
      await reservationStore.fetchMyReservations(agentStore.userId)
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
}

function handleDateChange(dates) {
  if (dates) {
    pickerStart.value = dates.startDate
    pickerEnd.value = dates.endDate
  } else {
    pickerStart.value = ''
    pickerEnd.value = ''
  }
}

async function handleCreateReservation() {
  if (!selectedToolId.value || !pickerStart.value || !pickerEnd.value) {
    createError.value = 'Please select a tool and date range.'
    return
  }
  createLoading.value = true
  createError.value = ''
  try {
    await reservationStore.requestReservation({
      toolId: Number(selectedToolId.value),
      borrowerId: agentStore.userId,
      startDate: pickerStart.value,
      endDate: pickerEnd.value,
      notes: reservationNotes.value || null,
    })
    // Reset form
    selectedToolId.value = ''
    reservationNotes.value = ''
    pickerRef.value?.clear()
  } catch {
    createError.value = reservationStore.error || 'Failed to create reservation'
  } finally {
    createLoading.value = false
  }
}

const statusLabel = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  CHECKED_OUT: 'Checked Out',
  RETURNED: 'Returned',
  REJECTED: 'Rejected',
}

const statusClass = {
  PENDING: 'bg-amber-100 text-amber-700',
  APPROVED: 'bg-emerald-100 text-emerald-700',
  CHECKED_OUT: 'bg-blue-100 text-blue-700',
  RETURNED: 'bg-slate-100 text-slate-600',
  REJECTED: 'bg-red-100 text-red-600',
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
          class="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-emerald-500 to-teal-600 text-white shadow-md"
          aria-hidden="true"
        >
          🛠️
        </div>
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-base font-semibold">My Reservations</h1>
          <p class="truncate text-xs text-slate-500">
            <template v-if="isAuthenticated">
              Active borrows and lending requests
            </template>
            <template v-else>Tool borrowing & scheduling</template>
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
          Sign in to view your tool reservations and borrowing history.
        </p>
        <label class="mt-4 block text-sm font-medium text-slate-700">
          Username
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            required
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
          />
        </label>
        <label class="mt-3 block text-sm font-medium text-slate-700">
          Password
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
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

    <!-- Main content -->
    <main v-else class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto max-w-5xl space-y-6 p-6">

        <!-- Tab bar -->
        <div class="flex gap-1 rounded-xl border border-slate-200 bg-white p-1">
          <button
            type="button"
            @click="activeTab = 'borrows'"
            class="flex-1 rounded-lg px-4 py-2.5 text-sm font-medium transition"
            :class="
              activeTab === 'borrows'
                ? 'bg-emerald-600 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-50'
            "
          >
            📦 My Active Borrows
            <span
              v-if="reservationStore.activeReservations.length"
              class="ml-1.5 inline-flex size-5 items-center justify-center rounded-full text-[11px]"
              :class="
                activeTab === 'borrows'
                  ? 'bg-white/20 text-white'
                  : 'bg-emerald-100 text-emerald-700'
              "
            >
              {{ reservationStore.activeReservations.length }}
            </span>
          </button>
          <button
            type="button"
            @click="activeTab = 'requests'"
            class="flex-1 rounded-lg px-4 py-2.5 text-sm font-medium transition"
            :class="
              activeTab === 'requests'
                ? 'bg-teal-600 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-50'
            "
          >
            📋 My Tool Lending Requests
            <span
              v-if="reservationStore.pastReservations.length"
              class="ml-1.5 inline-flex size-5 items-center justify-center rounded-full text-[11px]"
              :class="
                activeTab === 'requests'
                  ? 'bg-white/20 text-white'
                  : 'bg-teal-100 text-teal-700'
              "
            >
              {{ reservationStore.pastReservations.length }}
            </span>
          </button>
        </div>

        <!-- New reservation form -->
        <section class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 class="text-sm font-semibold text-slate-800">Request a new reservation</h2>
          <p class="mt-1 text-xs text-slate-500">
            Select a tool and date range to request a borrow.
          </p>
          <div class="mt-4 space-y-4">
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-700">Tool ID</label>
              <input
                v-model="selectedToolId"
                type="number"
                min="1"
                placeholder="e.g. 1"
                class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
              />
            </div>
            <DateRangePicker
              ref="pickerRef"
              @change="handleDateChange"
              :disabled="createLoading"
            />
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-700">Notes (optional)</label>
              <input
                v-model="reservationNotes"
                type="text"
                placeholder="What do you need it for?"
                class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
              />
            </div>
            <p v-if="createError" class="text-sm text-red-600" role="alert">
              {{ createError }}
            </p>
            <button
              type="button"
              @click="handleCreateReservation"
              :disabled="createLoading || !selectedToolId || !pickerStart || !pickerEnd"
              class="rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white transition enabled:hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {{ createLoading ? 'Requesting…' : 'Request reservation' }}
            </button>
          </div>
        </section>

        <!-- Loading state -->
        <div
          v-if="reservationStore.isLoading && !reservationStore.reservations.length"
          class="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white p-8 text-sm text-slate-400"
        >
          <svg class="size-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          Loading reservations…
        </div>

        <!-- Active Borrows tab -->
        <section v-if="activeTab === 'borrows'">
          <h3 class="mb-3 text-sm font-semibold text-slate-800">Active borrows</h3>
          <div
            v-if="reservationStore.activeReservations.length === 0"
            class="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-400"
          >
            No active borrows — request a reservation above.
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="r in reservationStore.activeReservations"
              :key="r.id"
              class="flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4 transition hover:shadow-sm"
            >
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <span class="font-medium text-slate-800">Tool #{{ r.toolId }}</span>
                  <span
                    class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                    :class="statusClass[r.status]"
                  >
                    {{ statusLabel[r.status] }}
                  </span>
                </div>
                <p class="mt-1 text-xs text-slate-500">
                  {{ formatDate(r.startDate) }} → {{ formatDate(r.endDate) }}
                </p>
                <p v-if="r.notes" class="mt-1 text-xs text-slate-400 italic">
                  "{{ r.notes }}"
                </p>
              </div>
              <div class="flex shrink-0 gap-2">
                <button
                  v-if="r.status === 'APPROVED'"
                  type="button"
                  @click="reservationStore.checkout(r.id)"
                  class="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700 transition hover:bg-blue-100"
                >
                  Check out
                </button>
                <button
                  v-if="r.status === 'CHECKED_OUT'"
                  type="button"
                  @click="reservationStore.returnTool(r.id)"
                  class="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700 transition hover:bg-emerald-100"
                >
                  Return
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- Lending Requests tab (past / completed) -->
        <section v-if="activeTab === 'requests'">
          <h3 class="mb-3 text-sm font-semibold text-slate-800">Lending request history</h3>
          <div
            v-if="reservationStore.pastReservations.length === 0"
            class="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-400"
          >
            No past reservations yet.
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="r in reservationStore.pastReservations"
              :key="r.id"
              class="flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4"
            >
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <span class="font-medium text-slate-800">Tool #{{ r.toolId }}</span>
                  <span
                    class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                    :class="statusClass[r.status]"
                  >
                    {{ statusLabel[r.status] }}
                  </span>
                </div>
                <p class="mt-1 text-xs text-slate-500">
                  {{ formatDate(r.startDate) }} → {{ formatDate(r.endDate) }}
                </p>
                <p v-if="r.notes" class="mt-1 text-xs text-slate-400 italic">
                  "{{ r.notes }}"
                </p>
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>
