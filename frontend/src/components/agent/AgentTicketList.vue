<script setup>
import { useAgentStore } from '../../stores/agent'

const store = useAgentStore()

const statusLabel = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  ESCALATED: 'Escalated',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
}

const statusClass = {
  OPEN: 'bg-amber-100 text-amber-700',
  IN_PROGRESS: 'bg-blue-100 text-blue-700',
  ESCALATED: 'bg-red-100 text-red-700',
  RESOLVED: 'bg-emerald-100 text-emerald-700',
  CLOSED: 'bg-slate-100 text-slate-600',
}

const priorityClass = {
  LOW: 'bg-slate-100 text-slate-500',
  MEDIUM: 'bg-yellow-100 text-yellow-700',
  HIGH: 'bg-orange-100 text-orange-700',
  URGENT: 'bg-red-100 text-red-700',
}

const sentimentClass = {
  positive: 'bg-emerald-100 text-emerald-700',
  neutral: 'bg-slate-100 text-slate-600',
  negative: 'bg-red-100 text-red-700',
}
</script>

<template>
  <aside class="flex h-full flex-col">
    <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
      <h2 class="text-sm font-semibold text-slate-700">
        Tickets
        <span class="ml-1 text-xs font-normal text-slate-400">
          ({{ store.tickets.length }})
        </span>
      </h2>
      <button
        type="button"
        @click="store.fetchTickets()"
        :disabled="store.loading"
        class="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-500 transition hover:bg-slate-50 disabled:opacity-40"
      >
        {{ store.loading ? 'Refreshing…' : 'Refresh' }}
      </button>
    </div>

    <div class="flex-1 overflow-y-auto p-3">
      <p
        v-if="store.loading && store.tickets.length === 0"
        class="p-4 text-center text-sm text-slate-400"
      >
        Loading tickets…
      </p>
      <p
        v-else-if="store.tickets.length === 0"
        class="p-6 text-center text-sm text-slate-400"
      >
        No escalated or open tickets right now.
      </p>

      <button
        v-for="ticket in store.tickets"
        :key="ticket.id"
        type="button"
        @click="store.openTicket(ticket.id)"
        class="mb-2 block w-full rounded-xl border p-3 text-left transition"
        :class="
          store.activeTicket?.id === ticket.id
            ? 'border-indigo-400 bg-indigo-50'
            : 'border-slate-200 bg-white hover:border-indigo-200 hover:bg-slate-50'
        "
      >
        <div class="flex items-center justify-between gap-2">
          <span class="min-w-0 truncate text-sm font-medium text-slate-800">
            {{ ticket.subject }}
          </span>
          <span
            class="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold"
            :class="statusClass[ticket.status] || statusClass.OPEN"
          >
            {{ statusLabel[ticket.status] || ticket.status }}
          </span>
        </div>
        <!-- AI handoff summary takes priority; fall back to the last message -->
        <p
          v-if="ticket.aiSummary"
          class="mt-1 line-clamp-2 text-xs leading-relaxed text-amber-800/90"
          :title="ticket.aiSummary"
        >
          {{ ticket.aiSummary }}
        </p>
        <p
          v-else-if="ticket.lastMessage"
          class="mt-1 line-clamp-2 text-xs leading-relaxed text-slate-500"
        >
          {{ ticket.lastMessage }}
        </p>
        <p
          v-if="ticket.userEmail"
          class="mt-1 truncate text-[11px] text-slate-400"
          title="Customer contact"
        >
          ✉️ {{ ticket.userEmail }}
        </p>
        <div class="mt-2 flex items-center justify-between gap-2">
          <span class="flex min-w-0 items-center gap-1.5">
            <span
              class="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold"
              :class="priorityClass[ticket.priority] || priorityClass.MEDIUM"
            >
              {{ ticket.priority }}
            </span>
            <span
              v-if="ticket.sentiment"
              class="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold capitalize"
              :class="sentimentClass[ticket.sentiment] || sentimentClass.neutral"
            >
              {{ ticket.sentiment }}
            </span>
          </span>
          <span class="shrink-0 text-[10px] text-slate-400">
            {{ ticket.assignedAgent ? `@${ticket.assignedAgent}` : 'Unassigned' }}
          </span>
        </div>
      </button>
    </div>
  </aside>
</template>
