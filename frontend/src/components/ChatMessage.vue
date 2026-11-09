<script setup>
import { computed, ref } from 'vue'
import { markdownToHtml } from '../utils/markdown'

const props = defineProps({
  message: {
    type: Object,
    required: true, // { id, role, content, timestamp, status, error?, ragUsed?, contextReferences? }
  },
})

defineEmits(['retry'])

const isUser = computed(() => props.message.role === 'user')
const isAgent = computed(() => props.message.role === 'agent')
const isAssistant = computed(() => props.message.role === 'assistant')
const isFailed = computed(() => props.message.status === 'failed')
const isSending = computed(() => props.message.status === 'sending')

const citations = computed(() => props.message.contextReferences ?? [])
const sources = computed(() => props.message.sources ?? [])
const hasCitations = computed(() => citations.value.length > 0)
const hasSources = computed(() => sources.value.length > 0)
const showSources = ref(false)

/** AI responses are rendered as markdown; user/agent text stays plain + escaped. */
const renderedContent = computed(() =>
  isAssistant.value ? markdownToHtml(props.message.content) : null,
)

const formattedTime = computed(() =>
  new Date(props.message.timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  }),
)

const bubbleClass = computed(() => {
  const base =
    'rounded-2xl px-4 py-3 text-sm leading-relaxed shadow-sm'
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
        <!-- AI responses: rendered markdown (escaped HTML, linkified URLs) -->
        <div
          v-if="renderedContent"
          class="markdown-body"
          v-html="renderedContent"
        ></div>
        <!-- User/agent text: plain, escaped -->
        <div v-else class="whitespace-pre-wrap">{{ message.content }}</div>
      </div>

      <!-- RAG citations: clickable source documents from the pgvector store -->
      <div
        v-if="isAssistant && hasCitations"
        class="mt-2"
        data-test="citations"
      >
        <p class="text-[11px] font-medium text-slate-400">
          Answered from the knowledge base:
        </p>
        <div class="mt-1 flex flex-wrap gap-1.5">
          <a
            v-for="ref in citations"
            :key="ref.documentId ?? ref.title"
            href="?mode=knowledge"
            target="_blank"
            rel="noopener"
            class="inline-flex items-center gap-1 rounded-full border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-[11px] font-medium text-indigo-600 transition hover:bg-indigo-100"
            :title="`Source document (${ref.sourceType ?? 'UNKNOWN'}) — open the Knowledge Base`"
          >
            <span aria-hidden="true">📄</span>
            {{ ref.title }}
          </a>
        </div>
      </div>

      <!-- Source citations: collapsible badges for structured citation metadata -->
      <div
        v-if="isAssistant && hasSources"
        class="mt-2"
        data-test="source-citations"
      >
        <button
          type="button"
          class="inline-flex items-center gap-1.5 text-[11px] font-medium text-slate-400 transition hover:text-indigo-500"
          @click="showSources = !showSources"
        >
          <svg
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="2"
            stroke="currentColor"
            class="size-3 transition-transform"
            :class="showSources ? 'rotate-90' : ''"
            aria-hidden="true"
          >
            <path stroke-linecap="round" stroke-linejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" />
          </svg>
          {{ sources.length }} source{{ sources.length !== 1 ? 's' : '' }} cited
        </button>
        <div
          v-if="showSources"
          class="mt-1.5 flex flex-wrap gap-1.5"
        >
          <span
            v-for="src in sources"
            :key="src.sourceId ?? src.title"
            class="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-medium text-slate-600"
            :title="`Source type: ${src.sourceType ?? 'UNKNOWN'}`"
          >
            <span aria-hidden="true">
              {{ src.sourceType === 'PDF' ? '📕' : src.sourceType === 'MARKDOWN' ? '📝' : '📄' }}
            </span>
            {{ src.title }}
          </span>
        </div>
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

<style scoped>
/* Typography for rendered markdown inside the assistant bubble. */
.markdown-body > :first-child {
  margin-top: 0;
}
.markdown-body > :last-child {
  margin-bottom: 0;
}
.markdown-body p {
  margin: 0.5rem 0;
}
.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  margin: 0.75rem 0 0.375rem;
  font-weight: 600;
  line-height: 1.3;
}
.markdown-body h1 {
  font-size: 1.05rem;
}
.markdown-body h2 {
  font-size: 1rem;
}
.markdown-body h3 {
  font-size: 0.95rem;
}
.markdown-body ul,
.markdown-body ol {
  margin: 0.5rem 0;
  padding-left: 1.25rem;
}
.markdown-body ul {
  list-style: disc;
}
.markdown-body ol {
  list-style: decimal;
}
.markdown-body li {
  margin: 0.2rem 0;
}
.markdown-body a {
  color: #4f46e5;
  font-weight: 500;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.markdown-body code {
  border-radius: 0.25rem;
  background: rgb(241 245 249);
  padding: 0.1rem 0.3rem;
  font-size: 0.85em;
}
.markdown-body pre {
  margin: 0.5rem 0;
  overflow-x: auto;
  border-radius: 0.5rem;
  background: rgb(241 245 249);
  padding: 0.75rem;
}
.markdown-body pre code {
  background: transparent;
  padding: 0;
}
.markdown-body blockquote {
  margin: 0.5rem 0;
  border-left: 3px solid rgb(226 232 240);
  padding-left: 0.75rem;
  color: rgb(100 116 139);
}
.markdown-body table {
  margin: 0.5rem 0;
  border-collapse: collapse;
  width: 100%;
}
.markdown-body th,
.markdown-body td {
  border: 1px solid rgb(226 232 240);
  padding: 0.3rem 0.5rem;
}
</style>
