<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useAgentStore } from '../../stores/agent'
import ChatMessage from '../ChatMessage.vue'

const store = useAgentStore()

const replyText = ref('')
const noteText = ref('')
const feedRef = ref(null)

const SENDER_TO_ROLE = { USER: 'user', AI: 'assistant', AGENT: 'agent' }

const isConnected = computed(() =>
  ['ESCALATED', 'IN_PROGRESS'].includes(store.activeStatus),
)

const summaryBullets = computed(() =>
  (store.activeSummary || '').split('\n').filter((b) => b.trim().length > 0),
)

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
  const ok = await store.sendReply(replyText.value)
  if (ok) replyText.value = ''
}

function saveNote() {
  store.addNote(noteText.value)
  noteText.value = ''
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
          <div class="flex gap-2">
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

      <!-- Real-time handoff banner -->
      <div
        v-if="isConnected"
        class="border-b border-emerald-200 bg-emerald-50 px-4 py-2"
      >
        <p class="flex items-center gap-2 text-xs font-medium text-emerald-700">
          <span class="relative flex size-2" aria-hidden="true">
            <span
              class="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-75"
            ></span>
            <span
              class="relative inline-flex size-2 rounded-full bg-emerald-500"
            ></span>
          </span>
          Connected to Human Agent — replies you send appear instantly in the
          customer's chat.
        </p>
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
          <div class="mt-2 flex gap-2">
            <input
              v-model="noteText"
              type="text"
              placeholder="Add an internal note…"
              class="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
              @keydown.enter.prevent="saveNote"
            />
            <button
              type="button"
              @click="saveNote"
              :disabled="!noteText.trim()"
              class="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-600 transition enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Add
            </button>
          </div>
        </div>

        <div class="px-4 py-3">
          <form class="flex items-end gap-2" @submit.prevent="sendReply">
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
