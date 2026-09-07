import { computed, type ComputedRef } from 'vue'

/**
 * Valid state machine transitions for ticket lifecycle (Section 6.5).
 *
 * NEW -> OPEN
 * OPEN -> PENDING_CUSTOMER | PENDING_INTERNAL | RESOLVED
 * PENDING_CUSTOMER -> OPEN | RESOLVED
 * PENDING_INTERNAL -> OPEN | RESOLVED
 * RESOLVED -> CLOSED | REOPENED
 * REOPENED -> OPEN
 */
const TRANSITIONS: Record<string, string[]> = {
  NEW: ['OPEN'],
  OPEN: ['PENDING_CUSTOMER', 'PENDING_INTERNAL', 'RESOLVED'],
  PENDING_CUSTOMER: ['OPEN', 'RESOLVED'],
  PENDING_INTERNAL: ['OPEN', 'RESOLVED'],
  RESOLVED: ['CLOSED', 'REOPENED'],
  REOPENED: ['OPEN'],
  CLOSED: [],
}

/** All possible ticket statuses */
export const ALL_STATUSES = Object.keys(TRANSITIONS)

/** Human-readable status labels */
export const STATUS_LABELS: Record<string, string> = {
  NEW: 'New',
  OPEN: 'Open',
  PENDING_CUSTOMER: 'Pending Customer',
  PENDING_INTERNAL: 'Pending Internal',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  REOPENED: 'Reopened',
}

/** Tailwind badge classes for each status */
export const STATUS_BADGE_CLASS: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700',
  OPEN: 'bg-sky-100 text-sky-700',
  PENDING_CUSTOMER: 'bg-amber-100 text-amber-700',
  PENDING_INTERNAL: 'bg-purple-100 text-purple-700',
  RESOLVED: 'bg-emerald-100 text-emerald-700',
  CLOSED: 'bg-slate-200 text-slate-600',
  REOPENED: 'bg-orange-100 text-orange-700',
}

/**
 * Vue composable providing ticket state-machine helpers.
 *
 * @param currentStatus - reactive ref or getter for the ticket's current status string
 * @returns computed helpers for valid transitions, disabled statuses, etc.
 */
export function useTicketStateMachine(
  currentStatus: ComputedRef<string | null> | (() => string | null),
) {
  /** The valid next statuses from the current state */
  const validNextStatuses = computed(() => {
    const status = typeof currentStatus === 'function' ? currentStatus() : currentStatus.value
    if (!status) return [] as string[]
    return TRANSITIONS[status] ?? []
  })

  /** Whether a specific target status is a valid next state */
  function canTransitionTo(targetStatus: string): boolean {
    const status = typeof currentStatus === 'function' ? currentStatus() : currentStatus.value
    if (!status) return false
    const allowed = TRANSITIONS[status] ?? []
    return allowed.includes(targetStatus)
  }

  /** Whether the ticket is in a terminal (closed) state */
  const isTerminal = computed(() => {
    const status = typeof currentStatus === 'function' ? currentStatus() : currentStatus.value
    return status === 'CLOSED'
  })

  /** Whether the ticket can be reopened */
  const canReopen = computed(() => canTransitionTo('REOPENED'))

  /** Whether the ticket can be resolved */
  const canResolve = computed(() => canTransitionTo('RESOLVED'))

  /** All statuses that are NOT valid next states (for disabling dropdown options) */
  const disabledStatuses = computed(() => {
    const valid = new Set(validNextStatuses.value)
    // Keep the current status as a disabled option too (already selected)
    const status = typeof currentStatus === 'function' ? currentStatus() : currentStatus.value
    if (status) valid.add(status)
    return ALL_STATUSES.filter((s) => !valid.has(s))
  })

  /** Get the human-readable label for a status */
  function getLabel(status: string): string {
    return STATUS_LABELS[status] || status
  }

  /** Get the badge CSS class for a status */
  function getBadgeClass(status: string): string {
    return STATUS_BADGE_CLASS[status] || 'bg-slate-100 text-slate-600'
  }

  return {
    validNextStatuses,
    canTransitionTo,
    isTerminal,
    canReopen,
    canResolve,
    disabledStatuses,
    getLabel,
    getBadgeClass,
  }
}
