<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useAgentStore } from '../../stores/agent'
import { useWebSocket } from '../../composables/useWebSocket'
import AgentTicketList from './AgentTicketList.vue'
import KnowledgeBaseManager from '../admin/KnowledgeBaseManager.vue'
import ChatMessage from '../ChatMessage.vue'
import axios from 'axios'

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

const props = defineProps({
  embedded: { type: Boolean, default: false },
})
defineEmits(['switch-to-chat'])

const store = useAgentStore()

// ── WebSocket ──────────────────────────────────────────────────
const {
  isConnected: wsConnected,
  connect: wsConnect,
  disconnect: wsDisconnect,
  sendChatMessage,
  subscribeToSession,
  subscribeToAgentChannel,
  subscribe,
} = useWebSocket({ brokerUrl: '/ws-chat' })

// ── State ──────────────────────────────────────────────────────
const activeTab = ref('tickets')
const username = ref('')
const password = ref('')
const loginLoading = ref(false)
const loginError = ref('')

// Conversation state (mirrors store.activeTicket but with WS enhancements)
const messages = ref([])
const escalationSummary = ref('')
const inputText = ref('')
const isNoteMode = ref(false)
const feedRef = ref(null)
const isLoadingHistory = ref(false)

// Escalation queue
const escalationQueue = ref([])
let queueSubId = null

const SENDER_TO_ROLE = { USER: 'user', AI: 'assistant', AGENT: 'agent' }

// ── Helpers ────────────────────────────────────────────────────
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
  return new Date(value).toLocaleString([], { hour: '2-digit', minute: '2-digit' })
}

function scrollToBottom() {
  const el = feedRef.value
  if (el) el.scrollTop = el.scrollHeight
}

// ── Login ──────────────────────────────────────────────────────
async function handleLogin() {
  loginLoading.value = true
  loginError.value = ''
  try {
    await store.login(username.value, password.value)
    username.value = ''
    password.value = ''
  } catch (err) {
    loginError.value =
      err?.status === 401
        ? 'Invalid agent credentials.'
        : 'Could not reach the backend.'
  } finally {
    loginLoading.value = false
  }
}

// ── Tab persistence ────────────────────────────────────────────
try {
  if (localStorage.getItem('ai-support-chat:agentTab') === 'knowledge') {
    activeTab.value = 'knowledge'
  }
} catch {}

function setTab(tab) {
  activeTab.value = tab
  try { localStorage.setItem('ai-support-chat:agentTab', tab) } catch {}
}

// ── Conversation loading ───────────────────────────────────────
// Watch for ticket selection from the sidebar store
watch(
  () => store.activeTicket,
  async (ticket) => {
    if (!ticket) {
      messages.value = []
      escalationSummary.value = ''
      return
    }

    isLoadingHistory.value = true
    messages.value = []
    escalationSummary.value = ''

    try {
      const { data } = await axios.get(
        `${apiBase}/v1/chat/session/${ticket.sessionId}/messages`,
      )
      messages.value = Array.isArray(data) ? data : []
    } catch {
      messages.value = []
    } finally {
      isLoadingHistory.value = false
      await nextTick()
      scrollToBottom()
    }

    // Fetch escalation summary
    try {
      const { data: summary } = await axios.get(
        `${apiBase}/v1/chat/conversations/session/${ticket.sessionId}/summary`,
      )
      escalationSummary.value = summary || ''
    } catch {
      // summary endpoint may not exist yet
    }

    // Connect WebSocket and subscribe
    if (!wsConnected.value) wsConnect()

    subscribeToSession(ticket.sessionId, (msg) => {
      messages.value.push(msg)
    })
    subscribeToAgentChannel(ticket.sessionId, (msg) => {
      messages.value.push({ ...msg, internal: true })
    })
  },
  { immediate: true },
)

// ── Send message / note ────────────────────────────────────────
function handleSend() {
  const content = inputText.value.trim()
  if (!content || !store.activeTicket) return

  const payload = {
    sender: 'AGENT',
    content,
    internal: isNoteMode.value,
  }

  sendChatMessage(store.activeTicket.sessionId, payload)

  // Optimistic local add
  messages.value.push({
    id: Date.now(),
    sessionId: store.activeTicket.sessionId,
    sender: 'AGENT',
    content,
    timestamp: new Date().toISOString(),
    internal: isNoteMode.value,
  })

  inputText.value = ''
  isNoteMode.value = false
  nextTick(scrollToBottom)
}

// ── Escalation queue subscription ───────────────────────────────
onMounted(() => {
  if (store.authenticated) {
    wsConnect()
    queueSubId = subscribe('/topic/agent/queue', (ticket) => {
      escalationQueue.value.unshift(ticket)
    })
  }
})

onUnmounted(() => {
  wsDisconnect()
})

// Auto-scroll on new messages
watch(messages, () => { nextTick(scrollToBottom) }, { deep: true })
</script>

<template>
  <div class="flex h-full flex-col bg-slate-100 font-sans text-slate-900">
    <!-- Header -->
    <header v-if="!embedded" class="z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div class="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3">
        <div class="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-slate-700 to-slate-900 text-lg text-white shadow-md">🎧</div>
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-base font-semibold">Agent Workspace</h1>
          <p class="truncate text-xs text-slate-500">
            <template v-if="store.authenticated">
              Signed in as <span class="font-medium text-slate-700">{{ store.agentName }}</span>
              · {{ store.escalatedCount }} escalated
            </template>
            <template v-else>Human handoff queue</template>
          </p>
        </div>
        <span v-if="wsConnected" class="flex items-center gap-1 text-xs text-emerald-600">
          <span class="relative flex size-2"><span class="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-75"></span><span class="relative inline-flex size-2 rounded-full bg-emerald-500"></span></span>
          Live
        </span>
        <button v-if="store.authenticated" type="button" @click="store.logout()" class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 hover:text-red-600">Log out</button>
        <button type="button" @click="$emit('switch-to-chat')" class="flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 hover:text-indigo-600">← Customer chat</button>
      </div>
    </header>

    <!-- Login gate -->
    <div v-if="!embedded && !store.authenticated" class="flex flex-1 items-center justify-center p-4">
      <form class="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-6 shadow-sm" @submit.prevent="handleLogin">
        <h2 class="text-lg font-semibold text-slate-800">Agent sign in</h2>
        <p class="mt-1 text-sm leading-relaxed text-slate-500">Sign in to take over escalated conversations.</p>
        <label class="mt-4 block text-sm font-medium text-slate-700">
          Username
          <input v-model="username" type="text" autocomplete="username" required class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100" />
        </label>
        <label class="mt-3 block text-sm font-medium text-slate-700">
          Password
          <input v-model="password" type="password" autocomplete="current-password" required class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100" />
        </label>
        <p v-if="loginError" class="mt-3 text-sm text-red-600" role="alert">{{ loginError }}</p>
        <button type="submit" :disabled="loginLoading" class="mt-5 w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white transition enabled:hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50">
          {{ loginLoading ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
    </div>

    <!-- Workspace -->
    <template v-if="embedded || store.authenticated">
      <!-- Tabs -->
      <nav v-if="!embedded" class="flex shrink-0 items-center gap-1 border-b border-slate-200 bg-white px-4">
        <button type="button" @click="setTab('tickets')" class="relative px-3 py-2.5 text-sm font-medium transition" :class="activeTab === 'tickets' ? 'text-indigo-600' : 'text-slate-500 hover:text-slate-700'">
          🎧 Tickets
          <span v-if="activeTab === 'tickets'" class="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-indigo-600"></span>
        </button>
        <button type="button" @click="setTab('knowledge')" class="relative px-3 py-2.5 text-sm font-medium transition" :class="activeTab === 'knowledge' ? 'text-indigo-600' : 'text-slate-500 hover:text-slate-700'">
          📚 Knowledge Base
          <span v-if="activeTab === 'knowledge'" class="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-indigo-600"></span>
        </button>
      </nav>

      <!-- Tickets + Conversation split -->
      <div v-if="activeTab === 'tickets'" class="flex min-h-0 flex-1">
        <!-- Ticket list sidebar -->
        <AgentTicketList class="w-72 shrink-0 border-r border-slate-200 bg-white sm:w-80" />

        <!-- Conversation panel -->
        <section class="flex min-w-0 flex-1 flex-col">
          <template v-if="store.activeTicket">
            <!-- Ticket header -->
            <div class="border-b border-slate-200 bg-white px-4 py-3">
              <div class="flex items-center gap-2">
                <h2 class="min-w-0 flex-1 truncate text-sm font-semibold text-slate-800">{{ store.activeTicket.subject }}</h2>
                <span class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-600">#{{ store.activeTicket.id }}</span>
              </div>
              <p class="mt-1 text-xs text-slate-500">
                Customer: <span class="font-medium text-slate-700">{{ store.activeTicket.userEmail || 'Unknown' }}</span>
                · Session #{{ store.activeTicket.sessionId }}
              </p>
            </div>

            <!-- Scrollable feed -->
            <div ref="feedRef" class="min-h-0 flex-1 overflow-y-auto">
              <!-- AI Summary Banner -->
              <div v-if="escalationSummary" class="mx-4 mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4">
                <p class="flex items-center gap-1.5 text-xs font-semibold text-amber-700">
                  <svg fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" class="size-4"><path stroke-linecap="round" stroke-linejoin="round" d="M9.813 15.904 9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09ZM18.259 8.715 18 9.75l-.259-1.035a3.375 3.375 0 0 0-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 0 0 2.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 0 0 2.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 0 0-2.456 2.456Z" /></svg>
                  AI Handoff Summary
                </p>
                <p class="mt-2 text-sm leading-relaxed text-amber-900 whitespace-pre-wrap">{{ escalationSummary }}</p>
              </div>

              <!-- Messages -->
              <div class="space-y-5 p-4">
                <template v-for="m in messages" :key="m.id">
                  <!-- Internal note: distinct badge styling -->
                  <div v-if="m.internal" class="flex justify-end">
                    <div class="max-w-[80%] rounded-2xl border border-amber-200 bg-amber-50 px-4 py-2.5 text-sm leading-relaxed shadow-sm rounded-br-md">
                      <span class="mb-1 inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold text-amber-700">📝 Internal Note</span>
                      <p class="mt-1 whitespace-pre-wrap text-amber-900">{{ m.content }}</p>
                      <p class="mt-1 text-[10px] text-amber-400">{{ m.sender }} · {{ formatTime(m.timestamp) }}</p>
                    </div>
                  </div>
                  <!-- Regular message -->
                  <ChatMessage
                    v-else
                    :message="{ id: `ws-${m.id}`, role: senderRole(m.sender), content: m.content, timestamp: toTimestamp(m.timestamp), status: 'sent' }"
                  />
                </template>
                <p v-if="messages.length === 0 && !isLoadingHistory" class="p-6 text-center text-sm text-slate-400">No messages yet.</p>
                <p v-if="isLoadingHistory" class="p-6 text-center text-sm text-slate-400">Loading conversation…</p>
              </div>
            </div>

            <!-- Input bar with internal note toggle -->
            <div class="border-t border-slate-200 bg-white px-4 py-3">
              <!-- Toggle switch -->
              <div class="mb-2 flex items-center gap-2">
                <label class="relative inline-flex cursor-pointer items-center gap-2">
                  <input type="checkbox" v-model="isNoteMode" class="peer sr-only" />
                  <div class="h-5 w-9 rounded-full bg-slate-300 transition peer-checked:bg-amber-500 after:absolute after:left-[2px] after:top-[2px] after:h-4 after:w-4 after:rounded-full after:bg-white after:transition peer-checked:after:translate-x-4"></div>
                  <span class="text-xs font-medium" :class="isNoteMode ? 'text-amber-600' : 'text-slate-500'">
                    {{ isNoteMode ? '📝 Internal Note' : '💬 Send as Internal Note' }}
                  </span>
                </label>
              </div>
              <form class="flex items-end gap-2" @submit.prevent="handleSend">
                <textarea
                  v-model="inputText"
                  rows="1"
                  maxlength="2000"
                  :placeholder="isNoteMode ? 'Internal note (hidden from customer)…' : 'Reply to the customer…'"
                  class="max-h-32 min-w-0 flex-1 resize-none rounded-2xl border px-4 py-3 text-sm leading-relaxed text-slate-900 shadow-sm outline-none transition"
                  :class="isNoteMode
                    ? 'border-amber-300 bg-amber-50 placeholder:text-amber-400 focus:border-amber-400 focus:ring-2 focus:ring-amber-100'
                    : 'border-slate-300 bg-white placeholder:text-slate-400 focus:border-emerald-400 focus:ring-2 focus:ring-emerald-100'"
                  @keydown.enter.exact.prevent="handleSend"
                ></textarea>
                <button
                  type="submit"
                  :disabled="!inputText.trim()"
                  class="flex size-11 shrink-0 items-center justify-center rounded-full text-white shadow-md transition enabled:hover:shadow-lg enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
                  :class="isNoteMode
                    ? 'bg-gradient-to-br from-amber-500 to-orange-600'
                    : 'bg-gradient-to-br from-emerald-500 to-teal-600'"
                >
                  <svg fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="size-5 -rotate-45"><path stroke-linecap="round" stroke-linejoin="round" d="M6 12 3.269 3.125A59.769 59.769 0 0 1 21.485 12 59.768 59.768 0 0 1 3.27 20.875L5.999 12Zm0 0h7.5" /></svg>
                </button>
              </form>
            </div>
          </template>

          <!-- Empty state -->
          <div v-else class="flex flex-1 items-center justify-center p-6">
            <div class="text-center">
              <div class="mx-auto flex size-14 items-center justify-center rounded-2xl bg-slate-200 text-2xl">🎧</div>
              <h3 class="mt-4 text-base font-semibold text-slate-700">Select a ticket</h3>
              <p class="mt-1 max-w-sm text-sm leading-relaxed text-slate-500">Pick a conversation to review the AI summary, take it over, and start replying.</p>
            </div>
          </div>
        </section>
      </div>

      <KnowledgeBaseManager v-else class="flex min-h-0 flex-1" />
    </template>
  </div>
</template>
