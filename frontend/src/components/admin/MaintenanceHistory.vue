<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useMaintenanceStore } from '../../stores/maintenance'

const props = defineProps({
  /** Tool to show maintenance history for */
  tool: {
    type: Object,
    required: true,
  },
  /** Whether the modal is visible */
  visible: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['close', 'status-changed'])

const maintenanceStore = useMaintenanceStore()

// Form state for new maintenance log
const form = ref({
  serviceDate: new Date().toISOString().split('T')[0],
  description: '',
  cost: '',
  nextServiceDue: '',
})
const formLoading = ref(false)
const formError = ref('')

// Load logs when tool changes or modal opens
watch(
  () => props.visible,
  async (visible) => {
    if (visible && props.tool) {
      await maintenanceStore.fetchMaintenanceLogs(props.tool.id)
      await maintenanceStore.fetchToolStats(props.tool.id)
    }
  },
  { immediate: true },
)

const toolStatus = computed(() => props.tool?.status || 'UNKNOWN')

const statusLabel = {
  AVAILABLE: 'Available',
  BORROWED: 'Borrowed',
  IN_MAINTENANCE: 'In Maintenance',
}

const statusClass = {
  AVAILABLE: 'bg-emerald-100 text-emerald-700',
  BORROWED: 'bg-blue-100 text-blue-700',
  IN_MAINTENANCE: 'bg-amber-100 text-amber-700',
  UNKNOWN: 'bg-slate-100 text-slate-600',
}

function formatDate(d) {
  if (!d) return '—'
  const date = new Date(d)
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleDateString([], { year: 'numeric', month: 'short', day: 'numeric' })
}

function formatCurrency(amount) {
  if (amount == null) return '—'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(amount)
}

async function handleSubmitLog() {
  if (!form.value.description.trim()) {
    formError.value = 'Description is required'
    return
  }
  if (!form.value.serviceDate) {
    formError.value = 'Service date is required'
    return
  }

  formLoading.value = true
  formError.value = ''

  try {
    await maintenanceStore.addMaintenanceLog({
      toolId: props.tool.id,
      serviceDate: form.value.serviceDate,
      description: form.value.description.trim(),
      cost: form.value.cost ? parseFloat(form.value.cost) : null,
      nextServiceDue: form.value.nextServiceDue || null,
    })

    // Reset form
    form.value = {
      serviceDate: new Date().toISOString().split('T')[0],
      description: '',
      cost: '',
      nextServiceDue: '',
    }

    // Refresh tools list to reflect status change
    emit('status-changed')
  } catch (err) {
    formError.value = maintenanceStore.error || 'Failed to add maintenance log'
  } finally {
    formLoading.value = false
  }
}

async function handleCompleteMaintenance() {
  try {
    await maintenanceStore.completeToolMaintenance(props.tool.id)
    emit('status-changed')
  } catch (err) {
    formError.value = maintenanceStore.error || 'Failed to complete maintenance'
  }
}

function handleClose() {
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      @click.self="handleClose"
    >
      <div
        class="w-full max-w-2xl max-h-[90vh] overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl"
        @click.stop
      >
        <!-- Header -->
        <div class="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <div>
            <h2 class="text-lg font-semibold text-slate-800">Maintenance History</h2>
            <p class="mt-0.5 text-sm text-slate-500">
              {{ tool.name }} · Tool #{{ tool.id }}
            </p>
          </div>
          <button
            type="button"
            @click="handleClose"
            class="rounded-full p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
          >
            <svg class="size-5" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <!-- Content -->
        <div class="overflow-y-auto px-6 py-4" style="max-height: calc(90vh - 140px)">
          <!-- Tool Status -->
          <div class="mb-4 flex items-center gap-3">
            <span
              class="rounded-full px-3 py-1 text-xs font-medium"
              :class="statusClass[toolStatus]"
            >
              {{ statusLabel[toolStatus] }}
            </span>
            <span v-if="currentToolStats" class="text-xs text-slate-500">
              {{ currentToolStats.logCount }} service record{{ currentToolStats.logCount === 1 ? '' : 's' }}
            </span>
          </div>

          <!-- Complete Maintenance Button -->
          <div v-if="toolStatus === 'IN_MAINTENANCE'" class="mb-4">
            <button
              type="button"
              @click="handleCompleteMaintenance"
              class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-emerald-700"
            >
              ✓ Complete Maintenance (Restore to Available)
            </button>
          </div>

          <!-- Add New Log Form -->
          <div class="mb-6 rounded-xl border border-slate-200 bg-slate-50 p-4">
            <h3 class="text-sm font-semibold text-slate-800">Add Service Record</h3>
            <form @submit.prevent="handleSubmitLog" class="mt-3 space-y-3">
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="mb-1 block text-xs font-medium text-slate-600">Service Date *</label>
                  <input
                    v-model="form.serviceDate"
                    type="date"
                    required
                    class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-xs font-medium text-slate-600">Cost ($)</label>
                  <input
                    v-model="form.cost"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="0.00"
                    class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
                  />
                </div>
              </div>
              <div>
                <label class="mb-1 block text-xs font-medium text-slate-600">Description *</label>
                <textarea
                  v-model="form.description"
                  rows="2"
                  required
                  placeholder="What service was performed?"
                  class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
                ></textarea>
              </div>
              <div>
                <label class="mb-1 block text-xs font-medium text-slate-600">Next Service Due</label>
                <input
                  v-model="form.nextServiceDue"
                  type="date"
                  class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
                />
              </div>
              <p v-if="formError" class="text-sm text-red-600" role="alert">{{ formError }}</p>
              <button
                type="submit"
                :disabled="formLoading || !form.description.trim()"
                class="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white transition enabled:hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {{ formLoading ? 'Saving...' : 'Add Record' }}
              </button>
            </form>
          </div>

          <!-- Maintenance Logs List -->
          <div>
            <h3 class="mb-3 text-sm font-semibold text-slate-800">Service History</h3>

            <!-- Loading -->
            <div
              v-if="maintenanceStore.isLoading && !maintenanceStore.maintenanceLogs.length"
              class="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-400"
            >
              <svg class="size-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Loading logs...
            </div>

            <!-- Empty state -->
            <div
              v-else-if="maintenanceStore.maintenanceLogs.length === 0"
              class="rounded-xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-400"
            >
              No maintenance records yet.
            </div>

            <!-- Logs list -->
            <div v-else class="space-y-3">
              <div
                v-for="log in maintenanceStore.maintenanceLogs"
                :key="log.id"
                class="rounded-xl border border-slate-200 bg-white p-4"
              >
                <div class="flex items-start justify-between">
                  <div>
                    <p class="text-sm font-medium text-slate-800">{{ log.description }}</p>
                    <p class="mt-1 text-xs text-slate-500">
                      {{ formatDate(log.serviceDate) }}
                      <span v-if="log.cost"> · {{ formatCurrency(log.cost) }}</span>
                    </p>
                  </div>
                  <span class="text-xs text-slate-400">#{{ log.id }}</span>
                </div>
                <p v-if="log.nextServiceDue" class="mt-2 text-xs text-amber-600">
                  ⏰ Next service due: {{ formatDate(log.nextServiceDue) }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="border-t border-slate-200 px-6 py-3">
          <button
            type="button"
            @click="handleClose"
            class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-50"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
