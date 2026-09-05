<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import { useCustomerChatSession } from '../composables/useCustomerChatSession'
import { fetchSessionInfo, sendChatMessage } from '../api/chat'
import axios from 'axios'

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

const { sessionId, initSession, clearSession } = useCustomerChatSession()

const messages = ref([])
const input = ref('')
const isLoading = ref(false)
const conversationStatus = ref('AI_ASSISTANT')
const showFeedbackModal = ref(false)
const feedbackScore = ref(0)
const feedbackComment = ref('')
const feedbackSubmitting = ref(false)
const feedRef = ref(null)

// ---- Status badge config ----
const statusConfig = {
  AI_ASSISTANT:            { label: 'AI Assistant',          classes: 'bg-indigo-800 text-indigo-100' },
  WAITING_FOR_AGENT:       { label: 'Waiting for Agent',     classes: 'bg-amber-500 text-white' },
  CONNECTED_TO_AGENT:      { label: 'Connected to Agent',    classes: 'bg-emerald-500 text-white' },
  CLOSED:                  { label: 'Session Ended',         classes: 'bg-gray-400 text-white' },
}

const currentStatus = () => statusConfig[conversationStatus.value] ?? statusConfig.AI_ASSISTANT

// ---- Session restoration ----
onMounted(async () => {
  const sid = initSession()
  try {
    const { data } = await axios.get(`${apiBase}/v1/chat/session/${sid}/messages`)
    messages.value = Array.isArray(data) ? data : []
    await nextTick()
    scrollToBottom()
  } catch {
    // session may not exist yet — that's fine
  }
})

// ---- Send message ----
async function handleSend() {
  const text = input.value.trim()
  if (!text || isLoading.value) return

  // Optimistic add
  messages.value.push({
    id: Date.now(),
    sessionId: sessionId.value,
    sender: 'CUSTOMER',
    content: text,
    timestamp: new Date().toISOString(),
  })
  input.value = ''
  isLoading.value = true
  await nextTick()
  scrollToBottom()

  try {
    const { data } = await axios.post(`${apiBase}/v1/chat/message`, {
      message: text,
      sessionId: sessionId.value,
    })
    // Update session id if this was the first message
    if (data.sessionId && !sessionId.value) {
      sessionId.value = String(data.sessionId)
    }
    if (data.status) conversationStatus.value = data.status

    messages.value.push({
      id: Date.now() + 1,
      sessionId: sessionId.value,
      sender: 'AI',
      content: data.response ?? data.content ?? '',
      timestamp: new Date().toISOString(),
    })
  } catch {
    messages.value.push({
      id: Date.now() + 1,
      sessionId: sessionId.value,
      sender: 'AI',
      content: 'Sorry, something went wrong. Please try again.',
      timestamp: new Date().toISOString(),
    })
  } finally {
    isLoading.value = false
    await nextTick()
    scrollToBottom()
  }
}

// ---- End chat → open feedback modal ----
function handleEndChat() {
  showFeedbackModal.value = true
}

// ---- CSAT Feedback ----
async function submitFeedback() {
  if (!feedbackScore.value || feedbackSubmitting.value) return
  feedbackSubmitting.value = true
  try {
    await axios.post(
      `${apiBase}/v1/chat/session/${sessionId.value}/feedback`,
      { csatScore: feedbackScore.value, csatComment: feedbackComment.value || null },
    )
    conversationStatus.value = 'CLOSED'
    showFeedbackModal.value = false
    feedbackScore.value = 0
    feedbackComment.value = ''
    clearSession()
  } catch {
    // feedback failure is non-blocking
  } finally {
    feedbackSubmitting.value = false
  }
}

function scrollToBottom() {
  const el = feedRef.value
  if (el) el.scrollTop = el.scrollHeight
}

// Auto-scroll on new messages
watch(messages, async () => {
  await nextTick()
  scrollToBottom()
}, { deep: true })
</script>

<template>
  <div class="flex h-[32rem] w-[min(24rem,calc(100vw-2rem))] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl">
    <!-- Header -->
    <header class="flex items-center gap-3 border-b border-slate-200 bg-white/90 px-4 py-3">
      <div
        class="flex size-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-sm"
      >
        🤖
      </div>
      <div class="min-w-0 flex-1">
        <h2 class="truncate text-sm font-semibold text-slate-900">Customer Support</h2>
      </div>
      <span
        class="inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-semibold"
        :class="currentStatus().classes"
      >
        {{ currentStatus().label }}
      </span>
    </header>

    <!-- Message feed -->
    <main ref="feedRef" class="flex-1 overflow-y-auto overscroll-contain bg-slate-50 px-4 py-4">
      <div v-if="messages.length === 0" class="flex h-full items-center justify-center text-sm text-slate-400">
        👋 How can we help you today?
      </div>

      <div class="space-y-3">
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="flex"
          :class="msg.sender === 'CUSTOMER' ? 'justify-end' : 'justify-start'"
        >
          <div
            class="max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed shadow-sm"
            :class="msg.sender === 'CUSTOMER'
              ? 'bg-indigo-600 text-white rounded-br-md'
              : 'bg-slate-200 text-slate-800 rounded-bl-md'"
          >
            <p class="whitespace-pre-wrap">{{ msg.content }}</p>
            <p
              class="mt-1 text-[10px]"
              :class="msg.sender === 'CUSTOMER' ? 'text-indigo-200' : 'text-slate-400'"
            >
              {{ msg.sender === 'CUSTOMER' ? 'You' : msg.sender === 'AGENT' ? 'Agent' : 'AI' }}
              · {{ new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
            </p>
          </div>
        </div>

        <!-- Typing indicator -->
        <div v-if="isLoading" class="flex justify-start">
          <div class="rounded-2xl rounded-bl-md bg-slate-200 px-4 py-3 text-sm text-slate-500">
            <span class="inline-flex gap-1">
              <span class="animate-bounce" style="animation-delay: 0ms">·</span>
              <span class="animate-bounce" style="animation-delay: 150ms">·</span>
              <span class="animate-bounce" style="animation-delay: 300ms">·</span>
            </span>
          </div>
        </div>
      </div>
    </main>

    <!-- Input -->
    <footer class="border-t border-slate-200 bg-white">
      <div class="flex items-center gap-2 p-3">
        <textarea
          v-model="input"
          rows="1"
          placeholder="Type your message…"
          class="max-h-24 w-full resize-none rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm leading-relaxed text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
          @keydown.enter.exact.prevent="handleSend"
        ></textarea>
        <button
          type="button"
          :disabled="!input.trim() || isLoading"
          class="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-md transition enabled:hover:shadow-lg enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
          @click="handleSend"
        >
          <svg fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="size-4 -rotate-45">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 12 3.269 3.125A59.769 59.769 0 0 1 21.485 12 59.768 59.768 0 0 1 3.27 20.875L5.999 12Zm0 0h7.5" />
          </svg>
        </button>
      </div>
      <div class="flex justify-center pb-2">
        <button
          type="button"
          class="text-xs font-medium text-slate-400 transition hover:text-red-500"
          @click="handleEndChat"
        >
          End Chat
        </button>
      </div>
    </footer>

    <!-- CSAT Feedback Modal -->
    <Teleport to="body">
      <div
        v-if="showFeedbackModal"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/40"
        @click.self="showFeedbackModal = false"
      >
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl">
          <h3 class="mb-1 text-lg font-semibold text-slate-900">Rate your experience</h3>
          <p class="mb-4 text-sm text-slate-500">How satisfied were you with the support?</p>

          <!-- Star rating -->
          <div class="mb-4 flex items-center justify-center gap-1">
            <button
              v-for="star in 5"
              :key="star"
              type="button"
              class="text-3xl transition hover:scale-110"
              :class="star <= feedbackScore ? 'text-amber-400' : 'text-slate-300'"
              @click="feedbackScore = star"
            >
              ★
            </button>
          </div>

          <!-- Comment textarea -->
          <textarea
            v-model="feedbackComment"
            rows="3"
            placeholder="Optional comment…"
            class="mb-4 w-full resize-none rounded-xl border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
          ></textarea>

          <!-- Actions -->
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="flex-1 rounded-xl border border-slate-300 px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-50"
              @click="showFeedbackModal = false"
            >
              Skip
            </button>
            <button
              type="button"
              :disabled="!feedbackScore || feedbackSubmitting"
              class="flex-1 rounded-xl bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
              @click="submitFeedback"
            >
              {{ feedbackSubmitting ? 'Submitting…' : 'Submit' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
