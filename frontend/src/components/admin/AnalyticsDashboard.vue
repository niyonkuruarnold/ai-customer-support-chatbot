<script setup>
import { ref, onMounted, computed } from 'vue'
import { Chart as ChartJS, ArcElement, Tooltip, Legend, CategoryScale, LinearScale, PointElement, LineElement, BarElement, Filler } from 'chart.js'
import { Doughnut, Line, Bar } from 'vue-chartjs'
import { getOperationalMetrics, getDailyTrend, exportAnalyticsCsv, exportAnalyticsPdf } from '../../api/admin'

ChartJS.register(ArcElement, Tooltip, Legend, CategoryScale, LinearScale, PointElement, LineElement, BarElement, Filler)

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

// ── Computed KPI values ────────────────────────────────────────────
const aiContainmentRate = computed(() =>
  metrics.value ? Math.round(metrics.value.aiContainmentRate * 100) / 100 : 0
)
const humanEscalationRate = computed(() =>
  metrics.value ? Math.round(metrics.value.humanEscalationRate * 100) / 100 : 0
)
const avgResponseTime = computed(() => {
  if (!metrics.value) return '0 min'
  const minutes = Math.round(metrics.value.averageFirstResponseTimeMinutes * 10) / 10
  return `${minutes} min`
})
const csatScore = computed(() =>
  metrics.value && metrics.value.averageCsatRating != null
    ? Math.round(metrics.value.averageCsatRating * 10) / 10
    : 'N/A'
)
const totalConversations = computed(() => metrics.value?.totalConversations ?? 0)

// ── Chart.js: Donut — AI Containment vs Escalation ─────────────────
const donutData = computed(() => ({
  labels: ['AI Handled', 'Human Escalated'],
  datasets: [{
    data: [
      metrics.value ? metrics.value.aiResolvedConversations : 0,
      metrics.value ? metrics.value.escalatedConversations : 0,
    ],
    backgroundColor: ['#10B981', '#F59E0B'],
    borderWidth: 0,
    hoverOffset: 6,
  }],
}))
const donutOptions = {
  responsive: true,
  maintainAspectRatio: false,
  cutout: '65%',
  plugins: {
    legend: { position: 'bottom', labels: { padding: 16, usePointStyle: true, pointStyleWidth: 8 } },
    tooltip: {
      callbacks: {
        label: (ctx) => ` ${ctx.label}: ${ctx.raw} sessions`,
      },
    },
  },
}

// ── Chart.js: Line — Conversation Volume Trend ─────────────────────
const lineData = computed(() => ({
  labels: trendData.value.map((d) => d.date?.slice(5) ?? d.date),
  datasets: [
    {
      label: 'Total Sessions',
      data: trendData.value.map((d) => d.totalSessions),
      borderColor: '#4F46E5',
      backgroundColor: 'rgba(79, 70, 229, 0.08)',
      fill: true,
      tension: 0.35,
      pointRadius: 3,
      pointHoverRadius: 5,
    },
    {
      label: 'Escalated',
      data: trendData.value.map((d) => d.escalatedSessions),
      borderColor: '#F59E0B',
      backgroundColor: 'rgba(245, 158, 11, 0.08)',
      fill: true,
      tension: 0.35,
      pointRadius: 3,
      pointHoverRadius: 5,
    },
  ],
}))
const lineOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: { intersect: false, mode: 'index' },
  scales: {
    y: { beginAtZero: true, ticks: { stepSize: 1 } },
    x: { grid: { display: false } },
  },
  plugins: {
    legend: { position: 'bottom', labels: { padding: 16, usePointStyle: true, pointStyleWidth: 8 } },
  },
}

// ── Chart.js: Bar — CSAT Distribution (1–5 stars) ──────────────────
// Since the backend returns avgCsatRating but not a distribution, we
// show tickets by status as a bar chart instead, plus a fallback.
const barData = computed(() => {
  if (!metrics.value?.ticketsByStatus) return { labels: [], datasets: [] }
  const entries = Object.entries(metrics.value.ticketsByStatus)
  return {
    labels: entries.map(([k]) => k.replace('_', ' ')),
    datasets: [{
      label: 'Tickets',
      data: entries.map(([, v]) => v),
      backgroundColor: [
        '#3B82F6', '#6366F1', '#F59E0B', '#8B5CF6',
        '#EC4899', '#10B981', '#6B7280', '#F97316',
      ],
      borderRadius: 6,
      maxBarThickness: 40,
    }],
  }
})
const barOptions = {
  responsive: true,
  maintainAspectRatio: false,
  scales: {
    y: { beginAtZero: true, ticks: { stepSize: 1 } },
    x: { grid: { display: false } },
  },
  plugins: {
    legend: { display: false },
  },
}

// ── Export handlers ────────────────────────────────────────────────
function handleExportCsv() {
  exportAnalyticsCsv(dateRange.value).catch(() => { error.value = 'Failed to export CSV' })
}
function handleExportPdf() {
  exportAnalyticsPdf(dateRange.value).catch(() => { error.value = 'Failed to export PDF' })
}

// ── Data loading ──────────────────────────────────────────────────
async function loadMetrics() {
  loading.value = true
  error.value = ''
  try {
    const [metricsData, trend] = await Promise.all([
      getOperationalMetrics(dateRange.value),
      getDailyTrend(dateRange.value),
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
    <div class="mb-6 flex flex-wrap items-center justify-between gap-4">
      <div>
        <h2 class="text-xl font-bold text-slate-800">Analytics Dashboard</h2>
        <p class="text-sm text-slate-500">Service performance metrics and insights</p>
      </div>
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex items-center gap-2">
          <input v-model="dateRange.startDate" type="date"
            class="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            @change="loadMetrics" />
          <span class="text-slate-400">to</span>
          <input v-model="dateRange.endDate" type="date"
            class="rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            @change="loadMetrics" />
        </div>
        <button @click="handleExportCsv"
          class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-emerald-700">
          📊 Export CSV
        </button>
        <button @click="handleExportPdf"
          class="inline-flex items-center gap-1.5 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700">
          📄 Export PDF
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-16">
      <svg class="size-6 animate-spin text-indigo-500" viewBox="0 0 24 24" fill="none">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      <span class="ml-3 text-sm text-slate-500">Loading analytics…</span>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
      {{ error }}
      <button @click="loadMetrics" class="ml-2 underline hover:text-red-900">Retry</button>
    </div>

    <!-- Dashboard -->
    <template v-else-if="metrics">
      <!-- ── KPI Cards ─────────────────────────────────────────────── -->
      <div class="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <!-- AI Containment Rate -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-500">AI Containment Rate</p>
              <p class="mt-1 text-3xl font-bold text-emerald-600">{{ aiContainmentRate }}%</p>
            </div>
            <div class="flex size-12 items-center justify-center rounded-full bg-emerald-100 text-2xl">🤖</div>
          </div>
          <p class="mt-2 text-xs text-slate-400">
            {{ metrics.aiResolvedConversations }} of {{ totalConversations }} sessions handled by AI
          </p>
        </div>

        <!-- Average CSAT -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-500">Avg CSAT Score</p>
              <p class="mt-1 text-3xl font-bold text-purple-600">{{ csatScore }}<span class="text-lg">/5</span></p>
            </div>
            <div class="flex size-12 items-center justify-center rounded-full bg-purple-100 text-2xl">⭐</div>
          </div>
          <p class="mt-2 text-xs text-slate-400">Average customer satisfaction rating</p>
        </div>

        <!-- Total Conversations -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-500">Total Conversations</p>
              <p class="mt-1 text-3xl font-bold text-blue-600">{{ totalConversations }}</p>
            </div>
            <div class="flex size-12 items-center justify-center rounded-full bg-blue-100 text-2xl">💬</div>
          </div>
          <p class="mt-2 text-xs text-slate-400">
            {{ metrics.escalatedConversations }} escalated to human agents
          </p>
        </div>

        <!-- Avg First Response Time -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-500">Avg Response Time</p>
              <p class="mt-1 text-3xl font-bold text-amber-600">{{ avgResponseTime }}</p>
            </div>
            <div class="flex size-12 items-center justify-center rounded-full bg-amber-100 text-2xl">⚡</div>
          </div>
          <p class="mt-2 text-xs text-slate-400">First response time in minutes</p>
        </div>
      </div>

      <!-- ── Charts Row ────────────────────────────────────────────── -->
      <div class="mb-8 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <!-- Donut: AI vs Human -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-4 text-sm font-semibold text-slate-700">AI vs Human Handling</h3>
          <div class="h-56">
            <Doughnut v-if="totalConversations > 0" :data="donutData" :options="donutOptions" />
            <div v-else class="flex h-full items-center justify-center text-sm text-slate-400">No data</div>
          </div>
        </div>

        <!-- Line: Volume Trend -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm lg:col-span-2">
          <h3 class="mb-4 text-sm font-semibold text-slate-700">Conversation Volume Trend</h3>
          <div class="h-56">
            <Line v-if="trendData.length" :data="lineData" :options="lineOptions" />
            <div v-else class="flex h-full items-center justify-center text-sm text-slate-400">No trend data</div>
          </div>
        </div>
      </div>

      <!-- ── Bar: Ticket Status Distribution ───────────────────────── -->
      <div class="mb-8 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <h3 class="mb-4 text-sm font-semibold text-slate-700">Ticket Status Distribution</h3>
        <div class="h-64">
          <Bar v-if="metrics.ticketsByStatus && Object.keys(metrics.ticketsByStatus).length" :data="barData" :options="barOptions" />
          <div v-else class="flex h-full items-center justify-center text-sm text-slate-400">No ticket data</div>
        </div>
      </div>

      <!-- ── Summary Cards Row ─────────────────────────────────────── -->
      <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <!-- Session Summary -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-4 text-sm font-semibold text-slate-700">Session Summary</h3>
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-500">Total Sessions</span>
              <span class="font-semibold text-slate-800">{{ totalConversations }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-500">Escalated</span>
              <span class="font-semibold text-amber-600">{{ metrics.escalatedConversations }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-500">AI Resolved</span>
              <span class="font-semibold text-emerald-600">{{ metrics.aiResolvedConversations }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-500">Escalation Rate</span>
              <span class="font-semibold text-amber-600">{{ humanEscalationRate }}%</span>
            </div>
          </div>
        </div>

        <!-- Ticket Summary -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-4 text-sm font-semibold text-slate-700">Ticket Summary</h3>
          <div class="space-y-3">
            <div v-for="(count, status) in (metrics.ticketsByStatus || {})" :key="status"
              class="flex items-center justify-between">
              <span class="text-sm text-slate-500">{{ status.replace('_', ' ') }}</span>
              <span class="font-semibold text-slate-800">{{ count }}</span>
            </div>
          </div>
        </div>

        <!-- Priority Distribution -->
        <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 class="mb-4 text-sm font-semibold text-slate-700">Priority Distribution</h3>
          <div class="space-y-3">
            <div v-for="(count, priority) in (metrics.ticketsByPriority || {})" :key="priority"
              class="flex items-center justify-between">
              <span class="text-sm text-slate-500">{{ priority }}</span>
              <div class="flex items-center gap-2">
                <div class="h-2 w-20 overflow-hidden rounded-full bg-slate-200">
                  <div class="h-full rounded-full"
                    :class="{
                      'bg-slate-400': priority === 'LOW',
                      'bg-blue-500': priority === 'MEDIUM',
                      'bg-orange-500': priority === 'HIGH',
                      'bg-red-500': priority === 'URGENT',
                    }"
                    :style="{ width: `${totalConversations > 0 ? (count / totalConversations) * 100 : 0}%` }">
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
@reference "../../style.css";
.analytics-dashboard {
  @apply p-6;
}
</style>
