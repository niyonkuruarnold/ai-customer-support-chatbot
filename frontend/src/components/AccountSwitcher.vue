<script setup>
/**
 * AccountSwitcher — compact navbar pill that opens the account menu.
 *
 * The pill shows only a clean display name with an icon ("🔑 Admin"),
 * keeping the header compact; the full email address lives inside the open
 * menu as secondary muted text (and on the pill as a native tooltip).
 *
 * Emits `select` with the chosen account's email.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  /** [{ email, label, role, icon, defaultView }] */
  accounts: { type: Array, required: true },
  /** Email of the currently selected account (drives pill + check mark). */
  selectedEmail: { type: String, default: '' },
})

const emit = defineEmits(['select'])

const open = ref(false)
const root = ref(null)

const selected = computed(
  () =>
    props.accounts.find((account) => account.email === props.selectedEmail) ??
    props.accounts[props.accounts.length - 1],
)

function toggle() {
  open.value = !open.value
}

function choose(email) {
  open.value = false
  emit('select', email)
}

function onDocumentPointer(event) {
  if (root.value && !root.value.contains(event.target)) open.value = false
}

function onKeydown(event) {
  if (event.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointer)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointer)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="root" class="relative">
    <!-- Pill: icon + clean label only -->
    <button
      type="button"
      data-test="account-switcher"
      :title="selected?.email"
      aria-haspopup="menu"
      :aria-expanded="open"
      aria-label="Switch demo account"
      class="flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-2.5 py-1.5 text-[11px] font-medium text-slate-600 shadow-sm outline-none transition hover:border-slate-300 hover:bg-slate-50"
      @click="toggle"
    >
      <span aria-hidden="true">{{ selected?.icon }}</span>
      <span>{{ selected?.label }}</span>
      <svg
        fill="none"
        viewBox="0 0 24 24"
        stroke-width="2.5"
        stroke="currentColor"
        class="size-3 text-slate-400 transition-transform"
        :class="{ 'rotate-180': open }"
        aria-hidden="true"
      >
        <path stroke-linecap="round" stroke-linejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
      </svg>
    </button>

    <!-- Menu: label + muted email as secondary text -->
    <div
      v-if="open"
      data-test="account-menu"
      role="menu"
      class="absolute right-0 z-50 mt-2 w-60 overflow-hidden rounded-xl border border-slate-200 bg-white py-1 shadow-xl"
    >
      <button
        v-for="account in accounts"
        :key="account.email"
        type="button"
        role="menuitem"
        data-test="account-option"
        :data-value="account.email"
        class="flex w-full items-center gap-3 px-3 py-2 text-left transition hover:bg-slate-50"
        :class="account.email === selectedEmail ? 'bg-red-50/60' : ''"
        @click="choose(account.email)"
      >
        <span class="text-base" aria-hidden="true">{{ account.icon }}</span>
        <span class="min-w-0 flex-1">
          <span class="block text-sm font-medium text-slate-700">{{ account.label }}</span>
          <span class="block truncate text-xs text-slate-400">{{ account.email }}</span>
        </span>
        <svg
          v-if="account.email === selectedEmail"
          fill="none"
          viewBox="0 0 24 24"
          stroke-width="2.5"
          stroke="currentColor"
          class="size-4 shrink-0 text-red-600"
          aria-hidden="true"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5" />
        </svg>
      </button>
    </div>
  </div>
</template>
