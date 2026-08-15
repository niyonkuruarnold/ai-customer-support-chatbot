<script setup>
defineProps({
  label: { type: String, default: 'Assistant is typing' },
})
</script>

<template>
  <div class="flex items-start gap-3" role="status" :aria-label="label">
    <div
      class="flex size-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-lg text-white shadow-sm"
      aria-hidden="true"
    >
      🤖
    </div>
    <div
      class="rounded-2xl rounded-bl-md border border-slate-200 bg-white px-4 py-3.5 shadow-sm"
    >
      <div class="flex items-center gap-1.5">
        <span
          class="size-2 animate-bounce rounded-full bg-slate-400 [animation-delay:-0.3s]"
        ></span>
        <span
          class="size-2 animate-bounce rounded-full bg-slate-400 [animation-delay:-0.15s]"
        ></span>
        <span class="size-2 animate-bounce rounded-full bg-slate-400"></span>
      </div>
      <span class="sr-only">{{ label }}</span>
    </div>
  </div>
</template>
