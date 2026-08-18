<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const status = ref(null)
const loading = ref(true)
const error = ref(false)
let timer = null

const POLL_MS = 30_000

function timeAgo(dateString) {
  if (!dateString) return null
  const diff = Date.now() - new Date(dateString).getTime()
  if (diff < 0) return 'just now'
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

async function fetchStatus() {
  try {
    const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
    const res = await fetch(`${base}/v1/rag/sync-status`)
    if (!res.ok) throw new Error(res.statusText)
    status.value = await res.json()
    error.value = false
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchStatus()
  timer = setInterval(fetchStatus, POLL_MS)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div
    v-if="!loading"
    class="inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-medium transition"
    :class="
      error
        ? 'border-slate-200 bg-slate-50 text-slate-400'
        : status?.status === 'COMPLETED'
          ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
          : status?.status === 'RUNNING'
            ? 'border-amber-200 bg-amber-50 text-amber-700'
            : 'border-slate-200 bg-slate-50 text-slate-500'
    "
    :title="status?.message || 'System sync status'"
  >
    <!-- Status dot -->
    <span class="relative flex size-1.5 shrink-0" aria-hidden="true">
      <span
        v-if="status?.status === 'COMPLETED'"
        class="absolute inline-flex size-full rounded-full bg-emerald-400 opacity-75"
      ></span>
      <span
        v-if="status?.status === 'RUNNING'"
        class="absolute inline-flex size-full animate-ping rounded-full bg-amber-400 opacity-75"
      ></span>
      <span
        class="relative inline-flex size-1.5 rounded-full"
        :class="
          status?.status === 'COMPLETED'
            ? 'bg-emerald-500'
            : status?.status === 'RUNNING'
              ? 'bg-amber-500'
              : 'bg-slate-400'
        "
      ></span>
    </span>

    <!-- Label -->
    <span v-if="error">Sync unavailable</span>
    <span v-else-if="status?.status === 'COMPLETED' && status?.timestamp">
      System Synced · Last scan {{ timeAgo(status.timestamp) }}
    </span>
    <span v-else-if="status?.status === 'RUNNING'">Syncing…</span>
    <span v-else-if="status?.status === 'NO_SCANS'">No scans yet</span>
    <span v-else>System Synced</span>
  </div>
</template>
