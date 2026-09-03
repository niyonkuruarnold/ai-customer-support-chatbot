<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ChatMessage from './ChatMessage.vue'
import ChatFeedbackModal from './ChatFeedbackModal.vue'
import TypingIndicator from './TypingIndicator.vue'
import { MAX_MESSAGE_LENGTH, useChatStore } from '../stores/chat'

/**
 * Collapsible chat widget (floating launcher -> expanded panel).
 *
 * Reads and writes the shared chat store, so the conversation, session id,
 * escalation state, retries, and localStorage persistence are identical to
 * the full-page chat. Used on the admin/agent views so the customer chat is
 * always one click away; the chat view itself renders the full-page UI.
 */
const store = useChatStore()

const open = ref(false)
const input = ref('')
const feedRef = ref(null)
const showFeedbackModal = ref(false)
const feedbackSubmitted = ref(false)

const canSend = computed(() => {
  const text = input.value.trim()
  return (
    text.length > 0 &&
    text.length <= MAX_MESSAGE_LENGTH &&
    !store.isLoading
  )
})

async function handleSubmit() {
  if (!canSend.value) return
  const sent = await store.sendMessage(input.value)
  if (sent) input.value = ''
}

function handleEndChat() {
  if (store.sessionId && !feedbackSubmitted.value) {
    showFeedbackModal.value = true
  } else {
    store.closeAndResetConversation()
  }
}

function handleFeedbackSubmitted(data) {
  feedbackSubmitted.value = true
  showFeedbackModal.value = false
  store.closeAndResetConversation()
}

function handleFeedbackClose() {
  showFeedbackModal.value = false
  store.closeAndResetConversation()
}

function handleRetry(id) {
  store.retryMessage(id)
}

// Auto-scroll the feed to the newest message when open
watch(
  [() => store.messages, () => store.isLoading, open],
  async () => {
    if (!open.value) return
    await nextTick()
    const el = feedRef.value
    if (el) el.scrollTop = el.scrollHeight
  },
)

onMounted(() => {
  store.loadHistory()
  store.startPolling()
})
onBeforeUnmount(() => store.stopPolling())
</script>

<template>
  <!-- Collapsed: launcher bubble -->
  <button
    v-if="!open"
    type="button"
    data-test="chat-launcher"
    @click="open = true"
    class="fixed right-5 bottom-5 z-50 flex size-14 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-xl transition hover:scale-105 hover:shadow-2xl active:scale-95"
    :aria-label="store.isEscalated ? 'Open chat with your support agent' : 'Open chat with AI support'"
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

  <!-- Feedback Modal -->
  <ChatFeedbackModal
    :session-id="store.sessionId"
    :visible="showFeedbackModal"
    @submitted="handleFeedbackSubmitted"
    @close="handleFeedbackClose"
  />

  <!-- Expanded: chat panel -->
  <section
    v-else
    data-test="chat-window"
    class="fixed right-5 bottom-5 z-50 flex h-[32rem] max-h-[calc(100dvh-2.5rem)] w-[min(24rem,calc(100vw-2.5rem))] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl"
    role="dialog"
    aria-label="Chat with AI support"
  >
    <!-- Header -->
    <header
      class="flex items-center gap-3 border-b border-slate-200 bg-white/90 px-4 py-3"
    >
      <div
        class="flex size-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-sm"
        aria-hidden="true"
      >
        🤖
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <h2 class="truncate text-sm font-semibold text-slate-900">
            AI Customer Support
          </h2>
          <!-- Status Badge -->
          <span
            class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium"
            :class="
              store.isEscalated
                ? 'bg-amber-100 text-amber-700'
                : 'bg-emerald-100 text-emerald-700'
            "
          >
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
            {{
              store.isEscalated
                ? 'Connected to Agent'
                : 'AI Assistant'
            }}
          </span>
        </div>
        <p
          class="text-[11px]"
          :class="store.isEscalated ? 'text-amber-700' : 'text-slate-500'"
        >
          {{
            store.isEscalated
              ? 'A human agent is with you'
              : 'Online · replies instantly'
          }}
        </p>
      </div>
      <button
        type="button"
        data-test="chat-close"
        @click="handleEndChat"
        class="flex size-8 shrink-0 items-center justify-center rounded-full text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
        aria-label="End chat"
      >
        <svg
          fill="none"
          viewBox="0 0 24 24"
          stroke-width="2"
          stroke="currentColor"
          class="size-4"
          aria-hidden="true"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            d="M6 18L18 6M6 6l12 12"
          />
        </svg>
      </button>
    </header>

    <!-- Feed -->
    <main ref="feedRef" class="flex-1 overflow-y-auto overscroll-contain bg-slate-50">
      <div class="space-y-4 px-4 py-4">
        <p v-if="!store.hasMessages" class="pt-8 text-center text-sm text-slate-400">
          👋 Ask us anything — orders, refunds, shipping…
        </p>
        <ChatMessage
          v-for="message in store.messages"
          :key="message.id"
          :message="message"
          @retry="handleRetry"
        />
        <TypingIndicator v-if="store.isLoading" />
      </div>
    </main>

    <!-- Input -->
    <footer class="border-t border-slate-200 bg-white">
      <form class="flex items-end gap-2 p-3" @submit.prevent="handleSubmit">
        <textarea
          v-model="input"
          rows="1"
          :maxlength="MAX_MESSAGE_LENGTH"
          :placeholder="store.isEscalated ? 'Message the agent…' : 'Type your message…'"
          class="max-h-28 w-full resize-none rounded-2xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm leading-relaxed text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
          @keydown.enter.exact.prevent="handleSubmit"
        ></textarea>
        <button
          type="submit"
          :disabled="!canSend"
          class="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-md transition enabled:hover:shadow-lg enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
          :aria-label="store.isLoading ? 'Waiting for response' : 'Send message'"
        >
          <svg
            v-if="!store.isLoading"
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="2"
            stroke="currentColor"
            class="size-4 -rotate-45"
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
            class="size-4 animate-spin"
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
    </footer>
  </section>
</template>
