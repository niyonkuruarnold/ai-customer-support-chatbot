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
import ChatWindow from './components/ChatWindow.vue'
import TypingIndicator from './components/TypingIndicator.vue'
import AgentWorkspace from './components/agent/AgentWorkspace.vue'
import KnowledgeBaseAdmin from './components/admin/KnowledgeBaseAdmin.vue'
import TicketDashboard from './components/admin/TicketDashboard.vue'
import MyReservations from './components/reservations/MyReservations.vue'
import { MAX_MESSAGE_LENGTH, useChatStore } from './stores/chat'

const store = useChatStore()

// View mode: 'chat' | 'agent' (workspace) | 'knowledge' (KB admin) | 'tickets'.
// Persisted in localStorage and deep-linkable via ?mode=agent|knowledge|tickets.
function initialView() {
  const param = new URLSearchParams(window.location.search).get('mode')
  if (param === 'agent' || param === 'knowledge' || param === 'tickets' || param === 'reservations') return param
  const saved = localStorage.getItem('ai-support-chat:mode')
  return saved === 'agent' || saved === 'knowledge' || saved === 'tickets' || saved === 'reservations'
    ? saved
    : 'chat'
}

const view = ref(initialView())

function setView(next) {
  view.value = next
  try {
    localStorage.setItem('ai-support-chat:mode', next)
  } catch {
    // ignore storage errors
  }
  const url = new URL(window.location.href)
  if (next === 'chat') url.searchParams.delete('mode')
  else url.searchParams.set('mode', next)
  window.history.replaceState({}, '', url)
}

const input = ref('')
const feedRef = ref(null)
const inputRef = ref(null)

const confirmingClear = ref(false)
let clearConfirmTimer = null

const canSend = computed(() => {
  const text = input.value.trim()
  return (
    text.length > 0 &&
    text.length <= MAX_MESSAGE_LENGTH &&
    !store.isLoading
  )
})

const suggestedPrompts = [
  'How do I track my order?',
  'What are your support hours?',
  'I need help with a refund',
  'Talk to a human agent',
]

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
</script>

<template>
  <!-- Agent workspace (togglable mode; also deep-linkable via ?mode=agent) -->
  <AgentWorkspace v-if="view === 'agent'" @switch-to-chat="setView('chat')" />

  <!-- Dedicated Knowledge Base admin page (?mode=knowledge) -->
  <KnowledgeBaseAdmin
    v-else-if="view === 'knowledge'"
    @switch-to-chat="setView('chat')"
  />

  <!-- Ticket lifecycle dashboard (?mode=tickets) -->
  <TicketDashboard
    v-else-if="view === 'tickets'"
    @switch-to-chat="setView('chat')"
  />

  <!-- Tool reservations dashboard (?mode=reservations) -->
  <MyReservations
    v-else-if="view === 'reservations'"
    @switch-to-chat="setView('chat')"
  />

  <!-- Collapsible chat widget on the admin/agent views -->
  <ChatWindow v-if="view !== 'chat'" />

  <!-- Customer chat -->
  <div
    v-else
    class="flex h-dvh flex-col bg-slate-100 font-sans text-slate-900"
  >
    <!-- Header -->
    <header class="z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div class="mx-auto flex max-w-3xl items-center gap-3 px-4 py-3">
        <div
          class="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-md"
        >
          <svg
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="1.8"
            stroke="currentColor"
            class="size-5"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M8.625 12a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H8.25m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H12m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 0 1-2.555-.337A5.972 5.972 0 0 1 5.41 20.97a5.969 5.969 0 0 1-.474-.065 4.48 4.48 0 0 0 .978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25Z"
            />
          </svg>
        </div>
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-base font-semibold">
            AI Customer Support
          </h1>
          <p
            class="flex items-center gap-1.5 text-xs"
            :class="store.isEscalated ? 'text-amber-700' : 'text-slate-500'"
          >
            <span class="relative flex size-2" aria-hidden="true">
              <span
                class="absolute inline-flex size-full animate-ping rounded-full opacity-75"
                :class="store.isEscalated ? 'bg-amber-400' : 'bg-emerald-400'"
              ></span>
              <span
                class="relative inline-flex size-2 rounded-full"
                :class="store.isEscalated ? 'bg-amber-500' : 'bg-emerald-500'"
              ></span>
            </span>
            {{ store.isEscalated ? 'Agent Active · AI paused, a human agent is with you' : 'Online · replies instantly' }}
          </p>
        </div>

        <!-- Clear conversation -->
        <button
          type="button"
          @click="onClearClick"
          :disabled="!store.hasMessages && !confirmingClear"
          class="flex shrink-0 items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium transition disabled:cursor-not-allowed disabled:opacity-40"
          :class="
            confirmingClear
              ? 'border-red-600 bg-red-600 text-white'
              : 'border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-red-600'
          "
          :aria-label="confirmingClear ? 'Confirm clearing the conversation' : 'Clear conversation'"
        >
          <svg
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="1.8"
            stroke="currentColor"
            class="size-3.5"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0"
            />
          </svg>
          {{ confirmingClear ? 'Confirm clear?' : 'Clear chat' }}
        </button>

        <!-- Ticket dashboard toggle -->
        <button
          type="button"
          @click="setView('tickets')"
          class="flex shrink-0 items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 hover:text-emerald-600"
        >
          🎫 Tickets
        </button>

        <!-- Knowledge base admin toggle -->
        <button
          type="button"
          @click="setView('knowledge')"
          class="flex shrink-0 items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 hover:text-violet-600"
        >
          📚 Knowledge Base
        </button>

        <!-- Reservations toggle -->
        <button
          type="button"
          @click="setView('reservations')"
          class="flex shrink-0 items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 hover:text-emerald-600"
        >
          🛠️ Reservations
        </button>

        <!-- Agent workspace toggle -->
        <button
          type="button"
          @click="setView('agent')"
          class="flex shrink-0 items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 hover:text-indigo-600"
        >
          🎧 Agent Workspace
        </button>
      </div>
    </header>

    <!-- Agent Active banner (customer view) -->
    <div
      v-if="store.isEscalated"
      class="border-b border-amber-200 bg-amber-50"
    >
      <div class="mx-auto flex max-w-3xl items-center gap-2 px-4 py-2">
        <span class="relative flex size-2" aria-hidden="true">
          <span
            class="absolute inline-flex size-full animate-ping rounded-full bg-amber-400 opacity-75"
          ></span>
          <span
            class="relative inline-flex size-2 rounded-full bg-amber-500"
          ></span>
        </span>
        <p class="text-xs font-medium text-amber-800">
          🎧 Agent Active — the AI assistant is paused and a human agent is
          now handling this chat. They can see our conversation and will
          reply right here.
        </p>
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
            class="flex size-16 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-violet-600 text-3xl shadow-lg"
            aria-hidden="true"
          >
            🤖
          </div>
          <h2 class="mt-6 text-xl font-semibold">
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
              class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-700 shadow-sm transition hover:border-indigo-300 hover:text-indigo-600"
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
    <footer class="border-t border-slate-200 bg-white">
      <div class="mx-auto max-w-3xl px-4 py-3">
        <form class="flex items-end gap-2" @submit.prevent="handleSubmit">
          <div class="relative flex-1">
            <textarea
              ref="inputRef"
              v-model="input"
              rows="1"
              :maxlength="MAX_MESSAGE_LENGTH"
              :placeholder="store.isEscalated ? 'Message the agent… (Shift+Enter for a new line)' : 'Type your message… (Shift+Enter for a new line)'"
              class="max-h-40 w-full resize-none rounded-2xl border border-slate-300 bg-white px-4 py-3 pr-16 text-sm leading-relaxed text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
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
            class="flex size-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-md transition enabled:hover:shadow-lg enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
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
              <circle
                class="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                stroke-width="4"
              />
              <path
                class="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z"
              />
            </svg>
          </button>
        </form>
        <p class="mt-1.5 text-center text-[11px] text-slate-400">
          Powered by OpenAI · Responses are generated by the Spring Boot
          backend
        </p>
      </div>
    </footer>
  </div>
</template>
