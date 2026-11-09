<script setup>
import { computed, nextTick, ref, watch, onMounted, onUnmounted } from 'vue'
import { useAgentStore } from '../../stores/agent'
import { useWebSocket } from '../../composables/useWebSocket'
import ChatMessage from '../ChatMessage.vue'
import { updateTicketStatus, updateTicketAgent } from '../../api/admin'

const store = useAgentStore()

// WebSocket for real-time messaging
const {
  isConnected: wsConnected,
  connect: wsConnect,
  disconnect: wsDisconnect,
  sendChatMessage,
  subscribeToSession,
  subscribeToAgentChannel,
} = useWebSocket()

const replyText = ref('')
const noteText = ref('')
const feedRef = ref(null)
const statusLoading = ref(false)
const agentLoading = ref(false)
const assigneeInput = ref('')
const isNoteMode = ref(false) // Toggle between reply and note mode

let sessionSubId = null
let agentSubId = null

const SENDER_TO_ROLE = { USER: 'user', AI: 'assistant', AGENT: 'agent' }

const isConnected = computed(() =>
  ['ESCALATED', 'IN_PROGRESS'].includes(store.activeStatus),
)

const summaryBullets = computed(() =>
  (store.activeSummary || '').split('\n').filter((b) => b.trim().length > 0),
)

// WebSocket connection status
const connectionStatus = computed(() => {
  if (wsConnected.value) return 'connected'
  return 'polling'
})

const sentimentClass = {
  positive: 'bg-emerald-100 text-emerald-700',
  neutral: 'bg-slate-100 text-slate-600',
  negative: 'bg-red-100 text-red-700',
}

watch(
  () => store.activeMessages,
  async () => {
    await nextTick()
    const el = feedRef.value
    if (el) el.scrollTop = el.scrollHeight
  },
  { deep: true },
)

function senderRole(sender) {
  return SENDER_TO_ROLE[sender] ?? 'assistant'
}

function toTimestamp(value) {
  if (!value) return Date.now()
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? Date.now() : parsed
}

function formatTime(value) {
  if (!value) return ''
  return new Date(value).toLocaleString([], {
    hour: '2-digit',
    minute: '2-digit',
  })
}

async function sendReply() {
  const content = replyText.value.trim()
  if (!content) return
  
  // Send via WebSocket for instant delivery
  if (wsConnected.value && store.activeTicket) {
    sendChatMessage(store.activeTicket.sessionId, {
      sender: 'AGENT',
      content,
      internal: false,
    })
    replyText.value = ''
  } else {
    // Fallback to REST API
    const ok = await store.sendReply(replyText.value)
    if (ok) replyText.value = ''
  }
}

function saveNote() {
  const content = noteText.value.trim()
  if (!content) return
  
  // Send via WebSocket for instant delivery to other agents
  if (wsConnected.value && store.activeTicket) {
    sendChatMessage(store.activeTicket.sessionId, {
      sender: 'AGENT',
      content,
      internal: true,
    })
    noteText.value = ''
    isNoteMode.value = false
  } else {
    // Fallback to REST API
    store.addNote(noteText.value)
    noteText.value = ''
  }
}

// Subscribe to WebSocket when ticket is selected
watch(
  () => store.activeTicket,
  async (newTicket, oldTicket) => {
    // Cleanup old subscriptions
    if (oldTicket) {
      if (sessionSubId) {
        // unsub handled by composable cleanup
      }
      if (agentSubId) {
        // unsub handled by composable cleanup
      }
    }
    
    // Connect WebSocket if not already connected
    if (newTicket && !wsConnected.value) {
      wsConnect()
    }
    
    // Subscribe to session channel (all messages)
    if (newTicket) {
      sessionSubId = subscribeToSession(newTicket.sessionId, (message) => {
        // Handle incoming messages - they'll be picked up by polling
        // but WebSocket provides instant delivery
        console.debug('[Agent] Received message:', message)
      })
      
      // Subscribe to agent-only channel (internal notes)
      agentSubId = subscribeToAgentChannel(newTicket.sessionId, (message) => {
        // Handle internal notes from other agents
        console.debug('[Agent] Received internal note:', message)
      })
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  wsDisconnect()
})

/** Status update toggles (RESOLVED, CLOSED) */
const STATUS_OPTIONS = [
  { value: 'OPEN', label: 'Open', class: 'bg-sky-100 text-sky-700' },
  { value: 'IN_PROGRESS', label: 'In Progress', class: 'bg-indigo-100 text-indigo-700' },
  { value: 'RESOLVED', label: 'Resolved', class: 'bg-emerald-100 text-emerald-700' },
  { value: 'CLOSED', label: 'Closed', class: 'bg-slate-200 text-slate-600' },
]

const currentStatusLabel = computed(() => {
  const opt = STATUS_OPTIONS.find((s) => s.value === store.activeStatus)
  return opt ? opt.label : store.activeStatus
})
const currentStatusClass = computed(() => {
  const opt = STATUS_OPTIONS.find((s) => s.value === store.activeStatus)
  return opt ? opt.class : 'bg-slate-100 text-slate-600'
})

async function handleStatusChange(newStatus) {
  if (!store.activeTicket || newStatus === store.activeStatus) return
  statusLoading.value = true
  try {
    const updated = await updateTicketStatus(store.activeTicket.id, newStatus)
    store.activeTicket = { ...store.activeTicket, status: updated.status }
  } catch {
    store.activeError = 'Could not update ticket status.'
  } finally {
    statusLoading.value = false
  }
}

/** Agent assignment */
async function handleAssign() {
  if (!store.activeTicket || !assigneeInput.value.trim()) return
  agentLoading.value = true
  try {
    const updated = await updateTicketAgent(store.activeTicket.id, assigneeInput.value.trim())
    store.activeTicket = { ...store.activeTicket, assignedAgent: updated.assignedAgent }
    assigneeInput.value = ''
  } catch {
    store.activeError = 'Could not reassign ticket.'
  } finally {
    agentLoading.value = false
  }
}
</script>

<template>
  <section class="flex h-full min-w-0 flex-col bg-slate-100">
    <template v-if="store.activeTicket">
      <!-- Ticket header -->
      <div class="border-b border-slate-200 bg-white px-4 py-3">
        <div class="flex flex-wrap items-center gap-2">
          <h2 class="min-w-0 flex-1 truncate text-sm font-semibold text-slate-800">
            {{ store.activeTicket.subject }}
          </h2>
          <span
            v-if="store.activeSentiment"
            class="rounded-full px-2 py-0.5 text-[10px] font-semibold capitalize"
            :class="sentimentClass[store.activeSentiment] || sentimentClass.neutral"
          >
            {{ store.activeSentiment }} sentiment
          </span>
          <span
            class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-600"
          >
            #{{ store.activeTicket.id }}
          </span>
        </div>
        <div class="mt-2 flex flex-wrap items-center justify-between gap-2">
          <p class="text-xs text-slate-500">
            Assigned to
            <span class="font-medium text-slate-700">
              {{ store.activeTicket.assignedAgent || 'nobody yet' }}
            </span>
            · Updated {{ formatTime(store.activeTicket.updatedAt) }}
            <template v-if="store.activeTicket.userEmail">
              · Customer
              <span class="font-medium text-slate-700">
                {{ store.activeTicket.userEmail }}
              </span>
            </template>
          </p>
          <div class="flex flex-wrap items-center gap-2">
            <!-- Status update dropdown -->
            <select
              :value="store.activeStatus"
              :disabled="statusLoading"
              @change="handleStatusChange($event.target.value)"
              data-test="status-select"
              class="rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs font-medium outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100"
              :class="currentStatusClass"
            >
              <option v-for="s in STATUS_OPTIONS" :key="s.value" :value="s.value">
                {{ s.label }}
              </option>
            </select>
            <!-- Agent assignment -->
            <div class="flex items-center gap-1">
              <input
                v-model="assigneeInput"
                type="text"
                :placeholder="store.activeTicket.assignedAgent || 'Assign agent…'"
                data-test="assign-input"
                class="w-28 rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
                @keydown.enter.prevent="handleAssign"
              />
              <button
                type="button"
                @click="handleAssign"
                :disabled="agentLoading || !assigneeInput.trim()"
                class="rounded-lg border border-slate-300 px-2 py-1.5 text-xs font-medium text-slate-600 transition enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                {{ agentLoading ? '…' : 'Assign' }}
              </button>
            </div>
            <!-- Take over / Resolve -->
            <button
              v-if="!store.activeIsAssigned"
              type="button"
              @click="store.takeOver()"
              class="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-indigo-700"
            >
              Take over
            </button>
            <button
              v-else
              type="button"
              @click="store.resolve()"
              class="rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-emerald-700"
            >
              Resolve
            </button>
          </div>
        </div>
        <p
          v-if="store.activeError"
          class="mt-2 text-xs text-red-600"
          role="alert"
        >
          {{ store.activeError }}
        </p>
      </div>

      <!-- Real-time connection status -->
      <div
        v-if="isConnected"
        class="border-b border-emerald-200 bg-emerald-50 px-4 py-2"
      >
        <div class="flex items-center justify-between">
          <p class="flex items-center gap-2 text-xs font-medium text-emerald-700">
            <span class="relative flex size-2" aria-hidden="true">
              <span
                class="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-75"
              ></span>
              <span
                class="relative inline-flex size-2 rounded-full bg-emerald-500"
              ></span>
            </span>
            <template v-if="wsConnected">
              <span class="text-emerald-600">● Live</span> — Real-time WebSocket connected
            </template>
            <template v-else>
              <span class="text-amber-600">● Polling</span> — Updates refresh every 5s
            </template>
          </p>
        </div>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto">
        <!-- Pinned AI handoff summary -->
        <div
          v-if="summaryBullets.length"
          class="mx-4 mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4"
        >
          <p class="flex items-center gap-1.5 text-xs font-semibold text-amber-700">
            <svg
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.8"
              stroke="currentColor"
              class="size-4"
              aria-hidden="true"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M9.813 15.904 9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09ZM18.259 8.715 18 9.75l-.259-1.035a3.375 3.375 0 0 0-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 0 0 2.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 0 0 2.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 0 0-2.456 2.456Z"
              />
            </svg>
            AI Handoff Summary
          </p>
          <ul class="mt-2 space-y-1">
            <li
              v-for="(bullet, i) in summaryBullets"
              :key="i"
              class="text-sm leading-relaxed text-amber-900"
            >
              {{ bullet }}
            </li>
          </ul>
        </div>

        <!-- Transcript -->
        <div ref="feedRef" class="space-y-5 p-4">
          <ChatMessage
            v-for="m in store.activeMessages"
            :key="m.id"
            :message="{
              id: `agent-${m.id}`,
              role: senderRole(m.sender),
              content: m.content,
              timestamp: toTimestamp(m.timestamp),
              status: 'sent',
            }"
          />
          <p
            v-if="store.activeMessages.length === 0"
            class="p-6 text-center text-sm text-slate-400"
          >
            No messages in this conversation yet.
          </p>
        </div>
      </div>

      <!-- Internal notes + reply -->
      <div class="border-t border-slate-200 bg-white">
        <div class="px-4 pt-3">
          <p class="text-xs font-semibold text-slate-500">
            Internal notes
            <span class="font-normal text-slate-400">(hidden from customer)</span>
          </p>
          <ul v-if="store.activeNotes.length" class="mt-2 space-y-1.5">
            <li
              v-for="(note, i) in store.activeNotes"
              :key="i"
              class="rounded-lg bg-slate-50 px-3 py-2 text-xs leading-relaxed text-slate-600"
            >
              {{ note }}
            </li>
          </ul>
          <p v-else class="mt-2 text-xs text-slate-400">No notes yet.</p>
        </div>

        <div class="px-4 py-3">
          <!-- Mode toggle -->
          <div class="mb-2 flex items-center gap-2">
            <button
              type="button"
              @click="isNoteMode = false"
              class="rounded-lg px-3 py-1.5 text-xs font-medium transition"
              :class="!isNoteMode ? 'bg-emerald-100 text-emerald-700' : 'text-slate-500 hover:bg-slate-100'"
            >
              💬 Reply
            </button>
            <button
              type="button"
              @click="isNoteMode = true"
              class="rounded-lg px-3 py-1.5 text-xs font-medium transition"
              :class="isNoteMode ? 'bg-amber-100 text-amber-700' : 'text-slate-500 hover:bg-slate-100'"
            >
              📝 Internal Note
            </button>
          </div>

          <!-- Reply input -->
          <form v-if="!isNoteMode" class="flex items-end gap-2" @submit.prevent="sendReply">
            <textarea
              v-model="replyText"
              rows="1"
              maxlength="2000"
              placeholder="Reply to the customer…"
              class="max-h-32 min-w-0 flex-1 resize-none rounded-2xl border border-slate-300 bg-white px-4 py-3 text-sm leading-relaxed text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100"
              @keydown.enter.exact.prevent="sendReply"
            ></textarea>
            <button
              type="submit"
              :disabled="!replyText.trim() || store.activeLoading"
              class="flex size-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-emerald-500 to-teal-600 text-white shadow-md transition enabled:hover:shadow-lg enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
              aria-label="Send reply"
            >
              <svg
                fill="none"
                viewBox="0 0 24 24"
                stroke-width="2"
                stroke="currentColor"
                class="size-5 -rotate-45"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M6 12 3.269 3.125A59.769 59.769 0 0 1 21.485 12 59.768 59.768 0 0 1 3.27 20.875L5.999 12Zm0 0h7.5"
                />
              </svg>
            </button>
          </form>

          <!-- Internal note input -->
          <div v-else class="flex items-end gap-2">
            <textarea
              v-model="noteText"
              rows="1"
              maxlength="2000"
              placeholder="Add an internal note (hidden from customer)…"
              class="max-h-32 min-w-0 flex-1 resize-none rounded-2xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm leading-relaxed text-slate-900 shadow-sm outline-none transition placeholder:text-amber-400 focus:border-amber-400 focus:ring-2 focus:ring-amber-100"
              @keydown.enter.exact.prevent="saveNote"
            ></textarea>
            <button
              type="button"
              @click="saveNote"
              :disabled="!noteText.trim()"
              class="flex size-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-amber-500 to-orange-600 text-white shadow-md transition enabled:hover:shadow-lg enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
              aria-label="Save internal note"
            >
              <svg
                fill="none"
                viewBox="0 0 24 24"
                stroke-width="2"
                stroke="currentColor"
                class="size-5"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M16.5 3.75V16.5L12 14.25 7.5 16.5V3.75m9 0H7.5m9 0h1.5m-1.5 0H6M3.75 3.75h16.5M3.75 3.75v16.5a.75.75 0 00.75.75H7.5"
                />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- Empty state -->
    <div
      v-else
      class="flex flex-1 items-center justify-center p-6"
    >
      <div class="text-center">
        <div
          class="mx-auto flex size-14 items-center justify-center rounded-2xl bg-slate-200 text-2xl"
          aria-hidden="true"
        >
          🎧
        </div>
        <h3 class="mt-4 text-base font-semibold text-slate-700">
          Select a ticket
        </h3>
        <p class="mt-1 max-w-sm text-sm leading-relaxed text-slate-500">
          Pick a conversation from the list to review the AI handoff summary,
          take it over, and start replying.
        </p>
      </div>
    </div>
  </section>
</template>
