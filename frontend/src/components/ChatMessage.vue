<script setup>
import { computed } from 'vue'

const props = defineProps({
  message: {
    type: Object,
    required: true, // { id, role, content, timestamp, status, error? }
  },
})

defineEmits(['retry'])

const isUser = computed(() => props.message.role === 'user')
const isAgent = computed(() => props.message.role === 'agent')
const isFailed = computed(() => props.message.status === 'failed')
const isSending = computed(() => props.message.status === 'sending')

const formattedTime = computed(() =>
  new Date(props.message.timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  }),
)

const bubbleClass = computed(() => {
  const base =
    'rounded-2xl px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap shadow-sm'
  const tone = isUser.value
    ? 'bg-gradient-to-br from-indigo-500 to-violet-600 text-white rounded-br-md'
    : isAgent.value
      ? 'bg-gradient-to-br from-emerald-500 to-teal-600 text-white rounded-bl-md'
      : 'rounded-bl-md border border-slate-200 bg-white text-slate-800'
  const state = isFailed.value
    ? ' ring-1 ring-red-400'
    : isSending.value
      ? ' opacity-60'
      : ''
  return `${base} ${tone}${state}`
})
</script>

<template>
  <div class="flex items-start gap-3" :class="isUser ? 'flex-row-reverse' : ''">
    <!-- Avatar -->
    <div
      class="flex size-9 shrink-0 items-center justify-center rounded-full text-lg shadow-sm"
      :class="
        isUser
          ? 'bg-slate-200'
          : 'bg-gradient-to-br from-indigo-500 to-violet-600'
      "
      :aria-hidden="true"
    >
      <span v-if="isUser" class="text-base">🙂</span>
      <span v-else-if="isAgent" class="text-base">🎧</span>
      <span v-else class="text-base">🤖</span>
    </div>

    <!-- Bubble + meta -->
    <div class="max-w-[85%] sm:max-w-[70%]">
      <div :class="bubbleClass" :aria-invalid="isFailed || undefined">
        {{ message.content }}
      </div>

      <!-- Failed state: inline error + retry chip -->
      <div
        v-if="isFailed"
        class="mt-1.5 flex items-center gap-2"
        :class="isUser ? 'flex-row-reverse' : ''"
      >
        <span
          class="inline-flex items-center gap-1 text-[11px] font-medium text-red-500"
        >
          <svg
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="2"
            stroke="currentColor"
            class="size-3.5"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z"
            />
          </svg>
          Failed to send
        </span>
        <button
          type="button"
          data-test="retry"
          @click="$emit('retry', message.id)"
          class="inline-flex items-center gap-1 rounded-full border border-red-200 bg-red-50 px-3 py-1 text-[11px] font-medium text-red-600 transition hover:bg-red-100"
        >
          <svg
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="2"
            stroke="currentColor"
            class="size-3"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99"
            />
          </svg>
          Retry
        </button>
      </div>

      <p
        class="mt-1 text-[11px] text-slate-400"
        :class="isUser ? 'text-right' : ''"
      >
        {{
          isUser ? 'You' : isAgent ? 'Support Agent' : 'Support AI'
        }} · {{ formattedTime }}
      </p>
    </div>
  </div>
</template>
