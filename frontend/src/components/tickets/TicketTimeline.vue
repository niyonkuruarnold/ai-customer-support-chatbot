<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getTicketActivityLogsV1 } from '../../api/admin'

// ── Types ───────────────────────────────────────────────────────────
interface ActivityLog {
  id: number
  ticketId: number
  actorId: number | null
  actorRole: string       // CUSTOMER | AGENT | ADMIN | SYSTEM
  actionType: string      // STATUS_CHANGE | PRIORITY_CHANGE | ASSIGNMENT | PUBLIC_REPLY | INTERNAL_NOTE
  oldValue: string | null
  newValue: string | null
  note: string | null
  timestamp: string       // ISO datetime
}

// ── Props & Emits ──────────────────────────────────────────────────
const props = defineProps({
  ticketId: {
    type: Number,
    required: true,
  },
  /** Whether to show only customer-visible logs */
  customerOnly: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits<{
  (e: 'loaded', count: number): void
  (e: 'error', message: string): void
}>()

// ── State ──────────────────────────────────────────────────────────
const logs = ref<ActivityLog[]>([])
const loading = ref(false)
const error = ref('')

// ── Action type configuration for display ──────────────────────────
const actionConfig: Record<string, { icon: string; label: string; color: string; bgColor: string }> = {
  CREATED: {
    icon: '🎫',
    label: 'Ticket Created',
    color: 'bg-blue-100 text-blue-700',
    bgColor: 'bg-blue-50',
  },
  STATUS_CHANGE: {
    icon: '🔄',
    label: 'Status Changed',
    color: 'bg-indigo-100 text-indigo-700',
    bgColor: 'bg-indigo-50',
  },
  PRIORITY_CHANGE: {
    icon: '⚡',
    label: 'Priority Changed',
    color: 'bg-amber-100 text-amber-700',
    bgColor: 'bg-amber-50',
  },
  ASSIGNMENT: {
    icon: '👤',
    label: 'Assignment Changed',
    color: 'bg-purple-100 text-purple-700',
    bgColor: 'bg-purple-50',
  },
  PUBLIC_REPLY: {
    icon: '💬',
    label: 'Public Reply',
    color: 'bg-emerald-100 text-emerald-700',
    bgColor: 'bg-emerald-50',
  },
  INTERNAL_NOTE: {
    icon: '📝',
    label: 'Internal Note',
    color: 'bg-slate-100 text-slate-700',
    bgColor: 'bg-slate-50',
  },
  REPLY: {
    icon: '💬',
    label: 'Reply Added',
    color: 'bg-emerald-100 text-emerald-700',
    bgColor: 'bg-emerald-50',
  },
  NOTE: {
    icon: '📝',
    label: 'Internal Note',
    color: 'bg-slate-100 text-slate-700',
    bgColor: 'bg-slate-50',
  },
  REOPEN: {
    icon: '🔁',
    label: 'Ticket Reopened',
    color: 'bg-orange-100 text-orange-700',
    bgColor: 'bg-orange-50',
  },
}

// ── Status badge colors ────────────────────────────────────────────
const statusBadgeClass: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700',
  OPEN: 'bg-sky-100 text-sky-700',
  PENDING_CUSTOMER: 'bg-amber-100 text-amber-700',
  PENDING_INTERNAL: 'bg-purple-100 text-purple-700',
  RESOLVED: 'bg-emerald-100 text-emerald-700',
  CLOSED: 'bg-slate-200 text-slate-600',
  REOPENED: 'bg-orange-100 text-orange-700',
}

// ── Helpers ────────────────────────────────────────────────────────
function getConfig(actionType: string) {
  return actionConfig[actionType] || {
    icon: '📌',
    label: actionType,
    color: 'bg-slate-100 text-slate-700',
    bgColor: 'bg-slate-50',
  }
}

/**
 * Format timestamp as a relative string (e.g. "3 minutes ago", "2 hours ago").
 */
function relativeTime(timestamp: string | null): string {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = Date.now()
  const diffMs = now - date.getTime()
  const diffSec = Math.floor(diffMs / 1000)

  if (diffSec < 0) return 'just now'
  if (diffSec < 60) return 'just now'

  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `${diffMin}m ago`

  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr}h ago`

  const diffDay = Math.floor(diffHr / 24)
  if (diffDay < 7) return `${diffDay}d ago`

  // Older than a week — show absolute date
  return date.toLocaleDateString([], {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Format a full absolute timestamp for tooltips.
 */
function absoluteTime(timestamp: string | null): string {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleString([], {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

/**
 * Build a human-readable description for a log entry.
 */
function describeLog(log: ActivityLog): string {
  if (log.note) return log.note

  switch (log.actionType) {
    case 'STATUS_CHANGE':
      if (log.oldValue && log.newValue) {
        return `Status changed from ${formatStatusLabel(log.oldValue)} to ${formatStatusLabel(log.newValue)}`
      }
      if (log.newValue) return `Status set to ${formatStatusLabel(log.newValue)}`
      return 'Status changed'

    case 'PRIORITY_CHANGE':
      if (log.oldValue && log.newValue) {
        return `Priority changed from ${log.oldValue} to ${log.newValue}`
      }
      if (log.newValue) return `Priority set to ${log.newValue}`
      return 'Priority changed'

    case 'ASSIGNMENT':
      if (log.newValue) return `Assigned to ${log.newValue}`
      return 'Assignment changed'

    case 'PUBLIC_REPLY':
      return 'Sent a reply to the customer'

    case 'INTERNAL_NOTE':
      return 'Added an internal note'

    case 'CREATED':
      return 'Ticket was created'

    default:
      return log.actionType
  }
}

function formatStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    NEW: 'New',
    OPEN: 'Open',
    PENDING_CUSTOMER: 'Pending Customer',
    PENDING_INTERNAL: 'Pending Internal',
    RESOLVED: 'Resolved',
    CLOSED: 'Closed',
    REOPENED: 'Reopened',
  }
  return labels[status] || status
}

// ── Data fetching ──────────────────────────────────────────────────
async function fetchLogs() {
  if (!props.ticketId) return
  loading.value = true
  error.value = ''
  try {
    const data = await getTicketActivityLogsV1(props.ticketId, props.customerOnly)
    logs.value = data ?? []
    emit('loaded', logs.value.length)
  } catch (err: any) {
    error.value = err?.response?.data?.message || 'Failed to load activity logs.'
    logs.value = []
    emit('error', error.value)
  } finally {
    loading.value = false
  }
}

// ── Lifecycle ──────────────────────────────────────────────────────
onMounted(fetchLogs)

// Re-fetch when ticketId changes
watch(() => props.ticketId, (newId) => {
  if (newId) fetchLogs()
})

// Expose refresh for parent components
defineExpose({ refresh: fetchLogs })
</script>

<template>
  <div class="ticket-timeline">
    <!-- Header -->
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-sm font-semibold text-slate-700">
        Activity Timeline
        <span v-if="!loading && !error" class="ml-2 text-xs font-normal text-slate-500">
          ({{ logs.length }} {{ logs.length === 1 ? 'event' : 'events' }})
        </span>
      </h3>
      <button
        type="button"
        @click="fetchLogs"
        :disabled="loading"
        class="flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-500 transition hover:bg-slate-50 disabled:opacity-40"
        aria-label="Refresh timeline"
      >
        <svg
          :class="['size-3 transition', loading ? 'animate-spin' : '']"
          fill="none"
          viewBox="0 0 24 24"
          stroke-width="2"
          stroke="currentColor"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182" />
        </svg>
        Refresh
      </button>
    </div>

    <!-- Loading state -->
    <div v-if="loading && logs.length === 0" class="py-8 text-center">
      <svg class="mx-auto size-5 animate-spin text-slate-400" viewBox="0 0 24 24" fill="none">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      <p class="mt-2 text-sm text-slate-400">Loading activity timeline…</p>
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="rounded-xl border border-red-200 bg-red-50 p-4 text-center text-sm text-red-700">
      {{ error }}
      <button
        type="button"
        @click="fetchLogs"
        class="ml-2 underline transition hover:text-red-900"
      >
        Retry
      </button>
    </div>

    <!-- Empty state -->
    <div v-else-if="logs.length === 0" class="py-8 text-center text-sm text-slate-400">
      No activity recorded yet.
    </div>

    <!-- Timeline -->
    <div v-else class="relative">
      <!-- Connecting vertical line -->
      <div class="absolute left-4 top-0 bottom-0 w-0.5 bg-slate-200"></div>

      <!-- Timeline entries -->
      <div class="space-y-4">
        <div
          v-for="log in logs"
          :key="log.id"
          class="relative flex gap-4"
        >
          <!-- Timeline dot / icon -->
          <div
            class="relative z-10 flex size-8 shrink-0 items-center justify-center rounded-full border-2 border-white shadow-sm"
            :class="getConfig(log.actionType).bgColor"
          >
            <span class="text-sm">{{ getConfig(log.actionType).icon }}</span>
          </div>

          <!-- Content card -->
          <div
            class="flex-1 rounded-lg border border-slate-200 p-3 transition"
            :class="getConfig(log.actionType).bgColor"
          >
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0 flex-1">
                <!-- Action tag badge -->
                <div class="flex flex-wrap items-center gap-1.5">
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                    :class="getConfig(log.actionType).color"
                  >
                    {{ getConfig(log.actionType).label }}
                  </span>

                  <!-- Actor role badge -->
                  <span
                    class="inline-flex items-center rounded-full bg-white/70 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide border border-slate-200"
                  >
                    {{ log.actorRole }}
                  </span>
                </div>

                <!-- Description -->
                <p class="mt-1.5 text-sm leading-relaxed text-slate-700">
                  {{ describeLog(log) }}
                </p>

                <!-- Before → After pills -->
                <div
                  v-if="log.actionType === 'STATUS_CHANGE' && log.oldValue && log.newValue"
                  class="mt-2 flex items-center gap-2 text-xs"
                >
                  <span
                    class="rounded-full px-2.5 py-0.5 font-medium"
                    :class="statusBadgeClass[log.oldValue] || 'bg-slate-200 text-slate-600'"
                  >
                    {{ formatStatusLabel(log.oldValue) }}
                  </span>
                  <svg class="size-3 shrink-0 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" />
                  </svg>
                  <span
                    class="rounded-full px-2.5 py-0.5 font-medium"
                    :class="statusBadgeClass[log.newValue] || 'bg-slate-200 text-slate-600'"
                  >
                    {{ formatStatusLabel(log.newValue) }}
                  </span>
                </div>

                <!-- Generic before/after for non-status changes -->
                <div
                  v-else-if="log.oldValue && log.newValue"
                  class="mt-2 flex items-center gap-2 text-xs"
                >
                  <span class="rounded bg-slate-200 px-2 py-0.5 font-medium text-slate-600">
                    {{ log.oldValue }}
                  </span>
                  <svg class="size-3 shrink-0 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" />
                  </svg>
                  <span class="rounded bg-slate-200 px-2 py-0.5 font-medium text-slate-600">
                    {{ log.newValue }}
                  </span>
                </div>
              </div>

              <!-- Timestamp + visibility indicator -->
              <div class="flex flex-col items-end gap-1 text-right shrink-0">
                <time
                  class="text-xs text-slate-500 tabular-nums"
                  :title="absoluteTime(log.timestamp)"
                >
                  {{ relativeTime(log.timestamp) }}
                </time>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@reference "../../style.css";
.ticket-timeline {
  @apply p-4;
}
</style>
