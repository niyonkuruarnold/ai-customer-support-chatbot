<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  /** Minimum selectable date (today by default) */
  minDate: {
    type: String,
    default: () => new Date().toISOString().split('T')[0],
  },
  /** Whether the picker is disabled */
  disabled: Boolean,
})

const emit = defineEmits(['update:startDate', 'update:endDate', 'change'])

const startDate = ref('')
const endDate = ref('')

const minEnd = computed(() => {
  if (startDate.value) return startDate.value
  return props.minDate
})

const today = computed(() => props.minDate)

watch(startDate, (val) => {
  emit('update:startDate', val)
  // Reset end date if it's now before the new start
  if (endDate.value && val && endDate.value < val) {
    endDate.value = val
    emit('update:endDate', val)
  }
  if (val && endDate.value) {
    emit('change', { startDate: val, endDate: endDate.value })
  }
})

watch(endDate, (val) => {
  emit('update:endDate', val)
  if (startDate.value && val) {
    emit('change', { startDate: startDate.value, endDate: val })
  }
})

function clear() {
  startDate.value = ''
  endDate.value = ''
  emit('update:startDate', '')
  emit('update:endDate', '')
  emit('change', null)
}

defineExpose({ clear })
</script>

<template>
  <div class="space-y-3">
    <label class="block text-sm font-medium text-slate-700">
      Reservation dates
    </label>
    <div class="flex items-center gap-3">
      <div class="flex-1">
        <label class="mb-1 block text-xs text-slate-500">From</label>
        <input
          v-model="startDate"
          type="date"
          :min="today"
          :disabled="disabled"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100 disabled:cursor-not-allowed disabled:opacity-50"
        />
      </div>
      <span class="mt-5 text-slate-400">→</span>
      <div class="flex-1">
        <label class="mb-1 block text-xs text-slate-500">To</label>
        <input
          v-model="endDate"
          type="date"
          :min="minEnd"
          :disabled="disabled || !startDate"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100 disabled:cursor-not-allowed disabled:opacity-50"
        />
      </div>
      <button
        v-if="startDate || endDate"
        type="button"
        @click="clear"
        :disabled="disabled"
        class="mt-5 rounded-full border border-slate-200 px-2 py-1 text-xs text-slate-400 transition hover:border-red-300 hover:text-red-500 disabled:opacity-40"
      >
        ✕
      </button>
    </div>
    <p v-if="startDate && endDate" class="text-xs text-slate-500">
      {{ Math.ceil((new Date(endDate) - new Date(startDate)) / 86400000) + 1 }} day(s) selected
    </p>
  </div>
</template>
