<script setup>
import { ref, onMounted, computed } from 'vue'
import { 
  getDashboardMetrics, 
  getDailyTrend, 
  exportTicketsCsv, 
  exportTicketsPdf 
} from '../../api/analytics'

const props = defineProps({
  embedded: { type: Boolean, default: false },
})

const loading = ref(false)
const error = ref('')
const metrics = ref(null)
const trendData = ref([])

// Date range filter
const dateRange = ref({
  startDate: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
  endDate: new Date().toISOString().split('T')[0],
})

// Computed metrics for display
const aiContainmentRate = computed(() => 
  metrics.value ? Math.round(metrics.value.aiContainmentRate * 100) / 100 : 0
)

const humanEscalationRate = computed(() => 
  metrics.value ? Math.round(metrics.value.humanEscalationRate * 100) / 100 : 0
)

const avgResponseTime = computed(() => {
  if (!metrics.value) return '0s'
  const seconds = Math.round(metrics.value.avgFirstResponseTimeSeconds)
  if (seconds < 60) return `${seconds}s`
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
})

const csatScore = computed(() => 
  metrics.value ? Math.round(metrics.value.csatScore * 10) / 10 : 0
)

// Chart data computed properties
const sessionChartData = computed(() => {
  if (!trendData.value.length) return { labels: [], datasets: [] }
  return {
    labels: trendData.value.map(d => d.date),
    datasets: [
      {
        label: 'Total Sessions',
        data: trendData.value.map(d => d.totalSessions),
        borderColor: '#4F46E5',
        backgroundColor: 'rgba(79, 70, 229, 0.1)',
        fill: true,
      },
      {
        label: 'Escalated',
        data: trendData.value.map(d => d.escalatedSessions),
        borderColor: '#F59E0B',
        backgroundColor: 'rgba(245, 158, 11, 0.1)',
        fill: true,
      }
    ]
  }
})

const containmentChartData = computed(() => {
  if (!metrics.value) return { labels: [], datasets: [] }
  return {
    labels: ['AI Handled', 'Human Escalated'],
    datasets: [{
      data: [
        metrics.value.totalSessions - metrics.value.escalatedSessions,
        metrics.value.escalatedSessions
      ],
      backgroundColor: ['#10B981', '#F59E0B']
    }]
  }
})

const ticketStatusChartData = computed(() => {
  if (!metrics.value?.ticketsByStatus) return { labels: [], datasets: [] }
  const entries = Object.entries(metrics.value.ticketsByStatus)
  return {
    labels: entries.map(([k]) => k),
    datasets: [{
      data: entries.map(([, v]) => v),
      backgroundColor: ['#3B82F6', '#6366F1', '#F59E0B', '#8B5CF6', '#EC4899', '#10B981', '#6B7280', '#F97316']
    }]
  }
})

// Export functions
async function handleExportCsv() {
  try {
    await exportTicketsCsv(dateRange.value)
  } catch (err) {
    error.value = 'Failed to export CSV'
  }
}

async function handleExportPdf() {
  try {
    await exportTicketsPdf(dateRange.value)
  } catch (err) {
    error.value = 'Failed to export PDF'
  }
}

// Load data
async function loadMetrics() {
  loading.value = true
  error.value = ''
  try {
    const [metricsData, trend] = await Promise.all([
      getDashboardMetrics(dateRange.value),
      getDailyTrend(dateRange.value)
    ])
    metrics.value = metricsData
    trendData.value = trend
  } catch (err) {
    error.value = 'Failed to load analytics data'
    console.error(err)
  } finally {
    loading.value = false
  }
}

onMounted(loadMetrics)
</script>

<template>
  <div class="analytics-dashboard">
    <!-- Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h2 class="text-xl font-bold text-slate-800">Analytics Dashboard</h2>
        <p class="text-sm text-slate-500">Service metrics and performance insights</p>
      </div>
      <div class="flex items-center gap-3">
        <!-- Date Range Picker -->
        <div class="flex items-center gap-2">
          <input
            v-model="dateRange.startDate"
            type="date"
            class="rounded-lg border border-slate-300 px-3 py-2 text-sm"
            @change="loadMetrics"
          />
          <span class="text-slate-400">to</span>
          <input
            v-model="dateRange.endDate"
            type="date"
            class="rounded-lg border border-slate-300 px-3 py-2 text-sm"
            @change="loadMetrics"
          />
        </div>
        <!-- Export Buttons -->
        <button
          @click="handleExportCsv"
          class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700"
        >
          📊 Export CSV
        </button>
        <button
          @click="handleExportPdf"
          class="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700"
        >
          📄 Export PDF
        </button>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="text-slate-500">Loading analytics...</div>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="rounded-lg bg-red-50 p-4 text-red-700">
      {{ error }}
    </div>

    <!-- Dashboard Content -->
    <template v-else-if="metrics">
      <!-- Key Metrics Cards -->
      <div class="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <!-- AI Containment Rate -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-500">AI Containment Rate</p>
              <p class="mt-1 text-3xl font-bold text-emerald-600">{{ aiContainmentRate }}%</p>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100">
              <span class="text-2xl">🤖</span>
            </div>
          </div>
          <p class="mt-2 text-xs text-slate-500">
            {{ metrics.totalSessions - metrics.escalatedSessions }} of {{ metrics.totalSessions }} sessions handled by AI
          </p>
        </div>

        <!-- Human Escalation Rate -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-500">Escalation Rate</p>
              <p class="mt-1 text-3xl font-bold text-amber-600">{{ humanEscalationRate }}%</p>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-amber-100">
              <span class="text-2xl">👥</span>
            </div>
          </div>
          <p class="mt-2 text-xs text-slate-500">
            {{ metrics.escalatedSessions }} sessions escalated to human agents
          </p>
        </div>

        <!-- Average Response Time -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-500">Avg Response Time</p>
              <p class="mt-1 text-3xl font-bold text-blue-600">{{ avgResponseTime }}</p>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-blue-100">
              <span class="text-2xl">⚡</span>
            </div>
          </div>
          <p class="mt-2 text-xs text-slate-500">
            Time from first message to AI response
          </p>
        </div>

        <!-- CSAT Score -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-500">CSAT Score</p>
              <p class="mt-1 text-3xl font-bold text-purple-600">{{ csatScore }}/5</p>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-purple-100">
              <span class="text-2xl">⭐</span>
            </div>
          </div>
          <p class="mt-2 text-xs text-slate-500">
            Average customer satisfaction rating
          </p>
        </div>
      </div>

      <!-- Charts Section -->
      <div class="mb-8 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <!-- Session Trend Chart -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-4 text-lg font-semibold text-slate-800">Session Trend</h3>
          <div class="h-64">
            <div class="flex h-full items-center justify-center text-slate-400">
              <!-- Placeholder for Chart.js / Recharts -->
              <div class="text-center">
                <p class="text-sm">Chart visualization</p>
                <p class="mt-1 text-xs">Sessions over time</p>
                <div class="mt-4 flex justify-center gap-4 text-xs">
                  <span class="flex items-center gap-1">
                    <span class="h-2 w-2 rounded-full bg-indigo-500"></span> Total
                  </span>
                  <span class="flex items-center gap-1">
                    <span class="h-2 w-2 rounded-full bg-amber-500"></span> Escalated
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Containment Pie Chart -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-4 text-lg font-semibold text-slate-800">AI vs Human Handling</h3>
          <div class="h-64">
            <div class="flex h-full items-center justify-center text-slate-400">
              <div class="text-center">
                <div class="mx-auto mb-4 flex h-32 w-32 items-center justify-center rounded-full border-8 border-emerald-500 bg-amber-500">
                  <span class="text-2xl font-bold text-white">{{ aiContainmentRate }}%</span>
                </div>
                <p class="text-sm">AI Containment Rate</p>
                <div class="mt-4 flex justify-center gap-4 text-xs">
                  <span class="flex items-center gap-1">
                    <span class="h-2 w-2 rounded-full bg-emerald-500"></span> AI Handled
                  </span>
                  <span class="flex items-center gap-1">
                    <span class="h-2 w-2 rounded-full bg-amber-500"></span> Escalated
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Ticket Status Distribution -->
      <div class="mb-8 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <h3 class="mb-4 text-lg font-semibold text-slate-800">Ticket Status Distribution</h3>
        <div class="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-8">
          <div v-for="(count, status) in metrics.ticketsByStatus" :key="status" 
               class="rounded-lg bg-slate-50 p-4 text-center">
            <p class="text-2xl font-bold text-slate-800">{{ count }}</p>
            <p class="mt-1 text-xs text-slate-500">{{ status.replace('_', ' ') }}</p>
          </div>
        </div>
      </div>

      <!-- Additional Stats -->
      <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <!-- Ticket Summary -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-4 text-lg font-semibold text-slate-800">Ticket Summary</h3>
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">Total Tickets</span>
              <span class="font-semibold text-slate-800">{{ metrics.totalTickets }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">Open Tickets</span>
              <span class="font-semibold text-amber-600">{{ metrics.openTickets }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">Resolved Tickets</span>
              <span class="font-semibold text-emerald-600">{{ metrics.resolvedTickets }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">Closed Tickets</span>
              <span class="font-semibold text-slate-600">{{ metrics.closedTickets }}</span>
            </div>
          </div>
        </div>

        <!-- Session Summary -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-4 text-lg font-semibold text-slate-800">Session Summary</h3>
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">Total Sessions</span>
              <span class="font-semibold text-slate-800">{{ metrics.totalSessions }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">Escalated Sessions</span>
              <span class="font-semibold text-amber-600">{{ metrics.escalatedSessions }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">Closed Sessions</span>
              <span class="font-semibold text-emerald-600">{{ metrics.closedSessions }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">Resolution Rate</span>
              <span class="font-semibold text-blue-600">
                {{ metrics.totalSessions > 0 ? Math.round((metrics.closedSessions / metrics.totalSessions) * 100) : 0 }}%
              </span>
            </div>
          </div>
        </div>

        <!-- Priority Distribution -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-4 text-lg font-semibold text-slate-800">Priority Distribution</h3>
          <div class="space-y-3">
            <div v-for="(count, priority) in metrics.ticketsByPriority" :key="priority" 
                 class="flex items-center justify-between">
              <span class="text-sm text-slate-600">{{ priority }}</span>
              <div class="flex items-center gap-2">
                <div class="h-2 w-24 overflow-hidden rounded-full bg-slate-200">
                  <div class="h-full rounded-full" 
                       :class="{
                         'bg-slate-400': priority === 'LOW',
                         'bg-blue-500': priority === 'MEDIUM',
                         'bg-orange-500': priority === 'HIGH',
                         'bg-red-500': priority === 'URGENT'
                       }"
                       :style="{ width: `${metrics.totalTickets > 0 ? (count / metrics.totalTickets) * 100 : 0}%` }">
                  </div>
                </div>
                <span class="w-8 text-right text-sm font-semibold text-slate-800">{{ count }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.analytics-dashboard {
  @apply p-6;
}
</style>
