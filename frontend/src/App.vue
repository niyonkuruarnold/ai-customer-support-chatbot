<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import ChatMessage from './components/ChatMessage.vue'
import SyncStatusBadge from './components/SyncStatusBadge.vue'
import TypingIndicator from './components/TypingIndicator.vue'
import AgentWorkspace from './components/agent/AgentWorkspace.vue'
import KnowledgeBaseAdmin from './components/admin/KnowledgeBaseAdmin.vue'
import TicketDashboard from './components/admin/TicketDashboard.vue'
import OwnerDashboard from './components/admin/OwnerDashboard.vue'
import MyReservations from './components/reservations/MyReservations.vue'
import { MAX_MESSAGE_LENGTH, useChatStore } from './stores/chat'
import { useAgentStore } from './stores/agent'
import { fetchSuggestedQuestions } from './api/chat'

const store = useChatStore()
const agentStore = useAgentStore()

// ── Role constants ─────────────────────────────────────────────────────
const ROLES = { CUSTOMER: 'CUSTOMER', AGENT: 'AGENT', ADMIN: 'ADMIN' }

// ── View mode: 'chat' | 'my-tickets' | 'agent' | 'knowledge' | 'tickets' | 'reservations' | 'owner'
const VALID_VIEWS = ['chat', 'my-tickets', 'agent', 'tickets', 'reservations', 'owner', 'knowledge']

/** Which views each role may access */
const ROLE_ALLOWED_VIEWS = {
  CUSTOMER: ['chat', 'my-tickets'],
  AGENT: ['chat', 'tickets', 'agent'],
  ADMIN: ['chat', 'tickets', 'knowledge', 'owner', 'agent', 'reservations'],
}

function allowedViewsFor(role) {
  return ROLE_ALLOWED_VIEWS[role] || ROLE_ALLOWED_VIEWS.CUSTOMER
}

function initialView() {
  const param = new URLSearchParams(window.location.search).get('mode')
  const role = agentStore.userRole || ROLES.CUSTOMER

  let raw
  if (VALID_VIEWS.includes(param)) {
    raw = param
  } else {
    const saved = localStorage.getItem('ai-support-chat:mode')
    raw = VALID_VIEWS.includes(saved) ? saved : 'chat'
  }

  // Role-based access control: redirect to chat if not allowed
  if (!allowedViewsFor(role).includes(raw)) return 'chat'
  return raw
}

const view = ref(initialView())

function setView(next) {
  const role = agentStore.userRole || ROLES.CUSTOMER
  // Role-based access control
  if (!allowedViewsFor(role).includes(next)) {
    next = 'chat'
  }
  view.value = next
  try {
    localStorage.setItem('ai-support-chat:mode', next)
  } catch { /* ignore */ }
  const url = new URL(window.location.href)
  if (next === 'chat') url.searchParams.delete('mode')
  else url.searchParams.set('mode', next)
  window.history.replaceState({}, '', url)
}

// ── Role helpers ───────────────────────────────────────────────────────
const isCustomer = computed(() => agentStore.userRole === ROLES.CUSTOMER)
const isStaff = computed(() => agentStore.userRole === ROLES.AGENT || agentStore.userRole === ROLES.ADMIN)

/** Dev role switcher — updates role and redirects if current view is not allowed */
function switchRole(newRole) {
  agentStore.setUserRole(newRole)
  // For staff roles, ensure we navigate to an allowed view
  if (!allowedViewsFor(newRole).includes(view.value)) {
    setView('chat')
  }
}

// ── Customer: Widget open/close + expand state ─────────────────────────
const isOpen = ref(
  // Auto-open for non-chat deep-links in customer mode
  !isStaff.value && view.value !== 'chat',
)
const expanded = ref(false)

function toggleExpand() {
  expanded.value = !expanded.value
}

function closeWidget() {
  isOpen.value = false
  expanded.value = false
}

// ── Staff: Auth gate ───────────────────────────────────────────────────
const staffUsername = ref('')
const staffPassword = ref('')
const staffLoginLoading = ref(false)
const staffLoginError = ref('')

async function handleStaffLogin() {
  staffLoginLoading.value = true
  staffLoginError.value = ''
  try {
    await agentStore.login(staffUsername.value, staffPassword.value)
    staffUsername.value = ''
    staffPassword.value = ''
  } catch (err) {
    staffLoginError.value =
      err?.status === 401
        ? 'Invalid credentials. Use the Spring Security user (default admin / admin123).'
        : 'Could not reach the backend. Is it running on port 8080?'
  } finally {
    staffLoginLoading.value = false
  }
}

function handleStaffLogout() {
  agentStore.logout()
  view.value = 'chat'
}

// ── New Conversation confirmation modal ──────────────────────────────
const showNewChatModal = ref(false)

function requestNewChat() {
  if (store.hasMessages || store.isEscalated) {
    showNewChatModal.value = true
  } else {
    // No conversation to close — just reset
    confirmNewChat()
  }
}

async function confirmNewChat() {
  showNewChatModal.value = false
  await store.closeAndResetConversation()
  input.value = ''
  resizeInput()
}

function cancelNewChat() {
  showNewChatModal.value = false
}

// ── Chat input state (customer widget + expanded portal) ────────────────
const input = ref('')
const feedRef = ref(null)
const inputRef = ref(null)

const confirmingClear = ref(false)
let clearConfirmTimer = null

const canSend = computed(() => {
  const text = input.value.trim()
  return text.length > 0 && text.length <= MAX_MESSAGE_LENGTH && !store.isLoading
})

// Hardcoded fallback prompts used when the backend is unreachable
const FALLBACK_PROMPTS = [
  'How do I track my order?',
  'What are your support hours?',
  'I need help with a refund',
  'Talk to a human agent',
]

const suggestedPrompts = ref(FALLBACK_PROMPTS)

// Fetch dynamic suggestions from the vector store
async function refreshSuggestions() {
  const questions = await fetchSuggestedQuestions(FALLBACK_PROMPTS)
  suggestedPrompts.value = questions
}

// Fetch on initial load
refreshSuggestions()

// Re-fetch when switching to chat view (in case new docs were uploaded)
watch(view, (newView) => {
  if (newView === 'chat') {
    refreshSuggestions()
  }
})

// Restore prior messages (backend history, else localStorage) on load
store.loadHistory()

async function handleSubmit() {
  const sent = await store.sendMessage(input.value)
  if (sent) {
    input.value = ''
    resizeInput()
  }
}

function handleRetry(id) {
  store.retryMessage(id)
}

function onClearClick() {
  if (!confirmingClear.value) {
    confirmingClear.value = true
    clearConfirmTimer = setTimeout(() => {
      confirmingClear.value = false
    }, 3000)
  } else {
    clearTimeout(clearConfirmTimer)
    clearConfirmTimer = null
    confirmingClear.value = false
    store.clearConversation()
  }
}

function usePrompt(prompt) {
  input.value = prompt
  resizeInput()
  inputRef.value?.focus()
}

// Auto-scroll the feed to the newest message
watch(
  [() => store.messages, () => store.isLoading],
  async () => {
    await nextTick()
    const el = feedRef.value
    if (!el) return
    if (typeof el.scrollTo === 'function') {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
    } else {
      el.scrollTop = el.scrollHeight
    }
  },
)

// Grow the textarea up to a max height as content is typed
function resizeInput() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 160)}px`
}

onMounted(() => store.startPolling())

onBeforeUnmount(() => {
  clearTimeout(clearConfirmTimer)
  store.stopPolling()
})

// ── Navigation tabs (filtered by role) ─────────────────────────────────
const ROLE_NAV_ITEMS = {
  CUSTOMER: [
    { key: 'chat', label: 'Customer Chat', icon: '💬' },
    { key: 'my-tickets', label: 'My Support Tickets', icon: '🎫' },
  ],
  AGENT: [
    { key: 'agent', label: 'Live Customer Workspace', icon: '🎧' },
    { key: 'tickets', label: 'Ticket Queue', icon: '🎫' },
  ],
  ADMIN: [
    { key: 'agent', label: 'Live Customer Workspace', icon: '🎧' },
    { key: 'tickets', label: 'Ticket Queue', icon: '🎫' },
    { key: 'knowledge', label: 'Knowledge Base Admin', icon: '📚' },
    { key: 'owner', label: 'System Indexer', icon: '🔧' },
  ],
}

const navItems = computed(() => {
  return ROLE_NAV_ITEMS[agentStore.userRole || ROLES.CUSTOMER]
})

/** Human-readable role label for the badge */
const roleLabel = computed(() => {
  if (agentStore.isAdmin) return 'Admin'
  if (agentStore.isAgent) return 'Agent'
  return 'Customer'
})
const roleBadgeClass = computed(() => {
  if (agentStore.isAdmin) return 'bg-violet-100 text-violet-700'
  if (agentStore.isAgent) return 'bg-sky-100 text-sky-700'
  return 'bg-slate-100 text-slate-600'
})
const roleIcon = computed(() => {
  if (agentStore.isAdmin) return '🔑'
  if (agentStore.isAgent) return '🎧'
  return '👤'
})

// ── Staff role label for auth form ─────────────────────────────────────
const staffRoleLabel = computed(() => {
  if (agentStore.userRole === ROLES.ADMIN) return 'Admin'
  return 'Agent'
})
</script>

<template>
  <!-- ═══════════════════════════════════════════════════════════════════
       CUSTOMER MODE: Floating AI Concierge Widget
       ═══════════════════════════════════════════════════════════════════ -->
  <template v-if="isCustomer">
    <!-- ── Floating Action Button (FAB) ─────────────────────────────── -->
    <button
      v-if="!isOpen"
      type="button"
      data-test="chat-fab"
      @click="isOpen = true"
      class="fixed bottom-6 right-6 z-50 flex size-14 items-center justify-center rounded-full bg-red-600 text-white shadow-xl transition hover:bg-red-700 active:scale-95"
      aria-label="Open CODAFRIQA Smart Assistant"
    >
      <svg
        fill="none"
        viewBox="0 0 24 24"
        stroke-width="1.8"
        stroke="currentColor"
        class="size-6"
        aria-hidden="true"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          d="M8.625 12a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H8.25m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H12m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 0 1-2.555-.337A5.972 5.972 0 0 1 5.41 20.97a5.969 5.969 0 0 1-.474-.065 4.48 4.48 0 0 0 .978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25Z"
        />
      </svg>
      <!-- Unread pulse while a response is generating -->
      <span
        v-if="store.isLoading"
        class="absolute -top-1 -right-1 flex size-4 items-center justify-center rounded-full bg-amber-400"
        aria-hidden="true"
      >
        <span class="size-2 animate-ping rounded-full bg-amber-400"></span>
      </span>
    </button>

    <!-- ── Compact Chat Widget (popup, not expanded) ─────────────────── -->
    <section
      v-if="isOpen && !expanded"
      data-test="chat-widget"
      class="fixed bottom-24 right-6 z-50 flex h-[580px] w-[400px] max-w-[calc(100vw-3rem)] flex-col overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-2xl"
      role="dialog"
      aria-label="CODAFRIQA Smart Assistant"
    >
      <!-- Header -->
      <header class="rounded-t-3xl bg-red-900 p-5 text-white">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-[10px] font-bold uppercase tracking-widest text-red-300">
              AI CONCIERGE
            </p>
            <h1 class="mt-1 text-lg font-bold leading-tight">
              CODAFRIQA Smart Assistant
            </h1>
            <p class="mt-0.5 flex items-center gap-1.5 text-xs text-red-200">
              <span class="relative flex size-1.5" aria-hidden="true">
                <span class="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-75"></span>
                <span class="relative inline-flex size-1.5 rounded-full bg-emerald-400"></span>
              </span>
              {{ store.isEscalated ? 'Agent Active' : 'Online' }}
            </p>
          </div>
          <div class="flex items-center gap-1">
            <!-- New Conversation button -->
            <button
              v-if="store.hasMessages || store.isEscalated"
              type="button"
              data-test="new-conversation"
              @click="requestNewChat"
              class="flex items-center gap-1 rounded-full bg-white/15 px-2 py-1 text-[10px] font-medium text-white/80 transition hover:bg-white/25 hover:text-white"
              aria-label="Start a new conversation"
            >
              <svg fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="size-3" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182" />
              </svg>
              New
            </button>
            <!-- Expand button -->
            <button
              type="button"
              data-test="widget-expand"
              @click="toggleExpand"
              class="flex size-8 shrink-0 items-center justify-center rounded-full text-white/60 transition hover:bg-white/10 hover:text-white"
              aria-label="Expand to full screen"
            >
              <svg fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="size-4" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9M3.75 20.25v-4.5m0 4.5h4.5m-4.5 0L9 15M20.25 3.75h-4.5m4.5 0v4.5m0-4.5L15 9m5.25 11.25h-4.5m4.5 0v-4.5m0 4.5L15 15" />
              </svg>
            </button>
            <!-- Close button -->
            <button
              type="button"
              data-test="chat-widget-close"
              @click="closeWidget"
              class="flex size-8 shrink-0 items-center justify-center rounded-full text-white/60 transition hover:bg-white/10 hover:text-white"
              aria-label="Close chat"
            >
              <svg fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="size-4" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      </header>

      <!-- Compact Navigation Tabs -->
      <div class="flex items-center gap-2 mx-4 my-3 rounded-2xl bg-slate-100 p-2">
        <button
          v-for="item in navItems"
          :key="item.key"
          type="button"
          @click="setView(item.key)"
          class="flex-1 rounded-xl py-2 px-4 text-xs font-medium transition"
          :class="view === item.key
            ? 'bg-red-600 text-white shadow-sm'
            : 'text-slate-500 hover:text-slate-700'
          "
        >
          {{ item.label }}
        </button>
      </div>

      <!-- Agent Active banner -->
      <div
        v-if="store.isEscalated"
        class="mx-4 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2"
      >
        <div class="flex items-center gap-2">
          <span class="relative flex size-1.5" aria-hidden="true">
            <span class="absolute inline-flex size-full animate-ping rounded-full bg-amber-400 opacity-75"></span>
            <span class="relative inline-flex size-1.5 rounded-full bg-amber-500"></span>
          </span>
          <p class="text-[11px] font-medium text-amber-800">
            🎧 Agent Active — a human agent is now handling this chat.
          </p>
        </div>
      </div>

      <!-- Message Feed -->
      <main ref="feedRef" class="flex-1 overflow-y-auto overscroll-contain bg-slate-50">
        <div class="space-y-4 px-4 py-4">
          <!-- Empty state -->
          <div
            v-if="!store.hasMessages"
            class="flex flex-col items-center px-2 pt-8 pb-4 text-center"
          >
            <div
              class="flex size-12 items-center justify-center rounded-2xl bg-red-100 text-2xl"
              aria-hidden="true"
            >
              🤖
            </div>
            <h2 class="mt-4 text-sm font-semibold text-slate-800">
              How can we help you today?
            </h2>
            <p class="mt-1.5 max-w-xs text-xs leading-relaxed text-slate-500">
              Ask about orders, refunds, shipping, or request a quote or meeting.
            </p>
            <div class="mt-4 flex flex-wrap justify-center gap-1.5">
              <button
                v-for="prompt in suggestedPrompts"
                :key="prompt"
                type="button"
                @click="usePrompt(prompt)"
                class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 shadow-sm transition hover:border-red-300 hover:text-red-600"
              >
                {{ prompt }}
              </button>
            </div>
          </div>

          <!-- Conversation -->
          <div v-else class="space-y-4">
            <ChatMessage
              v-for="message in store.messages"
              :key="message.id"
              :message="message"
              @retry="handleRetry"
            />
            <TypingIndicator v-if="store.isLoading" />
          </div>
        </div>
      </main>

      <!-- Input bar (Chat tab only) -->
      <footer v-if="view === 'chat'" class="shrink-0 border-t border-slate-200 bg-white">
        <form class="flex items-end gap-2 p-3" @submit.prevent="handleSubmit">
          <div class="relative flex-1">
            <textarea
              ref="inputRef"
              v-model="input"
              rows="1"
              :maxlength="MAX_MESSAGE_LENGTH"
              :placeholder="store.isEscalated ? 'Message the agent…' : 'Type your message…'"
              class="max-h-28 w-full resize-none rounded-2xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm leading-relaxed text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-red-400 focus:ring-2 focus:ring-red-100"
              @input="resizeInput"
              @keydown.enter.exact.prevent="handleSubmit"
            ></textarea>
            <span
              v-if="input.length > 0"
              class="absolute right-3 bottom-2 text-[10px] tabular-nums text-slate-400"
            >
              {{ input.length }}/{{ MAX_MESSAGE_LENGTH }}
            </span>
          </div>
          <button
            type="submit"
            :disabled="!canSend"
            class="flex size-10 shrink-0 items-center justify-center rounded-full bg-red-600 text-white shadow-md transition enabled:hover:bg-red-700 enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
            :aria-label="store.isLoading ? 'Waiting for response' : 'Send message'"
          >
            <svg
              v-if="!store.isLoading"
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
            <svg
              v-else
              class="size-5 animate-spin"
              viewBox="0 0 24 24"
              fill="none"
              aria-hidden="true"
            >
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          </button>
        </form>
        <p class="pb-2 text-center text-[10px] text-slate-400">
          Powered by Google Gemini · CODAFRIQA
        </p>
      </footer>

      <!-- My Tickets tab content -->
      <div
        v-else-if="view === 'my-tickets'"
        class="flex flex-1 flex-col items-center justify-center bg-slate-50 px-4 text-center"
      >
        <div class="flex size-12 items-center justify-center rounded-2xl bg-slate-200 text-2xl" aria-hidden="true">
          🎫
        </div>
        <h2 class="mt-4 text-sm font-semibold text-slate-800">My Support Tickets</h2>
        <p class="mt-1.5 max-w-xs text-xs leading-relaxed text-slate-500">
          Your support history appears here. Start a chat to create a new ticket, or ask about an existing one.
        </p>
        <button
          type="button"
          @click="setView('chat')"
          class="mt-4 rounded-full bg-red-600 px-4 py-2 text-xs font-medium text-white shadow-sm transition hover:bg-red-700"
        >
          Start a conversation
        </button>
      </div>
    </section>

    <!-- ── Full-Screen Expanded Portal ───────────────────────────────── -->
    <div
      v-if="isOpen && expanded"
      data-test="portal-overlay"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-sm sm:p-8"
      @keydown.escape="closeWidget"
    >
      <div
        class="relative flex h-full w-full max-w-7xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
        role="dialog"
        aria-label="CODAFRIQA Smart Assistant"
      >
        <!-- ── Portal Header ─────────────────────────────────────── -->
        <header
          class="z-10 flex shrink-0 flex-wrap items-center gap-4 border-b border-slate-200 bg-white/95 px-5 py-3 backdrop-blur sm:px-6"
        >
          <!-- Brand -->
          <div class="flex items-center gap-3">
            <div
              class="flex size-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-red-600 to-red-800 text-white shadow-sm"
              aria-hidden="true"
            >
              <svg fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" class="size-5">
                <path stroke-linecap="round" stroke-linejoin="round" d="M8.625 12a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H8.25m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H12m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 0 1-2.555-.337A5.972 5.972 0 0 1 5.41 20.97a5.969 5.969 0 0 1-.474-.065 4.48 4.48 0 0 0 .978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25Z" />
              </svg>
            </div>
            <div class="hidden min-w-0 sm:block">
              <h1 class="truncate text-sm font-bold text-slate-900">
                CODAFRIQA Smart Assistant
              </h1>
              <p class="flex items-center gap-1.5 text-[11px]">
                <span class="relative flex size-1.5" aria-hidden="true">
                  <span
                    class="absolute inline-flex size-full animate-ping rounded-full opacity-75"
                    :class="store.isEscalated ? 'bg-amber-400' : 'bg-emerald-400'"
                  ></span>
                  <span
                    class="relative inline-flex size-1.5 rounded-full"
                    :class="store.isEscalated ? 'bg-amber-500' : 'bg-emerald-500'"
                  ></span>
                </span>
                <span :class="store.isEscalated ? 'text-amber-700' : 'text-slate-500'">
                  {{ store.isEscalated ? 'Agent Active · AI paused' : 'Online · replies instantly' }}
                </span>
              </p>
            </div>
          </div>

          <!-- Navigation tabs -->
          <nav class="flex flex-wrap gap-1">
            <button
              v-for="item in navItems"
              :key="item.key"
              type="button"
              @click="setView(item.key)"
              class="rounded-full px-3 py-1.5 text-xs font-medium transition"
              :class="
                view === item.key
                  ? 'bg-red-600 text-white shadow-sm'
                  : 'text-slate-500 hover:bg-slate-100 hover:text-slate-700'
              "
            >
              <span class="mr-1 hidden lg:inline" aria-hidden="true">{{ item.icon }}</span>
              {{ item.label }}
            </button>
          </nav>

          <!-- Spacer + role badge + dev role switcher + controls -->
          <div class="ml-auto flex items-center gap-2">
            <SyncStatusBadge v-if="view === 'chat'" />
            <span
              class="hidden rounded-full px-2.5 py-1 text-[11px] font-medium sm:inline-block"
              :class="roleBadgeClass"
            >
              {{ roleIcon }} {{ roleLabel }}
            </span>
            <!-- Dev-only role switcher -->
            <select
              data-test="role-switcher"
              :value="agentStore.userRole || 'CUSTOMER'"
              @change="switchRole($event.target.value)"
              class="rounded-full border border-slate-200 bg-white px-2 py-1 text-[11px] font-medium text-slate-600 outline-none transition hover:border-slate-300"
              aria-label="Switch role (development)"
            >
              <option value="CUSTOMER">👤 Customer</option>
              <option value="AGENT">🎧 Agent</option>
              <option value="ADMIN">🔑 Admin</option>
            </select>
            <!-- New Conversation button -->
            <button
              v-if="view === 'chat' && (store.hasMessages || store.isEscalated)"
              type="button"
              data-test="new-conversation"
              @click="requestNewChat"
              class="flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:border-red-300 hover:bg-red-50 hover:text-red-600"
              aria-label="Start a new conversation"
            >
              <svg fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="size-3.5" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182" />
              </svg>
              New Chat
            </button>
            <!-- Collapse back to compact widget -->
            <button
              type="button"
              data-test="widget-collapse"
              @click="expanded = false"
              class="flex size-8 shrink-0 items-center justify-center rounded-full text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
              aria-label="Collapse to widget"
            >
              <svg fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="size-5" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 1 0-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 0 0 2.25-2.25v-6.75a2.25 2.25 0 0 0-2.25-2.25H6.75a2.25 2.25 0 0 0-2.25 2.25v6.75a2.25 2.25 0 0 0 2.25 2.25Z" />
              </svg>
            </button>
            <!-- Close button -->
            <button
              type="button"
              data-test="portal-close"
              @click="closeWidget"
              class="flex size-8 shrink-0 items-center justify-center rounded-full text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
              aria-label="Close portal"
            >
              <svg fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="size-5" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </header>

        <!-- ── Portal Content ─────────────────────────────────────── -->
        <main class="min-h-0 flex-1 overflow-hidden">
          <!-- ── Agent Workspace ────────────────────────────────── -->
          <AgentWorkspace
            v-if="view === 'agent'"
            embedded
            class="h-full"
          />

          <!-- ── Knowledge Base Admin ───────────────────────────── -->
          <KnowledgeBaseAdmin
            v-else-if="view === 'knowledge'"
            embedded
            class="h-full"
          />

          <!-- ── Ticket Dashboard ───────────────────────────────── -->
          <TicketDashboard
            v-else-if="view === 'tickets'"
            embedded
            class="h-full"
          />

          <!-- ── Owner Dashboard ────────────────────────────────── -->
          <OwnerDashboard
            v-else-if="view === 'owner'"
            embedded
            class="h-full"
          />

          <!-- ── Reservations ───────────────────────────────────── -->
          <MyReservations
            v-else-if="view === 'reservations'"
            embedded
            class="h-full"
          />

          <!-- ── Chat View (default) ────────────────────────────── -->
          <div
            v-else
            class="flex h-full flex-col bg-slate-100"
          >
            <!-- Agent Active banner -->
            <div
              v-if="store.isEscalated"
              class="border-b border-amber-200 bg-amber-50"
            >
              <div class="mx-auto flex max-w-3xl items-center justify-between gap-2 px-4 py-2">
                <div class="flex items-center gap-2">
                  <span class="relative flex size-2" aria-hidden="true">
                    <span class="absolute inline-flex size-full animate-ping rounded-full bg-amber-400 opacity-75"></span>
                    <span class="relative inline-flex size-2 rounded-full bg-amber-500"></span>
                  </span>
                  <p class="text-xs font-medium text-amber-800">
                    🎧 Agent Active — the AI assistant is paused and a human agent is
                    now handling this chat. They can see our conversation and will
                    reply right here.
                  </p>
                </div>
                <button
                  type="button"
                  data-test="new-conversation"
                  @click="requestNewChat"
                  class="shrink-0 rounded-full border border-amber-300 bg-white px-3 py-1 text-[11px] font-medium text-amber-700 transition hover:bg-amber-100"
                >
                  + New Chat
                </button>
              </div>
            </div>

            <!-- Message feed -->
            <main ref="feedRef" class="flex-1 overflow-y-auto overscroll-contain">
              <div class="mx-auto max-w-3xl px-4 py-6">
                <!-- Empty state -->
                <div
                  v-if="!store.hasMessages"
                  class="flex flex-col items-center px-4 pt-16 pb-8 text-center"
                >
                  <div
                    class="flex size-16 items-center justify-center rounded-2xl bg-gradient-to-br from-red-500 to-red-700 text-3xl shadow-lg"
                    aria-hidden="true"
                  >
                    🤖
                  </div>
                  <h2 class="mt-6 text-xl font-semibold text-slate-800">
                    How can we help you today?
                  </h2>
                  <p class="mt-2 max-w-md text-sm leading-relaxed text-slate-500">
                    Ask about your orders, refunds, shipping, or anything else.
                    Our AI assistant replies instantly — type a message below or
                    pick a quick question.
                  </p>
                  <div class="mt-6 flex flex-wrap justify-center gap-2">
                    <button
                      v-for="prompt in suggestedPrompts"
                      :key="prompt"
                      type="button"
                      @click="usePrompt(prompt)"
                      class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-700 shadow-sm transition hover:border-red-300 hover:text-red-600"
                    >
                      {{ prompt }}
                    </button>
                  </div>
                </div>

                <!-- Conversation -->
                <div v-else class="space-y-5">
                  <ChatMessage
                    v-for="message in store.messages"
                    :key="message.id"
                    :message="message"
                    @retry="handleRetry"
                  />
                  <TypingIndicator v-if="store.isLoading" />
                </div>
              </div>
            </main>

            <!-- Input bar -->
            <footer class="shrink-0 border-t border-slate-200 bg-white">
              <div class="mx-auto max-w-3xl px-4 py-3">
                <form class="flex items-end gap-2" @submit.prevent="handleSubmit">
                  <div class="relative flex-1">
                    <textarea
                      ref="inputRef"
                      v-model="input"
                      rows="1"
                      :maxlength="MAX_MESSAGE_LENGTH"
                      :placeholder="store.isEscalated ? 'Message the agent… (Shift+Enter for a new line)' : 'Type your message… (Shift+Enter for a new line)'"
                      class="max-h-40 w-full resize-none rounded-2xl border border-slate-300 bg-white px-4 py-3 pr-16 text-sm leading-relaxed text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-red-400 focus:ring-2 focus:ring-red-100"
                      @input="resizeInput"
                      @keydown.enter.exact.prevent="handleSubmit"
                    ></textarea>
                    <span
                      v-if="input.length > 0"
                      class="absolute right-3 bottom-2.5 text-[10px] tabular-nums text-slate-400"
                    >
                      {{ input.length }}/{{ MAX_MESSAGE_LENGTH }}
                    </span>
                  </div>
                  <button
                    type="submit"
                    :disabled="!canSend"
                    class="flex size-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-red-600 to-red-700 text-white shadow-md transition enabled:hover:shadow-lg enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
                    :aria-label="store.isLoading ? 'Waiting for response' : 'Send message'"
                  >
                    <svg
                      v-if="!store.isLoading"
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
                    <svg
                      v-else
                      class="size-5 animate-spin"
                      viewBox="0 0 24 24"
                      fill="none"
                      aria-hidden="true"
                    >
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                  </button>
                </form>
                <p class="mt-1.5 text-center text-[11px] text-slate-400">
                  Powered by Google Gemini · Responses are generated by the Spring Boot
                  backend
                </p>
              </div>
            </footer>
          </div>
        </main>
      </div>
    </div>
  </template>

  <!-- ═══════════════════════════════════════════════════════════════════
       AGENT / ADMIN MODE: Full-Screen Professional Dashboard
       ═══════════════════════════════════════════════════════════════════ -->
  <div
    v-else
    class="flex h-dvh flex-col bg-slate-100 font-sans text-slate-900"
  >
    <!-- ── Top Navigation Bar ──────────────────────────────────────── -->
    <header class="z-10 shrink-0 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div class="mx-auto flex max-w-7xl flex-wrap items-center gap-4 px-5 py-3 sm:px-6">
        <!-- Brand -->
        <div class="flex items-center gap-3">
          <div
            class="flex size-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-red-600 to-red-800 text-white shadow-sm"
            aria-hidden="true"
          >
            <svg fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" class="size-5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M8.625 12a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H8.25m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H12m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 0 1-2.555-.337A5.972 5.972 0 0 1 5.41 20.97a5.969 5.969 0 0 1-.474-.065 4.48 4.48 0 0 0 .978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25Z" />
            </svg>
          </div>
          <div class="hidden min-w-0 sm:block">
            <h1 class="truncate text-sm font-bold text-slate-900">
              CODAFRIQA Smart Assistant
            </h1>
            <p class="text-[11px] text-slate-500">
              {{ roleLabel }} workspace
            </p>
          </div>
        </div>

        <!-- Navigation tabs -->
        <nav class="flex flex-wrap gap-1">
          <button
            v-for="item in navItems"
            :key="item.key"
            type="button"
            @click="setView(item.key)"
            class="rounded-full px-3 py-1.5 text-xs font-medium transition"
            :class="
              view === item.key
                ? 'bg-red-600 text-white shadow-sm'
                : 'text-slate-500 hover:bg-slate-100 hover:text-slate-700'
            "
          >
            <span class="mr-1 hidden lg:inline" aria-hidden="true">{{ item.icon }}</span>
            {{ item.label }}
          </button>
        </nav>

        <!-- Spacer + role badge + dev role switcher + controls -->
        <div class="ml-auto flex items-center gap-2">
          <span
            class="hidden rounded-full px-2.5 py-1 text-[11px] font-medium sm:inline-block"
            :class="roleBadgeClass"
          >
            {{ roleIcon }} {{ roleLabel }}
          </span>
          <!-- Dev-only role switcher -->
          <select
            data-test="role-switcher"
            :value="agentStore.userRole || 'CUSTOMER'"
            @change="switchRole($event.target.value)"
            class="rounded-full border border-slate-200 bg-white px-2 py-1 text-[11px] font-medium text-slate-600 outline-none transition hover:border-slate-300"
            aria-label="Switch role (development)"
          >
            <option value="CUSTOMER">👤 Customer</option>
            <option value="AGENT">🎧 Agent</option>
            <option value="ADMIN">🔑 Admin</option>
          </select>
          <!-- Logout (only when authenticated) -->
          <button
            v-if="agentStore.authenticated"
            type="button"
            @click="handleStaffLogout"
            class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 hover:text-red-600"
          >
            Log out
          </button>
        </div>
      </div>
    </header>

    <!-- ── Auth Gate (inline modal for unauthenticated staff) ──────── -->
    <div
      v-if="!agentStore.authenticated"
      class="flex flex-1 items-center justify-center p-4"
    >
      <form
        data-test="staff-auth-form"
        class="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
        @submit.prevent="handleStaffLogin"
      >
        <div class="flex items-center gap-3">
          <div
            class="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-slate-700 to-slate-900 text-lg text-white shadow-md"
            aria-hidden="true"
          >
            {{ agentStore.userRole === ROLES.ADMIN ? '🔑' : '🎧' }}
          </div>
          <div>
            <h2 class="text-lg font-semibold text-slate-800">{{ staffRoleLabel }} Sign In</h2>
            <p class="text-xs text-slate-500">Staff credentials required</p>
          </div>
        </div>
        <p class="mt-3 text-sm leading-relaxed text-slate-500">
          Sign in to access the {{ staffRoleLabel.toLowerCase() }} workspace.
          Uses the Spring Security HTTP Basic credentials configured on the backend.
        </p>
        <label class="mt-4 block text-sm font-medium text-slate-700">
          Username
          <input
            v-model="staffUsername"
            type="text"
            autocomplete="username"
            required
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-red-400 focus:ring-2 focus:ring-red-100"
          />
        </label>
        <label class="mt-3 block text-sm font-medium text-slate-700">
          Password
          <input
            v-model="staffPassword"
            type="password"
            autocomplete="current-password"
            required
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-red-400 focus:ring-2 focus:ring-red-100"
          />
        </label>
        <p v-if="staffLoginError" class="mt-3 text-sm text-red-600" role="alert">
          {{ staffLoginError }}
        </p>
        <button
          type="submit"
          :disabled="staffLoginLoading"
          class="mt-5 w-full rounded-lg bg-red-600 py-2.5 text-sm font-semibold text-white transition enabled:hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {{ staffLoginLoading ? 'Signing in…' : 'Sign in' }}
        </button>
        <p class="mt-3 text-center text-xs text-slate-400">
          Default: admin / admin123
        </p>
      </form>
    </div>

    <!-- ── Dashboard Content (authenticated staff only) ────────────── -->
    <main
      v-else
      class="min-h-0 flex-1 overflow-hidden"
    >
      <!-- ── Live Customer Workspace ─────────────────────────────── -->
      <AgentWorkspace
        v-if="view === 'agent'"
        embedded
        class="h-full"
      />

      <!-- ── Ticket Queue ────────────────────────────────────────── -->
      <TicketDashboard
        v-else-if="view === 'tickets'"
        embedded
        class="h-full"
      />

      <!-- ── Knowledge Base Admin ────────────────────────────────── -->
      <KnowledgeBaseAdmin
        v-else-if="view === 'knowledge'"
        embedded
        class="h-full"
      />

      <!-- ── System Indexer ──────────────────────────────────────── -->
      <OwnerDashboard
        v-else-if="view === 'owner'"
        embedded
        class="h-full"
      />

      <!-- ── Reservations ────────────────────────────────────────── -->
      <MyReservations
        v-else-if="view === 'reservations'"
        embedded
        class="h-full"
      />

      <!-- ── Fallback: Customer Chat ─────────────────────────────── -->
      <div
        v-else
        class="flex h-full flex-col bg-slate-100"
      >
        <!-- Agent Active banner -->
        <div
          v-if="store.isEscalated"
          class="border-b border-amber-200 bg-amber-50"
        >
          <div class="mx-auto flex max-w-3xl items-center justify-between gap-2 px-4 py-2">
            <div class="flex items-center gap-2">
              <span class="relative flex size-2" aria-hidden="true">
                <span class="absolute inline-flex size-full animate-ping rounded-full bg-amber-400 opacity-75"></span>
                <span class="relative inline-flex size-2 rounded-full bg-amber-500"></span>
              </span>
              <p class="text-xs font-medium text-amber-800">
                🎧 Agent Active — the AI assistant is paused and a human agent is
                now handling this chat.
              </p>
            </div>
            <button
              type="button"
              data-test="new-conversation"
              @click="requestNewChat"
              class="shrink-0 rounded-full border border-amber-300 bg-white px-3 py-1 text-[11px] font-medium text-amber-700 transition hover:bg-amber-100"
            >
              + New Chat
            </button>
          </div>
        </div>

        <!-- Message feed -->
        <main ref="feedRef" class="flex-1 overflow-y-auto overscroll-contain">
          <div class="mx-auto max-w-3xl px-4 py-6">
            <!-- Empty state -->
            <div
              v-if="!store.hasMessages"
              class="flex flex-col items-center px-4 pt-16 pb-8 text-center"
            >
              <div
                class="flex size-16 items-center justify-center rounded-2xl bg-gradient-to-br from-red-500 to-red-700 text-3xl shadow-lg"
                aria-hidden="true"
              >
                🤖
              </div>
              <h2 class="mt-6 text-xl font-semibold text-slate-800">
                How can we help you today?
              </h2>
              <p class="mt-2 max-w-md text-sm leading-relaxed text-slate-500">
                Ask about your orders, refunds, shipping, or anything else.
              </p>
              <div class="mt-6 flex flex-wrap justify-center gap-2">
                <button
                  v-for="prompt in suggestedPrompts"
                  :key="prompt"
                  type="button"
                  @click="usePrompt(prompt)"
                  class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-700 shadow-sm transition hover:border-red-300 hover:text-red-600"
                >
                  {{ prompt }}
                </button>
              </div>
            </div>

            <!-- Conversation -->
            <div v-else class="space-y-5">
              <ChatMessage
                v-for="message in store.messages"
                :key="message.id"
                :message="message"
                @retry="handleRetry"
              />
              <TypingIndicator v-if="store.isLoading" />
            </div>
          </div>
        </main>

        <!-- Input bar -->
        <footer class="shrink-0 border-t border-slate-200 bg-white">
          <div class="mx-auto max-w-3xl px-4 py-3">
            <form class="flex items-end gap-2" @submit.prevent="handleSubmit">
              <div class="relative flex-1">
                <textarea
                  ref="inputRef"
                  v-model="input"
                  rows="1"
                  :maxlength="MAX_MESSAGE_LENGTH"
                  :placeholder="store.isEscalated ? 'Message the agent…' : 'Type your message…'"
                  class="max-h-40 w-full resize-none rounded-2xl border border-slate-300 bg-white px-4 py-3 pr-16 text-sm leading-relaxed text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-red-400 focus:ring-2 focus:ring-red-100"
                  @input="resizeInput"
                  @keydown.enter.exact.prevent="handleSubmit"
                ></textarea>
                <span
                  v-if="input.length > 0"
                  class="absolute right-3 bottom-2.5 text-[10px] tabular-nums text-slate-400"
                >
                  {{ input.length }}/{{ MAX_MESSAGE_LENGTH }}
                </span>
              </div>
              <button
                type="submit"
                :disabled="!canSend"
                class="flex size-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-red-600 to-red-700 text-white shadow-md transition enabled:hover:shadow-lg enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
                :aria-label="store.isLoading ? 'Waiting for response' : 'Send message'"
              >
                <svg
                  v-if="!store.isLoading"
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
                <svg
                  v-else
                  class="size-5 animate-spin"
                  viewBox="0 0 24 24"
                  fill="none"
                  aria-hidden="true"
                >
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              </button>
            </form>
            <p class="mt-1.5 text-center text-[11px] text-slate-400">
              Powered by Google Gemini · CODAFRIQA
            </p>
          </div>
        </footer>
      </div>
    </main>
  </div>

  <!-- ═══════════════════════════════════════════════════════════════════
       Confirmation Modal — "Start a new chat?"
       ═══════════════════════════════════════════════════════════════════ -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="showNewChatModal"
        data-test="new-chat-modal"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm"
        @keydown.escape="cancelNewChat"
      >
        <div
          class="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl"
          role="alertdialog"
          aria-labelledby="new-chat-title"
          aria-describedby="new-chat-desc"
        >
          <div class="flex items-center gap-3">
            <div
              class="flex size-10 shrink-0 items-center justify-center rounded-full bg-amber-100 text-amber-600"
              aria-hidden="true"
            >
              <svg fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="size-5">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182" />
              </svg>
            </div>
            <div>
              <h2 id="new-chat-title" class="text-base font-semibold text-slate-900">
                Start a new chat?
              </h2>
              <p id="new-chat-desc" class="text-sm text-slate-500">
                This will close the active session and reconnect you to the AI Assistant.
              </p>
            </div>
          </div>
          <div class="mt-5 flex gap-3">
            <button
              type="button"
              data-test="new-chat-cancel"
              @click="cancelNewChat"
              class="flex-1 rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
            >
              Keep chatting
            </button>
            <button
              type="button"
              data-test="new-chat-confirm"
              @click="confirmNewChat"
              class="flex-1 rounded-lg bg-red-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-red-700"
            >
              New conversation
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
