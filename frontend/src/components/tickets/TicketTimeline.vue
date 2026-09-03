<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** Array of activity log entries from the API */
  logs: {
    type: Array,
    default: () => [],
  },
  /** Whether to show only customer-visible logs */
  customerOnly: {
    type: Boolean,
    default: false,
  },
})

/**
 * Action type configuration for display
 */
const actionConfig = {
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

/**
 * Get configuration for an action type
 */
function getConfig(actionType) {
  return actionConfig[actionType] || {
    icon: '📌',
    label: actionType,
    color: 'bg-slate-100 text-slate-700',
    bgColor: 'bg-slate-50',
  }
}

/**
 * Format timestamp for display
 */
function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Filter logs based on customerOnly prop
 */
const filteredLogs = computed(() => {
  if (props.customerOnly) {
    return props.logs.filter(log => log.customerVisible)
  }
  return props.logs
})
</script>

<template>
  <div class="ticket-timeline">
    <h3 class="mb-4 text-sm font-semibold text-slate-700">
      Activity Timeline
      <span class="ml-2 text-xs font-normal text-slate-500">
        ({{ filteredLogs.length }} {{ filteredLogs.length === 1 ? 'event' : 'events' }})
      </span>
    </h3>
    
    <div v-if="filteredLogs.length === 0" class="py-8 text-center text-sm text-slate-400">
      No activity recorded yet.
    </div>
    
    <div v-else class="relative">
      <!-- Timeline line -->
      <div class="absolute left-4 top-0 bottom-0 w-0.5 bg-slate-200"></div>
      
      <!-- Timeline entries -->
      <div class="space-y-4">
        <div
          v-for="log in filteredLogs"
          :key="log.id"
          class="relative flex gap-4"
        >
          <!-- Timeline dot -->
          <div class="relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 border-white shadow-sm"
               :class="getConfig(log.actionType).bgColor">
            <span class="text-sm">{{ getConfig(log.actionType).icon }}</span>
          </div>
          
          <!-- Content -->
          <div class="flex-1 rounded-lg border border-slate-200 p-3"
               :class="getConfig(log.actionType).bgColor">
            <div class="flex items-start justify-between gap-2">
              <div>
                <span class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                      :class="getConfig(log.actionType).color">
                  {{ getConfig(log.actionType).label }}
                </span>
                <p class="mt-1 text-sm text-slate-700">{{ log.description }}</p>
                
                <!-- Before/After values for state changes -->
                <div v-if="log.previousValue || log.newValue" class="mt-2 flex items-center gap-2 text-xs">
                  <span v-if="log.previousValue" class="rounded bg-slate-200 px-2 py-0.5 text-slate-600">
                    {{ log.previousValue }}
                  </span>
                  <svg v-if="log.previousValue && log.newValue" class="h-3 w-3 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                  </svg>
                  <span v-if="log.newValue" class="rounded bg-slate-200 px-2 py-0.5 text-slate-600">
                    {{ log.newValue }}
                  </span>
                </div>
              </div>
              
              <div class="flex flex-col items-end gap-1 text-right">
                <span class="text-xs text-slate-500">{{ formatTime(log.timestamp) }}</span>
                <span class="text-xs text-slate-400">{{ log.actorName }}</span>
                <span v-if="!log.customerVisible" class="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium text-amber-700">
                  INTERNAL
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ticket-timeline {
  @apply p-4;
}
</style>
