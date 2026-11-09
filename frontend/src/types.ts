export interface MetricCard {
  label: string
  value: string
  delta: string
  direction: 'up' | 'down'
}

export type AlertSeverity = 'critical' | 'warning' | 'stable'
export type AgentStatus = 'available' | 'focused' | 'coaching' | 'vip'
export type WorkflowStatus = 'active' | 'monitoring' | 'draft'

export interface TrendPoint {
  day: string
  automated: number
  escalated: number
  satisfaction: number
}

export interface TicketSummary {
  id: string
  customer: string
  topic: string
  category: string
  status: string
  priority: string
  sentiment: string
  eta: string
  updatedAt: string
  slaRisk: boolean
}

export interface KnowledgeItem {
  id: string
  title: string
  summary: string
  content: string
  tags: string[]
  trustScore: number
  lastReviewed: string
}

export interface ChatMessage {
  id: string
  role: 'customer' | 'assistant' | 'agent' | 'system'
  author: string
  content: string
  timestamp: string
}

export interface ActivityItem {
  id: string
  title: string
  description: string
  time: string
  type: 'automation' | 'human' | 'knowledge' | 'system'
}

export interface InsightCard {
  title: string
  value: string
  note: string
}

export interface AlertItem {
  id: string
  title: string
  description: string
  severity: AlertSeverity
  owner: string
  impact: string
}

export interface AgentSnapshot {
  id: string
  name: string
  role: string
  status: AgentStatus
  activeCases: number
  csat: string
  automationLift: string
}

export interface WorkflowCard {
  id: string
  name: string
  summary: string
  coverage: string
  successRate: string
  status: WorkflowStatus
}

export interface ChannelSnapshot {
  channel: string
  volume: number
  avgHandle: string
  satisfaction: number
}

export interface JourneyStep {
  stage: string
  count: number
  healthy: boolean
}

export interface DashboardData {
  metrics: MetricCard[]
  trend: TrendPoint[]
  tickets: TicketSummary[]
  knowledge: KnowledgeItem[]
  conversation: ChatMessage[]
  activity: ActivityItem[]
  insights: InsightCard[]
  quickActions: string[]
  alerts: AlertItem[]
  agents: AgentSnapshot[]
  workflows: WorkflowCard[]
  channels: ChannelSnapshot[]
  journey: JourneyStep[]
}

export interface ChatReply {
  message: ChatMessage
  confidence: number
  matchedKnowledge: KnowledgeItem[]
  suggestedActions: string[]
  escalated: boolean
  escalationReason?: string
  ticket?: TicketSummary
}
