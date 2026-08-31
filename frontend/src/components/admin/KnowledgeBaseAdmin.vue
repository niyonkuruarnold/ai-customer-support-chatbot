<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useKnowledgeBaseStore } from '../../stores/knowledgeBase'
import { useAgentStore } from '../../stores/agent'
import { useToasts } from '../../composables/useToasts'

const props = defineProps({
  /** When true, the parent shell provides the header and auth gate. */
  embedded: { type: Boolean, default: false },
})
defineEmits(['switch-to-chat'])

const store = useKnowledgeBaseStore()
const agentStore = useAgentStore()
const { toasts, push, remove } = useToasts()

// .md / .txt only, per the document management spec
const ACCEPTED = '.txt,.text,.md,.markdown'

const dragActive = ref(false)
const fileInput = ref(null)

// Paste-content tab
const uploadTab = ref('paste') // 'upload' | 'paste'
const pasteContent = ref('')
const pasteTitle = ref('')
const pasteError = ref('')

const username = ref('')
const password = ref('')
const loginLoading = ref(false)
const loginError = ref('')

const sourceTypeLabel = { TEXT: 'Text', MARKDOWN: 'Markdown', PDF: 'PDF' }
const sourceTypeClass = {
  TEXT: 'bg-sky-100 text-sky-700',
  MARKDOWN: 'bg-violet-100 text-violet-700',
  PDF: 'bg-rose-100 text-rose-700',
}

const isAuthenticated = computed(() => agentStore.authenticated)

onMounted(() => {
  if (agentStore.authenticated) {
    store.fetchAll()
  }
})

// A 401 mid-session (e.g. credentials revoked) sends the user back to the
// sign-in gate with a toast explaining why.
watch(
  () => store.needsAuth,
  (needsAuth) => {
    if (needsAuth) {
      agentStore.logout()
      push('error', 'Session expired — please sign in again.')
    }
  },
)

onBeforeUnmount(() => {
  // This page never needs the agent workspace's ticket polling
  agentStore.stopPolling()
})

async function handleLogin() {
  loginLoading.value = true
  loginError.value = ''
  try {
    await agentStore.login(username.value, password.value)
    // Dedicated KB page: no ticket polling needed after sign-in
    agentStore.stopPolling()
    username.value = ''
    password.value = ''
    await store.fetchAll()
  } catch (err) {
    loginError.value =
      err?.status === 401
        ? 'Invalid admin credentials. Use the Spring Security user (default admin / admin123).'
        : 'Could not reach the backend. Is it running on port 8080?'
  } finally {
    loginLoading.value = false
  }
}

function handleLogout() {
  agentStore.logout()
  store.clearError()
}

function titleForFile(file) {
  return file.name.replace(/\.[^.]+$/, '')
}

function isAccepted(file) {
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  return ['txt', 'text', 'md', 'markdown'].includes(ext)
}

async function handleFiles(fileList) {
  const files = Array.from(fileList || [])
  if (!files.length) return
  dragActive.value = false
  for (const file of files) {
    if (!isAccepted(file)) {
      push('error', `"${file.name}" is not supported — upload .md or .txt files.`)
      continue
    }
    const ok = await store.uploadFile(file, titleForFile(file))
    if (ok) {
      push('success', `"${file.name}" indexed — chunks embedded into the vector store.`)
    } else {
      push('error', store.error || `Could not index "${file.name}".`)
    }
  }
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

/** Paste / type raw text and index it as a .md Blob file. */
async function handlePasteSubmit() {
  pasteError.value = ''
  const text = pasteContent.value.trim()
  if (!text) {
    pasteError.value = 'Paste some content first.'
    return
  }
  const title = pasteTitle.value.trim() || 'Pasted content'
  const file = new File([text], 'company_policies.md', {
    type: 'text/markdown',
  })
  const ok = await store.uploadFile(file, title)
  if (ok) {
    push('success', `"${title}" indexed — chunks embedded into the vector store.`)
    pasteContent.value = ''
    pasteTitle.value = ''
  } else {
    pasteError.value = store.error || 'Could not index the pasted content.'
  }
}

async function removeDocument(doc) {
  const ok = await store.removeDocument(doc.id)
  if (ok) {
    push('success', `"${doc.title}" removed from the knowledge base.`)
  } else {
    push('error', store.error || 'Could not delete the document.')
  }
}

function formatDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleDateString([], { year: 'numeric', month: 'short', day: 'numeric' })
}
</script>

<template>
  <div class="flex h-full flex-col bg-slate-100 font-sans text-slate-900">
    <!-- Toasts -->
    <div
      class="pointer-events-none fixed top-4 right-4 z-50 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2"
      aria-live="polite"
    >
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="pointer-events-auto flex items-start gap-2.5 rounded-xl border px-4 py-3 text-sm shadow-lg"
        :class="
          toast.type === 'success'
            ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
            : 'border-red-200 bg-red-50 text-red-800'
        "
        role="status"
      >
        <span class="shrink-0" aria-hidden="true">
          {{ toast.type === 'success' ? '✅' : '⚠️' }}
        </span>
        <p class="min-w-0 flex-1 leading-snug">{{ toast.message }}</p>
        <button
          type="button"
          class="shrink-0 text-xs font-semibold opacity-50 transition hover:opacity-100"
          :aria-label="'Dismiss notification'"
          @click="remove(toast.id)"
        >
          ✕
        </button>
      </div>
    </div>

    <!-- Header (standalone mode only) -->
    <header v-if="!embedded" class="z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div class="mx-auto flex max-w-5xl items-center gap-3 px-4 py-3">
        <div
          class="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-violet-600 to-indigo-800 text-lg text-white shadow-md"
          aria-hidden="true"
        >
          📚
        </div>
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-base font-semibold">Knowledge Base Admin</h1>
          <p class="truncate text-xs text-slate-500">
            <template v-if="isAuthenticated">
              Signed in as
              <span class="font-medium text-slate-700">{{ agentStore.agentName }}</span>
              · {{ store.documents.length }} indexed document{{
                store.documents.length === 1 ? '' : 's'
              }}
            </template>
            <template v-else>Document management & RAG indexing</template>
          </p>
        </div>
        <button
          v-if="isAuthenticated"
          type="button"
          @click="handleLogout"
          class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500 transition hover:bg-slate-50 hover:text-red-600"
        >
          Log out
        </button>
        <button
          type="button"
          @click="$emit('switch-to-chat')"
          class="flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-50 hover:text-indigo-600"
        >
          ← Customer chat
        </button>
      </div>
    </header>

    <!-- Sign-in gate (standalone mode only) -->
    <div v-if="!embedded && !isAuthenticated" class="flex flex-1 items-center justify-center p-4">
      <form
        class="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
        @submit.prevent="handleLogin"
      >
        <h2 class="text-lg font-semibold text-slate-800">Admin sign in</h2>
        <p class="mt-1 text-sm leading-relaxed text-slate-500">
          Sign in to upload and manage the knowledge base the AI assistant
          answers from. Uses the Spring Security HTTP Basic credentials.
        </p>
        <label class="mt-4 block text-sm font-medium text-slate-700">
          Username
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            required
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
          />
        </label>
        <label class="mt-3 block text-sm font-medium text-slate-700">
          Password
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
          />
        </label>
        <p v-if="loginError" class="mt-3 text-sm text-red-600" role="alert">
          {{ loginError }}
        </p>
        <button
          type="submit"
          :disabled="loginLoading"
          class="mt-5 w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white transition enabled:hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {{ loginLoading ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
    </div>

    <!-- Document management -->
    <main v-if="embedded || isAuthenticated" class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto max-w-5xl space-y-6 p-6">
        <div>
          <h2 class="text-lg font-semibold text-slate-800">Document management</h2>
          <p class="mt-1 text-sm leading-relaxed text-slate-500">
            Upload Markdown or text support documents — they are parsed, split
            into chunks, and embedded into PostgreSQL (pgvector) so the AI
            assistant can answer from them (RAG).
          </p>
        </div>

        <!-- Add content tabs -->
        <div class="flex items-center gap-1 rounded-xl bg-slate-100 p-1">
          <button
            type="button"
            data-test="upload-tab-paste"
            @click="uploadTab = 'paste'"
            class="flex-1 rounded-lg py-2 text-sm font-medium transition"
            :class="
              uploadTab === 'paste'
                ? 'bg-white text-indigo-700 shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
            "
          >
            ✏️ Paste Content
          </button>
          <button
            type="button"
            data-test="upload-tab-file"
            @click="uploadTab = 'upload'"
            class="flex-1 rounded-lg py-2 text-sm font-medium transition"
            :class="
              uploadTab === 'upload'
                ? 'bg-white text-indigo-700 shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
            "
          >
            📁 Upload Files
          </button>
        </div>

        <!-- Paste Content tab -->
        <section
          v-if="uploadTab === 'paste'"
          data-test="paste-section"
          class="rounded-2xl border border-slate-200 bg-white p-6"
        >
          <div v-if="store.uploading" class="flex flex-col items-center py-8">
            <div class="flex size-14 items-center justify-center rounded-2xl bg-indigo-100" aria-hidden="true">
              <svg class="size-7 animate-spin text-indigo-600" viewBox="0 0 24 24" fill="none">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
            </div>
            <p class="mt-4 text-sm font-medium text-indigo-600">
              Indexing — generating vector embeddings…
            </p>
          </div>

          <form v-else data-test="paste-form" class="space-y-4" @submit.prevent="handlePasteSubmit">
            <label class="block text-sm font-medium text-slate-700">
              Document title
              <input
                v-model="pasteTitle"
                type="text"
                data-test="paste-title"
                placeholder="e.g. Company Policies"
                class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
              />
            </label>
            <label class="block text-sm font-medium text-slate-700">
              Content (Markdown or plain text)
              <textarea
                v-model="pasteContent"
                rows="12"
                data-test="paste-textarea"
                placeholder="Paste or type your document content here…&#10;&#10;Supports Markdown formatting. The content will be indexed\ninto the vector store so the AI assistant can answer from it."
                class="mt-1 w-full resize-y rounded-lg border border-slate-300 bg-slate-50 px-3.5 py-3 font-mono text-sm leading-relaxed text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-indigo-400 focus:bg-white focus:ring-2 focus:ring-indigo-100"
              ></textarea>
            </label>
            <p v-if="pasteError" class="text-sm text-red-600" role="alert">
              {{ pasteError }}
            </p>
            <div class="flex items-center justify-between">
              <p class="text-xs text-slate-400">
                Pasted content is saved as a <code>.md</code> file and indexed into the vector store.
              </p>
              <button
                type="submit"
                data-test="paste-submit"
                :disabled="!pasteContent.trim()"
                class="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-semibold text-white shadow-sm transition enabled:hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Save & Index
              </button>
            </div>
          </form>
        </section>

        <!-- Drag & drop upload tab -->
        <section
          v-else
          data-test="dropzone"
          class="rounded-2xl border-2 border-dashed p-8 transition"
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
              v-if="store.uploading"
              class="flex size-14 items-center justify-center rounded-2xl bg-indigo-100"
              aria-hidden="true"
            >
              <svg
                class="size-7 animate-spin text-indigo-600"
                viewBox="0 0 24 24"
                fill="none"
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
            </div>
            <div
              v-else
              class="flex size-14 items-center justify-center rounded-2xl bg-indigo-100 text-2xl"
              aria-hidden="true"
            >
              📄
            </div>
            <p class="mt-4 text-sm font-medium text-slate-700">
              <template v-if="store.uploading">
                Indexing
                <span class="text-indigo-600">— generating vector embeddings…</span>
              </template>
              <template v-else>
                Drop Markdown or text files here, or
                <button
                  type="button"
                  @click="fileInput?.click()"
                  class="font-semibold text-indigo-600 hover:underline"
                >
                  browse
                </button>
              </template>
            </p>
            <p class="mt-1 text-xs text-slate-400">
              .md · .txt — parsed with Spring AI document readers, split into
              chunks, embedded into pgvector
            </p>
            <input
              ref="fileInput"
              type="file"
              class="hidden"
              :accept="ACCEPTED"
              multiple
              @change="onPick"
            />
          </div>
        </section>

        <!-- Indexed documents table -->
        <section>
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold text-slate-800">Indexed documents</h3>
            <p class="text-xs text-slate-400">
              {{ store.totalChunks }} chunks in the vector store
            </p>
          </div>

          <div
            v-if="store.loading"
            class="mt-3 flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white p-8 text-sm text-slate-400"
          >
            <svg class="size-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
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
            Loading knowledge base…
          </div>

          <div
            v-else-if="store.documents.length === 0"
            class="mt-3 rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-400"
          >
            No documents indexed yet — upload a Markdown or text file above.
          </div>

          <div
            v-else
            class="mt-3 overflow-hidden rounded-xl border border-slate-200 bg-white"
          >
            <table class="w-full text-left text-sm">
              <thead class="border-b border-slate-200 bg-slate-50 text-xs text-slate-500">
                <tr>
                  <th class="px-4 py-3 font-semibold">Title</th>
                  <th class="px-4 py-3 font-semibold">Source</th>
                  <th class="px-4 py-3 text-center font-semibold">Chunks</th>
                  <th class="px-4 py-3 font-semibold">Indexed</th>
                  <th class="px-4 py-3 text-right font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="doc in store.documents" :key="doc.id" class="hover:bg-slate-50">
                  <td class="max-w-xs px-4 py-3">
                    <p class="truncate font-medium text-slate-800">{{ doc.title }}</p>
                    <p v-if="doc.fileName" class="truncate text-xs text-slate-400">
                      {{ doc.fileName }}
                    </p>
                  </td>
                  <td class="px-4 py-3">
                    <span
                      class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                      :class="sourceTypeClass[doc.sourceType] ?? 'bg-slate-100 text-slate-600'"
                    >
                      {{ sourceTypeLabel[doc.sourceType] ?? doc.sourceType }}
                    </span>
                  </td>
                  <td class="px-4 py-3 text-center">
                    <span class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600">
                      {{ doc.chunkCount }}
                    </span>
                  </td>
                  <td class="px-4 py-3 text-xs whitespace-nowrap text-slate-500">
                    {{ formatDate(doc.createdAt) }}
                  </td>
                  <td class="px-4 py-3 text-right">
                    <button
                      type="button"
                      @click="removeDocument(doc)"
                      class="rounded-full border border-slate-200 px-3 py-1 text-xs font-medium text-slate-500 transition hover:border-red-300 hover:bg-red-50 hover:text-red-600"
                      :aria-label="`Delete ${doc.title}`"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>
