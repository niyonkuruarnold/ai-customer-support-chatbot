import { useEffect, useMemo, useState } from 'react'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import './App.css'
import { mockDashboard } from './mockDashboard'
import type { AlertItem, ChatReply, ChatMessage, DashboardData, KnowledgeItem, TicketSummary } from './types'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:4000'
const timeFormatter = new Intl.DateTimeFormat([], { hour: '2-digit', minute: '2-digit' })
const channelPalette = ['#111827', '#334155', '#64748b', '#cbd5e1']
let messageCounter = 0

const quickPromptMap: Record<string, string> = {
  'Reset a locked account': 'I cannot sign in and the account recovery email is not arriving. Please help unlock it.',
  'Review a duplicate charge': 'Our workspace was billed twice and finance needs a correction today.',
  'Trace a delayed shipment': 'The replacement order has not moved for days and I need a shipping trace started.',
  'Launch VIP rescue': 'We are a VIP customer and a service problem is blocking our team right now.',
}

const viewOptions = [
  { id: 'overview', label: 'Overview' },
  { id: 'live', label: 'Live Desk' },
  { id: 'knowledge', label: 'Knowledge Lab' },
] as const

const queueFilters = [
  { id: 'all', label: 'All queue' },
  { id: 'risk', label: 'SLA risk' },
  { id: 'billing', label: 'Billing' },
  { id: 'vip', label: 'VIP / urgent' },
] as const

const briefModes = [
  { id: 'leadership', label: 'Leadership' },
  { id: 'handoff', label: 'Handoff' },
  { id: 'customer', label: 'Customer' },
] as const

type WorkspaceView = (typeof viewOptions)[number]['id']
type QueueFilter = (typeof queueFilters)[number]['id']
type BriefMode = (typeof briefModes)[number]['id']

function createMessageId(prefix: string) {
  messageCounter += 1
  return `${prefix}-${messageCounter}`
}

function createTimestamp() {
  return timeFormatter.format(new Date())
}

async function fetchJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`)
  if (!response.ok) {
    throw new Error(`Request failed: ${path}`)
  }

  return (await response.json()) as T
}

function tokenize(text: string) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter((token) => token.length > 2)
}

function rankKnowledge(message: string, knowledgeBase: KnowledgeItem[]) {
  const tokens = tokenize(message)

  return knowledgeBase
    .map((item) => {
      const haystack = `${item.title} ${item.summary} ${item.content} ${item.tags.join(' ')}`.toLowerCase()
      const score = tokens.reduce((total, token) => total + (haystack.includes(token) ? 1 : 0), 0)
      return { item, score }
    })
    .sort((left, right) => right.score - left.score || right.item.trustScore - left.item.trustScore)
    .filter((entry) => entry.score > 0)
    .slice(0, 3)
    .map((entry) => entry.item)
}

function buildLocalReply(message: string, knowledgeBase: KnowledgeItem[]): ChatReply {
  const normalized = message.toLowerCase()
  const matchedKnowledge = rankKnowledge(message, knowledgeBase)
  const escalated = /(refund|charged|vip|outage|cancel|late|lawyer|finance|fraud)/.test(normalized)
  const assistantMessage: ChatMessage = {
    id: createMessageId('local'),
    role: 'assistant',
    author: 'Ari Copilot',
    content: escalated
      ? 'I prepared a cleaner executive handoff with urgency, likely cause, and the safest next action so a human specialist can move quickly.'
      : 'I found the strongest matching playbook and drafted a calm, trusted resolution path the team can send with minimal editing.',
    timestamp: createTimestamp(),
  }

  return {
    message: assistantMessage,
    confidence: escalated ? 76 : matchedKnowledge.length > 0 ? 92 : 64,
    matchedKnowledge,
    suggestedActions: escalated
      ? ['Prepare executive handoff', 'Open specialist review', 'Flag manager attention']
      : ['Send trusted reply', 'Summarize case', 'Confirm resolution'],
    escalated,
    escalationReason: escalated
      ? 'Offline resilience mode triggered a safe human handoff because the request includes sensitive risk language or urgency.'
      : undefined,
    ticket: escalated
      ? {
          id: `T-${2600 + messageCounter}`,
          customer: 'Live customer',
          topic: message.slice(0, 38) + (message.length > 38 ? '...' : ''),
          category: /(refund|charged|invoice|finance)/.test(normalized) ? 'Billing' : 'Priority support',
          status: 'Escalated',
          priority: 'High',
          sentiment: 'Concerned',
          eta: '22 min',
          updatedAt: 'just now',
          slaRisk: true,
        }
      : undefined,
  }
}

function formatShortList(items: string[]) {
  if (items.length === 0) {
    return 'No linked actions yet.'
  }

  if (items.length === 1) {
    return items[0]
  }

  return `${items.slice(0, -1).join(', ')} and ${items[items.length - 1]}`
}

function buildExecutiveBrief(
  ticket: TicketSummary | undefined,
  reply: ChatReply | null,
  alerts: AlertItem[],
  briefMode: BriefMode,
) {
  if (!ticket) {
    return 'Select a case to generate a leadership-ready brief.'
  }

  const topAlert = alerts.find((alert) => alert.severity === 'critical') ?? alerts[0]
  const actions = reply?.suggestedActions ?? ['Confirm customer impact', 'Align owner', 'Close the loop']
  const knowledge = reply?.matchedKnowledge.map((item) => item.title) ?? []

  if (briefMode === 'customer') {
    return [
      `Hi ${ticket.customer},`,
      '',
      `We are actively reviewing your ${ticket.category.toLowerCase()} issue: ${ticket.topic}.`,
      `The case is currently marked ${ticket.status.toLowerCase()} with an estimated next update in ${ticket.eta}.`,
      `Our team is prioritizing the safest path forward and the next step is to ${actions[0].toLowerCase()}.`,
      '',
      'We will share another update as soon as the change is confirmed.',
    ].join('\n')
  }

  if (briefMode === 'handoff') {
    return [
      `Case: ${ticket.id} | ${ticket.customer}`,
      `Topic: ${ticket.topic}`,
      `Priority: ${ticket.priority} | Status: ${ticket.status} | ETA: ${ticket.eta}`,
      `Sentiment: ${ticket.sentiment} | SLA risk: ${ticket.slaRisk ? 'Yes' : 'No'}`,
      '',
      `Recommended next move: ${actions[0]}.`,
      `Escalation context: ${reply?.escalationReason ?? 'No forced escalation. Keep response polished and monitor closely.'}`,
      `Reference material: ${formatShortList(knowledge)}`,
      `Ops watch item: ${topAlert?.title ?? 'General queue monitoring'}`,
    ].join('\n')
  }

  return [
    `Executive brief for ${ticket.customer}`,
    '',
    `${ticket.category} ticket ${ticket.id} is currently ${ticket.status.toLowerCase()} with ${ticket.priority.toLowerCase()} priority and a next response target of ${ticket.eta}.`,
    `Customer sentiment is ${ticket.sentiment.toLowerCase()}${ticket.slaRisk ? ', and the case is on SLA watch.' : '.'}`,
    `The recommended action is to ${actions[0].toLowerCase()}, followed by ${actions[1]?.toLowerCase() ?? 'closing the loop clearly'}.`,
    `The strongest support evidence comes from ${formatShortList(knowledge)}.`,
    `Leadership note: ${topAlert?.title ?? 'Queue posture is stable'}${topAlert ? ` - ${topAlert.description}` : '.'}`,
  ].join('\n')
}

function App() {
  const [dashboard, setDashboard] = useState<DashboardData>(mockDashboard)
  const [composer, setComposer] = useState('')
  const [knowledgeQuery, setKnowledgeQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSending, setIsSending] = useState(false)
  const [connectionLabel, setConnectionLabel] = useState('Synchronizing command center...')
  const [assistantReply, setAssistantReply] = useState<ChatReply | null>(null)
  const [activeView, setActiveView] = useState<WorkspaceView>('overview')
  const [queueFilter, setQueueFilter] = useState<QueueFilter>('all')
  const [briefMode, setBriefMode] = useState<BriefMode>('leadership')
  const [focusMode, setFocusMode] = useState(false)
  const [compactMode, setCompactMode] = useState(false)
  const [selectedTicketId, setSelectedTicketId] = useState(mockDashboard.tickets[0]?.id ?? '')
  const [pinnedWorkflows, setPinnedWorkflows] = useState<string[]>(mockDashboard.workflows.slice(0, 2).map((workflow) => workflow.id))
  const [copyLabel, setCopyLabel] = useState('Copy brief')

  useEffect(() => {
    async function loadWorkspace() {
      const [dashboardResult, knowledgeResult, healthResult] = await Promise.allSettled([
        fetchJson<DashboardData>('/api/dashboard'),
        fetchJson<KnowledgeItem[]>('/api/knowledge'),
        fetchJson<{ ok: boolean; mode: string }>('/api/health'),
      ])

      if (dashboardResult.status === 'fulfilled') {
        const nextDashboard = dashboardResult.value

        if (knowledgeResult.status === 'fulfilled') {
          nextDashboard.knowledge = knowledgeResult.value
        }

        setDashboard(nextDashboard)
        setSelectedTicketId(nextDashboard.tickets[0]?.id ?? '')

        if (healthResult.status === 'fulfilled') {
          setConnectionLabel(
            healthResult.value.mode === 'database-enabled'
              ? 'Live backend · database mode'
              : 'Live backend · resilient demo mode',
          )
        } else {
          setConnectionLabel('Live backend connected')
        }
      } else {
        setConnectionLabel('Offline fallback · premium demo mode')
      }

      setIsLoading(false)
    }

    void loadWorkspace()
  }, [])

  useEffect(() => {
    if (!dashboard.tickets.some((ticket) => ticket.id === selectedTicketId)) {
      setSelectedTicketId(dashboard.tickets[0]?.id ?? '')
    }
  }, [dashboard.tickets, selectedTicketId])

  const filteredKnowledge = useMemo(() => {
    const query = knowledgeQuery.trim().toLowerCase()
    if (!query) {
      return dashboard.knowledge
    }

    return dashboard.knowledge.filter((item) =>
      [item.title, item.summary, item.content, item.tags.join(' ')].join(' ').toLowerCase().includes(query),
    )
  }, [dashboard.knowledge, knowledgeQuery])

  const filteredTickets = useMemo(() => {
    return dashboard.tickets.filter((ticket) => {
      if (queueFilter === 'risk') {
        return ticket.slaRisk
      }

      if (queueFilter === 'billing') {
        return ticket.category === 'Billing'
      }

      if (queueFilter === 'vip') {
        return ticket.priority === 'Urgent' || ticket.category === 'Enterprise'
      }

      return true
    })
  }, [dashboard.tickets, queueFilter])

  const visibleKnowledge = useMemo(() => {
    if (activeView === 'knowledge') {
      return filteredKnowledge
    }

    return filteredKnowledge.slice(0, focusMode ? 3 : 5)
  }, [activeView, filteredKnowledge, focusMode])

  const selectedTicket = dashboard.tickets.find((ticket) => ticket.id === selectedTicketId) ?? dashboard.tickets[0]
  const urgentCount = dashboard.tickets.filter((ticket) => ticket.priority === 'Urgent' || ticket.slaRisk).length
  const automationScore = dashboard.metrics[0]?.value ?? '91%'
  const firstResponse = dashboard.metrics[1]?.value ?? '27s'
  const criticalAlerts = dashboard.alerts.filter((alert) => alert.severity === 'critical').length
  const totalVolume = dashboard.channels.reduce((total, channel) => total + channel.volume, 0)
  const pinnedWorkflowCards = dashboard.workflows.filter((workflow) => pinnedWorkflows.includes(workflow.id))
  const unpinnedWorkflowCards = dashboard.workflows.filter((workflow) => !pinnedWorkflows.includes(workflow.id))
  const workflowCards = [...pinnedWorkflowCards, ...unpinnedWorkflowCards]
  const copilotConfidence = assistantReply?.confidence ?? 94
  const liveKnowledge =
    assistantReply?.matchedKnowledge.length && assistantReply.matchedKnowledge.length > 0
      ? assistantReply.matchedKnowledge
      : visibleKnowledge.slice(0, 3)
  const queueNarrative =
    queueFilter === 'all'
      ? 'All high-signal conversations in one surface.'
      : queueFilter === 'risk'
        ? 'Only cases with SLA pressure or delivery risk.'
        : queueFilter === 'billing'
          ? 'Finance-sensitive tickets that need crisp responses.'
          : 'VIP, enterprise, and urgent tickets grouped together.'

  const dailyFocus = [
    `${urgentCount} conversations need tighter follow-through before the next service threshold.`,
    `${criticalAlerts} critical watch item${criticalAlerts === 1 ? '' : 's'} should stay visible to leadership.`,
    `${pinnedWorkflowCards.length} automation playbook${pinnedWorkflowCards.length === 1 ? '' : 's'} pinned for the team today.`,
  ]

  const commandDeckStats = [
    { label: 'Queue monitored', value: `${totalVolume}`, note: 'cross-channel conversations' },
    { label: 'First response', value: firstResponse, note: 'median first reply time' },
    { label: 'Pinned playbooks', value: `${pinnedWorkflowCards.length}`, note: 'ready for fast routing' },
  ]

  const executiveBrief = useMemo(
    () => buildExecutiveBrief(selectedTicket, assistantReply, dashboard.alerts, briefMode),
    [assistantReply, briefMode, dashboard.alerts, selectedTicket],
  )

  async function handleSendMessage(message: string) {
    const trimmed = message.trim()
    if (!trimmed) {
      return
    }

    const customerMessage: ChatMessage = {
      id: createMessageId('draft'),
      role: 'customer',
      author: 'Live customer',
      content: trimmed,
      timestamp: createTimestamp(),
    }

    setDashboard((current) => ({
      ...current,
      conversation: [...current.conversation, customerMessage],
    }))
    setComposer('')
    setIsSending(true)
    setActiveView('live')

    try {
      const response = await fetch(`${API_BASE}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: trimmed }),
      })

      if (!response.ok) {
        throw new Error('Chat request failed')
      }

      const payload = (await response.json()) as ChatReply
      setAssistantReply(payload)
      setConnectionLabel('Connected to live backend')
      setDashboard((current) => ({
        ...current,
        conversation: [...current.conversation, payload.message],
        tickets: payload.ticket ? [payload.ticket, ...current.tickets].slice(0, 8) : current.tickets,
      }))

      if (payload.ticket) {
        setSelectedTicketId(payload.ticket.id)
      }
    } catch {
      const fallback = buildLocalReply(trimmed, dashboard.knowledge)
      setAssistantReply(fallback)
      setConnectionLabel('Offline fallback · premium demo mode')
      setDashboard((current) => ({
        ...current,
        conversation: [...current.conversation, fallback.message],
        tickets: fallback.ticket ? [fallback.ticket, ...current.tickets].slice(0, 8) : current.tickets,
      }))

      if (fallback.ticket) {
        setSelectedTicketId(fallback.ticket.id)
      }
    } finally {
      setIsSending(false)
    }
  }

  async function handleCopyBrief() {
    if (!navigator.clipboard) {
      setCopyLabel('Clipboard unavailable')
      return
    }

    await navigator.clipboard.writeText(executiveBrief)
    setCopyLabel('Copied')
    window.setTimeout(() => setCopyLabel('Copy brief'), 1800)
  }

  function toggleWorkflow(workflowId: string) {
    setPinnedWorkflows((current) =>
      current.includes(workflowId) ? current.filter((id) => id !== workflowId) : [...current, workflowId].slice(-3),
    )
  }

  return (
    <main className={`app-shell ${compactMode ? 'compact-mode' : ''} ${focusMode ? 'focus-mode' : ''}`}>
      <section className="panel top-shell panel-glass">
        <div className="topbar-row">
          <div>
            <span className="eyebrow">Aurora Support OS</span>
            <h1>Rebuilt into a cleaner, sharper support command center.</h1>
          </div>

          <div className="topbar-actions">
            <div className="segmented-control" aria-label="Workspace view">
              {viewOptions.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  className={option.id === activeView ? 'segmented-button active' : 'segmented-button'}
                  onClick={() => setActiveView(option.id)}
                >
                  {option.label}
                </button>
              ))}
            </div>

            <div className="toggle-row">
              <button
                type="button"
                className={focusMode ? 'soft-button toggle-button active' : 'soft-button toggle-button'}
                onClick={() => setFocusMode((current) => !current)}
              >
                {focusMode ? 'Focus mode on' : 'Focus mode'}
              </button>
              <button
                type="button"
                className={compactMode ? 'soft-button toggle-button active' : 'soft-button toggle-button'}
                onClick={() => setCompactMode((current) => !current)}
              >
                {compactMode ? 'Compact on' : 'Compact layout'}
              </button>
            </div>
          </div>
        </div>

        <div className="hero-grid">
          <article className="hero-card hero-copy-card">
            <div className="hero-copy-block">
              <p className="hero-text">
                A lower-noise design, stronger layout hierarchy, and more useful workspace tools now make the product feel
                far more intentional. The experience is built around active queue control, faster case reviews, and a
                leadership-ready briefing flow.
              </p>
            </div>

            <div className="hero-pill-row">
              <span className="status-pill">{connectionLabel}</span>
              <span className="badge neutral">{totalVolume} conversations tracked</span>
              <span className="badge warning">{criticalAlerts} critical watch items</span>
            </div>

            <div className="quick-actions">
              {dashboard.quickActions.map((action) => (
                <button
                  key={action}
                  type="button"
                  className="soft-button"
                  onClick={() => void handleSendMessage(quickPromptMap[action] ?? action)}
                >
                  {action}
                </button>
              ))}
            </div>
          </article>

          <article className="hero-card hero-focus-card">
            <div className="section-heading">
              <div>
                <p className="mini-label">Today focus</p>
                <h2>Ops priorities</h2>
              </div>
              <span className="badge accent">{activeView === 'knowledge' ? 'Knowledge-led' : 'Live control'}</span>
            </div>

            <div className="focus-list">
              {dailyFocus.map((item) => (
                <article key={item} className="focus-item">
                  <span className="focus-index" />
                  <p>{item}</p>
                </article>
              ))}
            </div>
          </article>

          <article className="hero-card hero-score-card">
            <span className="mini-label">Executive signal</span>
            <strong>{automationScore}</strong>
            <p>automation posture</p>
            <div className="stat-stack">
              {commandDeckStats.map((item) => (
                <div key={item.label} className="stat-line">
                  <span>{item.label}</span>
                  <strong>{item.value}</strong>
                  <small>{item.note}</small>
                </div>
              ))}
            </div>
          </article>
        </div>
      </section>

      <section className="metrics-grid">
        {dashboard.metrics.map((metric) => (
          <article key={metric.label} className="panel metric-card">
            <p className="mini-label">{metric.label}</p>
            <strong>{metric.value}</strong>
            <span className={`delta ${metric.direction}`}>{metric.delta} vs previous period</span>
          </article>
        ))}
      </section>

      <section className="workspace-grid">
        <article className="panel panel-span-7 queue-panel">
          <div className="section-heading section-heading-wrap">
            <div>
              <p className="mini-label">Priority board</p>
              <h2>Queue Radar</h2>
            </div>
            <div className="panel-controls">
              <div className="segmented-control compact" aria-label="Queue filter">
                {queueFilters.map((filter) => (
                  <button
                    key={filter.id}
                    type="button"
                    className={filter.id === queueFilter ? 'segmented-button active' : 'segmented-button'}
                    onClick={() => setQueueFilter(filter.id)}
                  >
                    {filter.label}
                  </button>
                ))}
              </div>
              <span className="badge neutral">{queueNarrative}</span>
            </div>
          </div>

          <div className="ticket-board">
            {filteredTickets.length > 0 ? (
              filteredTickets.map((ticket) => (
                <button
                  key={ticket.id}
                  type="button"
                  className={ticket.id === selectedTicketId ? 'ticket-card ticket-card-button active' : 'ticket-card ticket-card-button'}
                  onClick={() => setSelectedTicketId(ticket.id)}
                >
                  <div className="ticket-title-row">
                    <div>
                      <strong>{ticket.customer}</strong>
                      <p>{ticket.topic}</p>
                    </div>
                    <span className={`badge ${ticket.slaRisk ? 'warning' : 'neutral'}`}>{ticket.priority}</span>
                  </div>
                  <div className="ticket-meta-row">
                    <span>{ticket.id}</span>
                    <span>{ticket.category}</span>
                    <span>{ticket.status}</span>
                    <span>{ticket.eta}</span>
                  </div>
                </button>
              ))
            ) : (
              <div className="empty-state">
                <strong>No tickets match the current filter.</strong>
                <p>Switch the queue view to see more cases.</p>
              </div>
            )}
          </div>
        </article>

        <article className="panel panel-span-5 case-panel">
          <div className="section-heading section-heading-wrap">
            <div>
              <p className="mini-label">Case studio</p>
              <h2>Selected Case</h2>
            </div>
            <span className={`badge ${selectedTicket?.slaRisk ? 'warning' : 'accent'}`}>
              {selectedTicket?.sentiment ?? 'No case selected'}
            </span>
          </div>

          {selectedTicket ? (
            <>
              <div className="case-overview">
                <strong>{selectedTicket.topic}</strong>
                <p>{selectedTicket.customer}</p>
              </div>

              <div className="case-meta-grid">
                <article className="mini-stat-card">
                  <span>Status</span>
                  <strong>{selectedTicket.status}</strong>
                </article>
                <article className="mini-stat-card">
                  <span>Priority</span>
                  <strong>{selectedTicket.priority}</strong>
                </article>
                <article className="mini-stat-card">
                  <span>ETA</span>
                  <strong>{selectedTicket.eta}</strong>
                </article>
                <article className="mini-stat-card">
                  <span>Category</span>
                  <strong>{selectedTicket.category}</strong>
                </article>
              </div>

              <div className="section-heading section-heading-wrap nested-heading">
                <p className="mini-label">Brief composer</p>
                <div className="segmented-control compact" aria-label="Brief style">
                  {briefModes.map((mode) => (
                    <button
                      key={mode.id}
                      type="button"
                      className={mode.id === briefMode ? 'segmented-button active' : 'segmented-button'}
                      onClick={() => setBriefMode(mode.id)}
                    >
                      {mode.label}
                    </button>
                  ))}
                </div>
              </div>

              <textarea className="brief-output" value={executiveBrief} readOnly rows={10} />

              <div className="action-row">
                <button type="button" className="primary-button" onClick={() => void handleCopyBrief()}>
                  {copyLabel}
                </button>
                <div className="tag-list">
                  {(assistantReply?.suggestedActions ?? ['Prepare response', 'Align owner', 'Send next update']).map((action) => (
                    <span key={action}>{action}</span>
                  ))}
                </div>
              </div>
            </>
          ) : (
            <p className="muted-text">No case selected.</p>
          )}
        </article>

        <article className="panel panel-span-7 chart-panel">
          <div className="section-heading">
            <div>
              <p className="mini-label">Performance arc</p>
              <h2>Service Rhythm</h2>
            </div>
            <span className="badge accent">{isLoading ? 'Syncing' : 'Automation and escalation trend'}</span>
          </div>

          <div className="chart-shell large-chart">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={dashboard.trend}>
                <defs>
                  <linearGradient id="automationGradient" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="0%" stopColor="#1f2937" stopOpacity="0.26" />
                    <stop offset="100%" stopColor="#1f2937" stopOpacity="0.02" />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#d8dee8" vertical={false} />
                <XAxis dataKey="day" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} />
                <Tooltip />
                <Area type="monotone" dataKey="automated" stroke="#111827" fill="url(#automationGradient)" strokeWidth={2.4} />
                <Line type="monotone" dataKey="escalated" stroke="#64748b" strokeWidth={2.4} dot={{ r: 3 }} />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          <div className="chart-summary-grid">
            {dashboard.journey.map((step) => (
              <article key={step.stage} className="journey-card">
                <span>{step.stage}</span>
                <strong>{step.count}</strong>
                <small>{step.healthy ? 'Healthy throughput' : 'Needs leadership attention'}</small>
              </article>
            ))}
          </div>
        </article>

        <article className="panel panel-span-5 knowledge-panel">
          <div className="section-heading section-heading-wrap">
            <div>
              <p className="mini-label">Trusted answers</p>
              <h2>Knowledge Library</h2>
            </div>
            <input
              className="search-input"
              value={knowledgeQuery}
              onChange={(event) => setKnowledgeQuery(event.target.value)}
              placeholder="Search policies, scripts, or tags..."
            />
          </div>

          <div className="knowledge-grid single-column">
            {visibleKnowledge.map((item) => (
              <article key={item.id} className="knowledge-card">
                <div className="knowledge-header">
                  <strong>{item.title}</strong>
                  <span className="badge neutral">{item.trustScore}% trusted</span>
                </div>
                <p>{item.summary}</p>
                <div className="tag-list">
                  {item.tags.map((tag) => (
                    <span key={tag}>{tag}</span>
                  ))}
                </div>
                <span className="muted-text">Reviewed {item.lastReviewed}</span>
              </article>
            ))}
          </div>
        </article>

        <article className="panel panel-span-7 chat-panel">
          <div className="section-heading section-heading-wrap">
            <div>
              <p className="mini-label">Live workspace</p>
              <h2>Copilot Studio</h2>
            </div>
            <span className="badge accent">{isSending ? 'Generating' : `${copilotConfidence}% confidence`}</span>
          </div>

          <div className="chat-layout">
            <div className="chat-thread">
              {dashboard.conversation.map((message) => (
                <div key={message.id} className={`chat-bubble ${message.role}`}>
                  <div className="chat-meta">
                    <strong>{message.author}</strong>
                    <span>{message.timestamp}</span>
                  </div>
                  <p>{message.content}</p>
                </div>
              ))}
            </div>

            <aside className="copilot-rail">
              <div className="confidence-ring">
                <div className="confidence-core">
                  <strong>{copilotConfidence}%</strong>
                  <span>Match quality</span>
                </div>
              </div>
              <div className="info-block">
                <span className="mini-label">Matched references</span>
                <div className="knowledge-mini-list">
                  {liveKnowledge.map((item) => (
                    <article key={item.id} className="mini-card soft-surface">
                      <strong>{item.title}</strong>
                      <p>{item.summary}</p>
                    </article>
                  ))}
                </div>
              </div>
            </aside>
          </div>

          <div className="composer-row">
            <textarea
              value={composer}
              onChange={(event) => setComposer(event.target.value)}
              onKeyDown={(event) => {
                if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                  event.preventDefault()
                  void handleSendMessage(composer)
                }
              }}
              placeholder="Describe the customer issue and let the workspace prepare the next best move..."
              rows={4}
            />
            <button
              type="button"
              className="primary-button"
              onClick={() => void handleSendMessage(composer)}
              disabled={isSending}
            >
              {isSending ? 'Preparing...' : 'Generate response'}
            </button>
          </div>
        </article>

        <article className="panel panel-span-5 automation-panel">
          <div className="section-heading section-heading-wrap">
            <div>
              <p className="mini-label">Workflow studio</p>
              <h2>Automation Playbooks</h2>
            </div>
            <span className="badge neutral">Pin up to 3</span>
          </div>

          <div className="workflow-grid single-column">
            {workflowCards.map((workflow) => {
              const isPinned = pinnedWorkflows.includes(workflow.id)

              return (
                <article key={workflow.id} className={`workflow-card ${workflow.status} ${isPinned ? 'is-pinned' : ''}`}>
                  <div className="workflow-top">
                    <div>
                      <strong>{workflow.name}</strong>
                      <p>{workflow.summary}</p>
                    </div>
                    <button
                      type="button"
                      className={isPinned ? 'pin-button active' : 'pin-button'}
                      onClick={() => toggleWorkflow(workflow.id)}
                    >
                      {isPinned ? 'Pinned' : 'Pin'}
                    </button>
                  </div>
                  <div className="meta-row">
                    <span>{workflow.coverage}</span>
                    <span>{workflow.successRate}</span>
                    <span>{workflow.status}</span>
                  </div>
                </article>
              )
            })}
          </div>
        </article>

        {!focusMode ? (
          <article className="panel panel-span-12 intelligence-panel">
            <div className="section-heading section-heading-wrap">
              <div>
                <p className="mini-label">Team intelligence</p>
                <h2>Leadership and floor view</h2>
              </div>
              <span className="badge accent">New layout with calmer colors</span>
            </div>

            <div className="intelligence-grid">
              <div className="stack-list">
                {dashboard.insights.map((insight) => (
                  <article key={insight.title} className="mini-card soft-surface">
                    <span className="mini-label">{insight.title}</span>
                    <strong>{insight.value}</strong>
                    <p>{insight.note}</p>
                  </article>
                ))}
              </div>

              <div className="activity-list">
                {dashboard.activity.map((item) => (
                  <article key={item.id} className="activity-item">
                    <span className={`activity-dot ${item.type}`}></span>
                    <div>
                      <strong>{item.title}</strong>
                      <p>{item.description}</p>
                      <span className="muted-text">{item.time}</span>
                    </div>
                  </article>
                ))}
              </div>

              <div className="agent-list">
                {dashboard.agents.map((agent) => (
                  <article key={agent.id} className="agent-card">
                    <div className="agent-top">
                      <div>
                        <strong>{agent.name}</strong>
                        <p>{agent.role}</p>
                      </div>
                      <span className={`badge status-${agent.status}`}>{agent.status}</span>
                    </div>
                    <div className="meta-row">
                      <span>{agent.activeCases} active</span>
                      <span>{agent.csat}</span>
                      <span>{agent.automationLift} lift</span>
                    </div>
                  </article>
                ))}
              </div>

              <div className="channel-panel">
                <div className="section-heading">
                  <div>
                    <p className="mini-label">Channel posture</p>
                    <h2>Volume mix</h2>
                  </div>
                </div>
                <div className="chart-shell compact">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={dashboard.channels}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                      <XAxis dataKey="channel" tickLine={false} axisLine={false} />
                      <YAxis tickLine={false} axisLine={false} />
                      <Tooltip />
                      <Bar dataKey="volume" radius={[10, 10, 0, 0]}>
                        {dashboard.channels.map((channel, index) => (
                          <Cell key={channel.channel} fill={channelPalette[index % channelPalette.length]} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>
            </div>
          </article>
        ) : null}
      </section>
    </main>
  )
}

export default App
