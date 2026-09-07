<script setup lang="ts">
import { computed, ref, toRef } from 'vue'
import {
  useTicketStateMachine,
  ALL_STATUSES,
  STATUS_LABELS,
} from '../../composables/useTicketStateMachine'

// ── Props ──────────────────────────────────────────────────────────
const props = defineProps({
  /** Current ticket status string */
  status: {
    type: String,
    default: null,
  },
  /** Ticket ID for API calls */
  ticketId: {
    type: Number,
    required: true,
  },
  /** Whether the parent is currently processing (disables the dropdown) */
  disabled: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits<{
  (e: 'status-change', ticketId: number, newStatus: string): void
  (e: 'error', ticketId: number, message: string): void
}>()

// ── State machine ──────────────────────────────────────────────────
const currentStatus = toRef(props, 'status')
const {
  canTransitionTo,
  isTerminal,
  disabledStatuses,
  getLabel,
  getBadgeClass,
} = useTicketStateMachine(currentStatus)

const updating = ref(false)

// ── Handlers ───────────────────────────────────────────────────────
async function handleStatusChange(newStatus: string) {
  if (!props.ticketId || newStatus === props.status || updating.value) return

  // Double-check the transition is valid
  if (!canTransitionTo(newStatus)) {
    emit('error', props.ticketId, `Invalid transition: ${props.status} → ${newStatus}`)
    return
  }

  updating.value = true
  try {
    // Use the v1 PATCH endpoint
    const apiBase = (await import.meta.env.VITE_API_BASE_URL) || 'http://localhost:8080/api'
    // Import dynamically to avoid circular deps — the admin.js functions are fine here
    const { updateTicketStatusV1 } = await import('../../api/admin')
    await updateTicketStatusV1(props.ticketId, newStatus)
    emit('status-change', props.ticketId, newStatus)
  } catch (err: any) {
    const message =
      err?.response?.data?.message ||
      err?.message ||
      `Failed to update status to ${getLabel(newStatus)}`
    emit('error', props.ticketId, message)
  } finally {
    updating.value = false
  }
}

// ── Human-readable status descriptions for tooltips ────────────────
const TRANSITION_HELPERS: Record<string, string> = {
  NEW: 'Fresh ticket, awaiting triage',
  OPEN: 'Under active investigation',
  PENDING_CUSTOMER: 'Waiting for customer response',
  PENDING_INTERNAL: 'Waiting on internal team',
  RESOLVED: 'Issue addressed, pending close',
  CLOSED: 'Ticket fully closed',
  REOPENED: 'Reopened for follow-up',
}
</script>

<template>
  <div class="ticket-status-control">
    <!-- Current status label -->
    <label class="mb-1.5 block text-xs font-medium text-slate-600">
      Ticket Status
    </label>

    <div class="flex items-center gap-2">
      <!-- Status dropdown -->
      <select
        :value="status"
        :disabled="disabled || updating || isTerminal"
        class="flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100 disabled:cursor-not-allowed disabled:opacity-50"
        :aria-label="`Change ticket status (currently ${getLabel(status || '')})`"
        @change="handleStatusChange(($event.target as HTMLSelectElement).value)"
      >
        <option
          v-for="s in ALL_STATUSES"
          :key="s"
          :value="s"
          :disabled="s !== status && !canTransitionTo(s)"
        >
          {{ getLabel(s) }}{{ s === status ? ' (current)' : !canTransitionTo(s) ? ' ✕' : '' }}
        </option>
      </select>

      <!-- Loading spinner -->
      <svg
        v-if="updating"
        class="size-4 shrink-0 animate-spin text-indigo-500"
        viewBox="0 0 24 24"
        fill="none"
        aria-label="Updating"
      >
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
    </div>

    <!-- Current status info line -->
    <p v-if="status && TRANSITION_HELPERS[status]" class="mt-1.5 text-[11px] text-slate-400">
      {{ TRANSITION_HELPERS[status] }}
    </p>

    <!-- Terminal state notice -->
    <div
      v-if="isTerminal"
      class="mt-2 flex items-center gap-1.5 rounded-lg bg-slate-100 px-3 py-2 text-xs text-slate-500"
    >
      <svg fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-3.5 shrink-0">
        <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
      </svg>
      This ticket is closed. Only admin can reopen it.
    </div>

    <!-- Quick action buttons -->
    <div v-if="!isTerminal && status" class="mt-2 flex flex-wrap gap-1.5">
      <button
        v-if="canTransitionTo('RESOLVED')"
        type="button"
        :disabled="disabled || updating"
        class="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700 transition hover:bg-emerald-100 disabled:opacity-40"
        @click="handleStatusChange('RESOLVED')"
      >
        ✓ Resolve
      </button>
      <button
        v-if="canTransitionTo('PENDING_CUSTOMER')"
        type="button"
        :disabled="disabled || updating"
        class="rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-medium text-amber-700 transition hover:bg-amber-100 disabled:opacity-40"
        @click="handleStatusChange('PENDING_CUSTOMER')"
      >
        ⏳ Pending Customer
      </button>
      <button
        v-if="canTransitionTo('PENDING_INTERNAL')"
        type="button"
        :disabled="disabled || updating"
        class="rounded-full border border-purple-200 bg-purple-50 px-3 py-1 text-xs font-medium text-purple-700 transition hover:bg-purple-100 disabled:opacity-40"
        @click="handleStatusChange('PENDING_INTERNAL')"
      >
        🔒 Pending Internal
      </button>
      <button
        v-if="canTransitionTo('CLOSED')"
        type="button"
        :disabled="disabled || updating"
        class="rounded-full border border-slate-200 bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600 transition hover:bg-slate-200 disabled:opacity-40"
        @click="handleStatusChange('CLOSED')"
      >
        ✕ Close
      </button>
      <button
        v-if="canTransitionTo('OPEN')"
        type="button"
        :disabled="disabled || updating"
        class="rounded-full border border-sky-200 bg-sky-50 px-3 py-1 text-xs font-medium text-sky-700 transition hover:bg-sky-100 disabled:opacity-40"
        @click="handleStatusChange('OPEN')"
      >
        🔓 Reopen
      </button>
    </div>
  </div>
</template>
