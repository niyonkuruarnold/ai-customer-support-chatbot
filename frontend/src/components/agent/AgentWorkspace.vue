<script setup>
import { ref } from 'vue'
import { useAgentStore } from '../../stores/agent'
import AgentTicketList from './AgentTicketList.vue'
import AgentConversation from './AgentConversation.vue'
import KnowledgeBaseManager from '../admin/KnowledgeBaseManager.vue'

defineEmits(['switch-to-chat'])

const store = useAgentStore()

// Workspace tabs: tickets queue or knowledge base (RAG) manager
const activeTab = ref('tickets')
try {
  if (localStorage.getItem('ai-support-chat:agentTab') === 'knowledge') {
    activeTab.value = 'knowledge'
  }
} catch {
  // ignore storage errors
}

function setTab(tab) {
  activeTab.value = tab
  try {
    localStorage.setItem('ai-support-chat:agentTab', tab)
  } catch {
    // ignore storage errors
  }
}

const username = ref('')
const password = ref('')
const loginLoading = ref(false)
const loginError = ref('')

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
        ? 'Invalid agent credentials. Use the Spring Security user (default admin / admin123).'
        : 'Could not reach the backend. Is it running on port 8080?'
  } finally {
    loginLoading.value = false
  }
}
</script>

<template>
  <div class="flex h-dvh flex-col bg-slate-100 font-sans text-slate-900">
    <!-- Workspace header -->
    <header class="z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div class="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3">
        <div
          class="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-slate-700 to-slate-900 text-lg text-white shadow-md"
          aria-hidden="true"
        >
          🎧
        </div>
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-base font-semibold">Agent Workspace</h1>
          <p class="truncate text-xs text-slate-500">
            <template v-if="store.authenticated">
              Signed in as
              <span class="font-medium text-slate-700">{{ store.agentName }}</span>
              · {{ store.escalatedCount }} escalated
            </template>
            <template v-else>Human handoff queue</template>
          </p>
        </div>
        <button
          v-if="store.authenticated"
          type="button"
          @click="store.logout()"
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

    <!-- Login gate -->
    <div
      v-if="!store.authenticated"
      class="flex flex-1 items-center justify-center p-4"
    >
      <form
        class="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
        @submit.prevent="handleLogin"
      >
        <h2 class="text-lg font-semibold text-slate-800">Agent sign in</h2>
        <p class="mt-1 text-sm leading-relaxed text-slate-500">
          Sign in to take over escalated customer conversations. Uses the
          Spring Security HTTP Basic credentials configured on the backend.
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

    <!-- Workspace panels -->
    <template v-else>
      <!-- Tab navigation -->
      <nav
        class="flex shrink-0 items-center gap-1 border-b border-slate-200 bg-white px-4"
        aria-label="Workspace sections"
      >
        <button
          type="button"
          @click="setTab('tickets')"
          class="relative px-3 py-2.5 text-sm font-medium transition"
          :class="
            activeTab === 'tickets'
              ? 'text-indigo-600'
              : 'text-slate-500 hover:text-slate-700'
          "
        >
          🎧 Tickets
          <span
            v-if="activeTab === 'tickets'"
            class="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-indigo-600"
          ></span>
        </button>
        <button
          type="button"
          @click="setTab('knowledge')"
          class="relative px-3 py-2.5 text-sm font-medium transition"
          :class="
            activeTab === 'knowledge'
              ? 'text-indigo-600'
              : 'text-slate-500 hover:text-slate-700'
          "
        >
          📚 Knowledge Base
          <span
            v-if="activeTab === 'knowledge'"
            class="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-indigo-600"
          ></span>
        </button>
      </nav>

      <div v-if="activeTab === 'tickets'" class="flex min-h-0 flex-1">
        <AgentTicketList class="w-72 shrink-0 border-r border-slate-200 bg-white sm:w-80" />
        <AgentConversation class="min-w-0 flex-1" />
      </div>
      <KnowledgeBaseManager v-else class="flex min-h-0 flex-1" />
    </template>
  </div>
</template>
