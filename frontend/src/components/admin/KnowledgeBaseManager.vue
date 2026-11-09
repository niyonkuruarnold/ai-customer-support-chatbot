<script setup>
import { computed, onMounted, ref } from 'vue'
import { useKnowledgeBaseStore } from '../../stores/knowledgeBase'
import { useAgentStore } from '../../stores/agent'

const store = useKnowledgeBaseStore()
const agentStore = useAgentStore()

const ACCEPTED = '.txt,.text,.md,.markdown,.pdf'

const dragActive = ref(false)
const fileInput = ref(null)
const textTitle = ref('')
const textContent = ref('')
const expandedDocs = ref(new Set())
const deletedIds = ref(new Set())

const acceptedFiles = computed(() =>
  store.documents.filter((d) => !deletedIds.value.has(d.id)),
)

const sourceTypeLabel = {
  TEXT: 'Text',
  MARKDOWN: 'Markdown',
  PDF: 'PDF',
}

const sourceTypeClass = {
  TEXT: 'bg-sky-100 text-sky-700',
  MARKDOWN: 'bg-violet-100 text-violet-700',
  PDF: 'bg-rose-100 text-rose-700',
}

onMounted(() => store.fetchAll())

function toggleExpanded(id) {
  const next = new Set(expandedDocs.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedDocs.value = next
}

function isExpanded(id) {
  return expandedDocs.value.has(id)
}

function titleForFile(file) {
  return file.name.replace(/\.[^.]+$/, '')
}

async function handleFiles(fileList) {
  const files = Array.from(fileList || [])
  if (!files.length) return
  for (const file of files) {
    await store.uploadFile(file, titleForFile(file))
  }
  dragActive.value = false
}

function onDrop(event) {
  event.preventDefault()
  dragActive.value = false
  handleFiles(event.dataTransfer?.files)
}

function onPick(event) {
  handleFiles(event.target.files)
  event.target.value = '' // allow re-selecting the same file
}

async function submitText() {
  const title = textTitle.value.trim()
  const content = textContent.value.trim()
  if (!title || !content) return
  const ok = await store.addText(title, content)
  if (ok) {
    textTitle.value = ''
    textContent.value = ''
  }
}

async function removeDocument(id) {
  const ok = await store.removeDocument(id)
  if (ok) {
    deletedIds.value.add(id)
  }
}

function reauthenticate() {
  store.clearError()
  agentStore.logout()
}
</script>

<template>
  <div class="min-h-0 flex-1 overflow-y-auto">
    <div class="mx-auto max-w-4xl space-y-6 p-6">
      <div>
        <h2 class="text-lg font-semibold text-slate-800">Knowledge Base</h2>
        <p class="mt-1 text-sm leading-relaxed text-slate-500">
          Index support documents so the AI assistant can answer from your
          knowledge base (RAG). Files are parsed, split into chunks, and
          embedded into PostgreSQL (pgvector).
        </p>
      </div>

      <!-- Error banner -->
      <div
        v-if="store.error"
        class="flex items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3"
        role="alert"
      >
        <p class="text-sm text-red-700">{{ store.error }}</p>
        <div class="flex shrink-0 gap-2">
          <button
            v-if="store.needsAuth"
            type="button"
            @click="reauthenticate"
            class="rounded-full bg-red-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-red-700"
          >
            Sign in again
          </button>
          <button
            v-else
            type="button"
            @click="store.clearError()"
            class="rounded-full border border-red-300 bg-white px-3 py-1.5 text-xs font-medium text-red-700 transition hover:bg-red-50"
          >
            Dismiss
          </button>
        </div>
      </div>

      <!-- Drag & drop upload -->
      <section
        class="rounded-2xl border-2 border-dashed p-6 transition"
        :class="
          dragActive
            ? 'border-indigo-400 bg-indigo-50'
            : 'border-slate-300 bg-white hover:border-indigo-300'
        "
        @dragover.prevent="dragActive = true"
        @dragleave.prevent="dragActive = false"
        @drop.prevent="onDrop"
      >
        <div class="flex flex-col items-center text-center">
          <div
            class="flex size-12 items-center justify-center rounded-2xl bg-indigo-100 text-2xl"
            aria-hidden="true"
          >
            📄
          </div>
          <p class="mt-3 text-sm font-medium text-slate-700">
            Drop support files here, or
            <button
              type="button"
              @click="fileInput?.click()"
              class="font-semibold text-indigo-600 hover:underline"
            >
              browse
            </button>
          </p>
          <p class="mt-1 text-xs text-slate-400">
            .txt · .md · .pdf — parsed with Spring AI document readers and
            split into chunks
          </p>
          <input
            ref="fileInput"
            type="file"
            class="hidden"
            :accept="ACCEPTED"
            multiple
            @change="onPick"
          />
          <p
            v-if="store.uploading"
            class="mt-3 text-xs font-medium text-indigo-600"
          >
            Indexing… generating embeddings
          </p>
        </div>
      </section>

      <!-- Paste raw FAQ text -->
      <section class="rounded-2xl border border-slate-200 bg-white p-5">
        <h3 class="text-sm font-semibold text-slate-800">
          Paste FAQ / support text
        </h3>
        <form class="mt-3 space-y-3" @submit.prevent="submitText">
          <input
            v-model="textTitle"
            type="text"
            placeholder="Title, e.g. “Shipping & delivery policy”"
            maxlength="200"
            required
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
          />
          <textarea
            v-model="textContent"
            rows="5"
            placeholder="Paste the FAQ content here…"
            maxlength="100000"
            required
            class="w-full resize-y rounded-lg border border-slate-300 px-3 py-2 text-sm leading-relaxed outline-none transition placeholder:text-slate-400 focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
          ></textarea>
          <div class="flex justify-end">
            <button
              type="submit"
              :disabled="
                store.uploading || !textTitle.trim() || !textContent.trim()
              "
              class="rounded-full bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition enabled:hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {{ store.uploading ? 'Indexing…' : 'Add to knowledge base' }}
            </button>
          </div>
        </form>
      </section>

      <!-- Indexed documents -->
      <section>
        <div class="flex items-center justify-between">
          <h3 class="text-sm font-semibold text-slate-800">
            Indexed documents
            <span
              class="ml-1 rounded-full bg-slate-200 px-2 py-0.5 text-xs font-medium text-slate-600"
            >
              {{ acceptedFiles.length }}
            </span>
          </h3>
          <p class="text-xs text-slate-400">
            {{ store.totalChunks }} chunks in the vector store
          </p>
        </div>

        <div
          v-if="store.loading"
          class="mt-3 rounded-xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-400"
        >
          Loading knowledge base…
        </div>

        <div v-else-if="acceptedFiles.length === 0" class="mt-3">
          <div
            class="rounded-xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-400"
          >
            No documents indexed yet — upload a file or paste some text above.
          </div>
        </div>

        <ul v-else class="mt-3 space-y-3">
          <li
            v-for="doc in acceptedFiles"
            :key="doc.id"
            class="overflow-hidden rounded-xl border border-slate-200 bg-white"
          >
            <div class="flex items-center gap-3 px-4 py-3">
              <button
                type="button"
                class="flex min-w-0 flex-1 items-center gap-3 text-left"
                @click="toggleExpanded(doc.id)"
                :aria-expanded="isExpanded(doc.id)"
              >
                <span
                  class="text-slate-400 transition"
                  :class="isExpanded(doc.id) ? 'rotate-90' : ''"
                  aria-hidden="true"
                >
                  ▶
                </span>
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-sm font-medium text-slate-800">
                    {{ doc.title }}
                  </span>
                  <span class="block text-xs text-slate-400">
                    {{ doc.fileName || 'pasted text' }}
                  </span>
                </span>
                <span
                  class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium"
                  :class="sourceTypeClass[doc.sourceType] ?? 'bg-slate-100 text-slate-600'"
                >
                  {{ sourceTypeLabel[doc.sourceType] ?? doc.sourceType }}
                </span>
                <span
                  class="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600"
                >
                  {{ doc.chunkCount }} chunk{{ doc.chunkCount === 1 ? '' : 's' }}
                </span>
              </button>
              <button
                type="button"
                @click="removeDocument(doc.id)"
                class="shrink-0 rounded-full border border-slate-200 px-3 py-1 text-xs font-medium text-slate-500 transition hover:border-red-300 hover:text-red-600"
                :aria-label="`Delete ${doc.title}`"
              >
                Delete
              </button>
            </div>

            <!-- Chunk previews -->
            <div
              v-if="isExpanded(doc.id)"
              class="space-y-2 border-t border-slate-100 bg-slate-50 px-4 py-3"
            >
              <p
                v-if="store.chunksFor(doc.id).length === 0"
                class="text-xs text-slate-400"
              >
                No chunks (document was deleted from the vector store).
              </p>
              <div
                v-for="chunk in store.chunksFor(doc.id)"
                :key="chunk.id"
                class="rounded-lg border border-slate-200 bg-white px-3 py-2"
              >
                <p
                  class="mb-1 text-[10px] font-semibold tracking-wide text-slate-400 uppercase"
                >
                  Chunk {{ chunk.chunkIndex + 1 }}
                </p>
                <p class="line-clamp-3 text-xs leading-relaxed text-slate-600">
                  {{ chunk.content }}
                </p>
              </div>
            </div>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>
