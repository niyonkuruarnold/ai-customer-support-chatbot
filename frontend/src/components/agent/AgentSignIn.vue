<script setup>
/**
 * AgentSignIn — the professional, in-page sign-in modal for staff.
 *
 * Replaces the browser's native HTTP Basic dialog ("Sign in to access this
 * site"): the backend's SecurityConfig disables httpBasic and answers 401s
 * with plain JSON and no `WWW-Authenticate` header, so App.vue renders this
 * modal whenever a protected request reports 401 (session expiry), a demo
 * role switch fails, or the staff workspace is reached unauthenticated.
 *
 * A password is always required — credentials go to POST /api/auth/token
 * where the backend verifies them; empty submits are blocked inline.
 *
 * Username/password are two-way bound by the parent (v-model:username /
 * v-model:password); submission is delegated upward via the `submit` event.
 */
import { computed, onMounted, ref } from 'vue'

const username = defineModel('username', { default: '' })
const password = defineModel('password', { default: '' })

const props = defineProps({
  /** 'Admin' | 'Agent' — heading and copy adapt to the selected role. */
  roleLabel: { type: String, default: 'Agent' },
  /** True while the credential exchange is in flight. */
  loading: { type: Boolean, default: false },
  /** Login/session error to display under the fields (empty = hidden). */
  error: { type: String, default: '' },
})

const emit = defineEmits(['submit'])

const showPassword = ref(false)
const fieldError = ref('')
const usernameInput = ref(null)

onMounted(() => usernameInput.value?.focus())

const displayError = computed(() => fieldError.value || props.error)

function submit() {
  // A password is mandatory: block empty submits before hitting the backend.
  if (!username.value.trim() || !password.value) {
    fieldError.value = 'Enter both your username and password to continue.'
    return
  }
  fieldError.value = ''
  emit('submit')
}
</script>

<template>
  <div
    class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm"
  >
    <div
      class="w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-slate-900/5"
      role="dialog"
      aria-modal="true"
      aria-labelledby="agent-signin-title"
    >
      <!-- Brand header -->
      <div class="bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 px-6 py-5">
        <div class="flex items-center gap-3">
          <div
            class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-white/10 text-xl ring-1 ring-white/20"
            aria-hidden="true"
          >
            {{ roleLabel === 'Admin' ? '🔑' : '🎧' }}
          </div>
          <div class="min-w-0">
            <p class="text-[11px] font-semibold tracking-[0.2em] text-red-300 uppercase">
              CODAFRIQA Support Console
            </p>
            <h2
              id="agent-signin-title"
              class="text-lg font-semibold text-white"
            >
              {{ roleLabel }} Sign In
            </h2>
          </div>
        </div>
        <p class="mt-3 text-sm leading-relaxed text-slate-300">
          Sign in with your staff account to access the workspace.
        </p>
      </div>

      <!-- Credentials -->
      <form
        data-test="staff-auth-form"
        class="space-y-4 px-6 py-6"
        @submit.prevent="submit"
      >
        <label class="block text-sm font-medium text-slate-700">
          Username
          <input
            ref="usernameInput"
            v-model="username"
            type="text"
            autocomplete="username"
            placeholder="you@codafriqa.local"
            required
            class="mt-1.5 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-red-400 focus:ring-2 focus:ring-red-100"
          />
        </label>

        <label class="block text-sm font-medium text-slate-700">
          Password
          <div class="relative mt-1.5">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="Enter your password"
              required
              class="w-full rounded-lg border border-slate-300 py-2.5 pr-11 pl-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-red-400 focus:ring-2 focus:ring-red-100"
            />
            <button
              type="button"
              class="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-slate-400 transition hover:text-slate-600"
              :aria-label="showPassword ? 'Hide password' : 'Show password'"
              @click="showPassword = !showPassword"
            >
              <svg
                v-if="!showPassword"
                fill="none"
                viewBox="0 0 24 24"
                stroke-width="1.8"
                stroke="currentColor"
                class="size-4.5"
                aria-hidden="true"
              >
                <path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 0 1 0-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178Z" />
                <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z" />
              </svg>
              <svg
                v-else
                fill="none"
                viewBox="0 0 24 24"
                stroke-width="1.8"
                stroke="currentColor"
                class="size-4.5"
                aria-hidden="true"
              >
                <path stroke-linecap="round" stroke-linejoin="round" d="M3.98 8.223A10.477 10.477 0 0 0 1.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.451 10.451 0 0 1 12 4.5c4.756 0 8.773 3.162 10.065 7.498a10.522 10.522 0 0 1-4.293 5.774M6.228 6.228 3 3m3.228 3.228 3.65 3.65m7.894 7.894L21 21m-3.228-3.228-3.65-3.65m0 0a3 3 0 1 0-4.243-4.243" />
              </svg>
            </button>
          </div>
        </label>

        <p
          v-if="displayError"
          role="alert"
          class="rounded-lg bg-red-50 px-3 py-2.5 text-sm text-red-600 ring-1 ring-inset ring-red-100"
        >
          {{ displayError }}
        </p>

        <button
          type="submit"
          :disabled="loading"
          class="flex w-full items-center justify-center gap-2 rounded-lg bg-red-600 py-2.5 text-sm font-semibold text-white transition enabled:hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <svg
            v-if="loading"
            class="size-4 animate-spin"
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
          >
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          {{ loading ? 'Signing in…' : 'Sign in' }}
        </button>

        <p class="text-center text-xs text-slate-400">
          Demo accounts: admin | agent
        </p>
      </form>
    </div>
  </div>
</template>
