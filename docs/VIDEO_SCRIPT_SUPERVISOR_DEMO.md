# CODAFRIQA AI Customer Support Chatbot — Supervisor Demo Video Script

**Target Duration:** 5–7 minutes
**Date:** September 2026
**Presenter:** [Your Name]

---

## Pre-Recording Checklist

| Item | Status |
|------|--------|
| Backend running on `localhost:8080` | ☐ |
| Frontend running on `localhost:5173` (or built to `localhost`) | ☐ |
| PostgreSQL with pgvector running | ☐ |
| `GEMINI_API_KEY` set in environment | ☐ |
| Database seeded (`StagingDataSeeder` ran on startup) | ☐ |
| Two browser windows/tabs ready (Customer + Agent) | ☐ |
| Screen recording software started | ☐ |

---

## Scene-by-Scene Script

---

### SCENE 1: Project Intro & RAG Chatbot Experience
**Time:** 0:00 – 1:00

#### Opening Shot (0:00 – 0:15)

**On-screen:** Show the CODAFRIQA Smart Assistant widget in its compact floating form (bottom-right corner of a clean browser window at `http://localhost`).

> **NARRATION:**
> "This is the CODAFRIQA AI Customer Support Chatbot — a full-stack application built with Spring Boot 3.3 on the backend, Vue 3 on the frontend, and PostgreSQL with pgvector for semantic vector search. The AI is powered by Google Gemini, and real-time communication uses STOMP WebSockets. Let me show you how it works."

**On-screen callout (lower-third):**
```
TECH STACK: Spring Boot 3.3 · Vue 3 · PostgreSQL + pgvector · Google Gemini API · STOMP WebSockets
```

#### Open the Widget (0:15 – 0:25)

**Action:** Click the red floating action button (FAB) in the bottom-right corner. The compact chat widget opens at 400×580px.

> **NARRATION:**
> "The customer sees a floating AI concierge widget — similar to Intercom or Drift. It opens as a compact popup and can be expanded to full-screen."

#### Ask a Domain-Specific Question (0:25 – 0:50)

**Action:** Click one of the suggested prompt chips — for example, "What is your return policy?" — or type it manually and press Enter.

**Wait for the response** (2–3 seconds).

**On-screen callout (arrow pointing to AI response):**
```
RAG Pipeline: pgvector similarity search → Gemini context injection → grounded response
```

> **NARRATION:**
> "I'm asking a domain-specific question about our return policy. The backend runs a pgvector similarity search against our knowledge base, retrieves the most relevant document chunks, injects them into the Gemini prompt as context, and returns a grounded answer. Notice the response references our actual policy — not generic AI knowledge."

#### Highlight the Status Badge (0:50 – 1:00)

**Action:** Point to the green "AI Assistant — Online" badge in the widget header.

> **NARRATION:**
> "The header shows a live status badge — green means the AI assistant is active and responding. This status changes in real-time as the conversation progresses through different lifecycle states."

---

### SCENE 2: Escalation & Live Agent Workspace Takeover
**Time:** 1:00 – 2:30

#### Trigger Escalation (1:00 – 1:20)

**Action:** Type "I want to talk to a human agent" and press Enter.

**Observe:**
- The AI returns the handoff acknowledgement message.
- The status badge changes from green "AI Assistant" to amber "Connected to Agent".
- An amber "Agent Active" banner appears inside the widget.

**On-screen callout:**
```
Escalation: AI detects intent → creates support ticket → pauses AI → notifies agent via WebSocket
```

> **NARRATION:**
> "When the customer explicitly requests a human agent, the system detects the escalation intent, creates a support ticket with an AI-generated handoff summary, and pauses the AI. The status badge switches to amber — 'Connected to Agent' — and the agent receives a real-time notification over STOMP WebSocket."

#### Switch to Agent Workspace (1:20 – 1:45)

**Action:** Switch to a second browser tab/window. Log in as `agent@codafriqa.local` (password: `Password123!`) using the Staff Sign In form.

**On-screen:** The Agent Workspace loads with:
- Left panel: list of active sessions with live status indicators
- Right panel: conversation transcript with the customer

**On-screen callout:**
```
RBAC: Agent role → access to Live Workspace + Ticket Queue only
```

> **NARRATION:**
> "I'm now logging in as a support agent. The role-based access control restricts agents to the Live Customer Workspace and Ticket Queue — they cannot see analytics or audit logs."

#### Highlight AI Conversation Summary (1:45 – 2:00)

**Action:** Point to the auto-generated AI summary panel in the agent workspace — a 2–3 sentence Gemini-generated summary of the customer's conversation history.

**On-screen callout:**
```
Gemini API: Auto-generates conversation summary on escalation handoff
```

> **NARRATION:**
> "When the conversation was escalated, Gemini automatically generated a concise handoff summary — two to three sentences capturing the customer's issue, what was discussed, and the current state. This saves the agent from reading the entire transcript."

#### Send a Private Internal Note (2:00 – 2:15)

**Action:** In the agent workspace, type an internal note (e.g., "Checking refund eligibility — customer has been with us since 2024"). Toggle the internal note switch or use the internal note input. Send it.

**On-screen callout:**
```
Internal Note: is_internal = true → hidden from customer view, broadcast only to agent WebSocket topic
```

> **NARRATION:**
> "The agent can send private internal notes — these are marked with `is_internal = true` in the database and are broadcast only to the agent-only WebSocket topic. The customer never sees these notes."

#### Send a Public Reply (2:15 – 2:30)

**Action:** Type a public reply to the customer (e.g., "I've reviewed your account and I can see the billing discrepancy. Let me process a refund for the extra charge.") and send it.

**Switch back to the customer view** — the message appears in real-time via WebSocket.

> **NARRATION:**
> "The agent's public reply is broadcast to the customer's chat in real-time over STOMP WebSocket — no page refresh needed. The customer sees the response immediately."

---

### SCENE 3: Ticket Lifecycle & Activity Log
**Time:** 2:30 – 3:45

#### Navigate to Ticket Queue (2:30 – 2:45)

**Action:** As the agent, click the "Ticket Queue" tab in the navigation bar.

**On-screen:** The Ticket Dashboard loads showing a table of support tickets with status badges, priority indicators, and assignment info.

> **NARRATION:**
> "The Ticket Queue shows all support tickets. Each ticket tracks its lifecycle through a state machine — from NEW through OPEN, PENDING states, to RESOLVED and CLOSED."

#### Demonstrate Status Transition (2:45 – 3:15)

**Action:**
1. Find a ticket in "OPEN" status.
2. Click the status control to transition it to "RESOLVED".
3. Confirm the transition.

**On-screen callout:**
```
State Machine: NEW → OPEN → PENDING_CUSTOMER / PENDING_INTERNAL → RESOLVED → CLOSED
```

> **NARRATION:**
> "Let me transition this ticket from OPEN to RESOLVED. The state machine enforces valid transitions — you can't jump from CLOSED to RESOLVED, for example. Each transition is logged immutably."

#### Show the Activity Timeline (3:15 – 3:45)

**Action:** Click on the ticket to open the detailed view. Scroll to the Activity Timeline component.

**On-screen:** A chronological list of activity entries — status changes, assignments, internal notes, public replies — each with timestamps and actor information.

**On-screen callout:**
```
Immutable Audit Trail: Every status change, assignment, and note is recorded with before/after snapshots
```

> **NARRATION:**
> "This is the immutable activity timeline — every change to the ticket is recorded chronologically. We can see the original status change from NEW to OPEN, the assignment to the agent, the internal note, and now the resolution. These records are append-only — no edits or deletes."

---

### SCENE 4: Manager Dashboard & System Audit Viewer
**Time:** 3:45 – 5:00

#### Manager Login (3:45 – 4:00)

**Action:** Log out. Log back in as `manager@codafriqa.local` (password: `Password123!`).

**On-screen:** The manager sees the navigation bar with additional tabs: Analytics, Audit Logs, Knowledge Base Admin, System Indexer.

> **NARRATION:**
> "Logging in as a support manager — this role has broader access including analytics, audit logs, and knowledge base management."

#### Analytics Dashboard (4:00 – 4:30)

**Action:** Click the "Analytics" tab.

**On-screen:** Chart.js visualizations:
- Containment Rate (donut chart)
- CSAT Score (bar chart)
- First Response Time (line chart)
- Ticket volume by status (stacked bar)

**On-screen callout:**
```
Analytics: Chart.js · Containment Rate · CSAT · First Response Time · Ticket Volume
```

> **NARRATION:**
> "The Analytics Dashboard shows key metrics — containment rate measures how many conversations the AI handles without escalation, CSAT tracks customer satisfaction, and first response time monitors agent performance. All charts are powered by Chart.js and update from the live database."

#### Export a Report (4:30 – 4:45)

**Action:** Click the "Export CSV" or "Export PDF" button on the dashboard.

**On-screen:** A file downloads (CSV or PDF) containing the ticket data.

> **NARRATION:**
> "Managers can export reports in CSV or PDF format for external analysis or compliance reporting."

#### Admin: System Audit Logs (4:45 – 5:00)

**Action:** Log out. Log back in as `admin@codafriqa.local` (password: `Password123!`). Click the "Audit Logs" tab.

**On-screen:** A filterable, paginated table of system audit entries — login attempts, role updates, ticket assignments, data exports, knowledge base publications.

**On-screen callout:**
```
Audit Log: Immutable record of all administrative actions · Filterable by actor, action type, date range
```

> **NARRATION:**
> "As a system administrator, I can view the read-only audit log. Every critical action — logins, role changes, ticket reassignments, data exports — is recorded with actor information, timestamps, and metadata. This table is strictly read-only for compliance."

---

### SCENE 5: Closing & Technical Summary
**Time:** 5:00 – 5:30

#### Final On-Screen Summary Card

**Action:** Display a summary slide or return to the main chat widget view.

**On-screen text overlay:**

```
┌─────────────────────────────────────────────────────────────┐
│           CODAFRIQA AI Customer Support Chatbot             │
│                                                             │
│  ✅ RAG Chatbot — pgvector + Gemini context injection      │
│  ✅ Real-time Escalation — STOMP WebSocket handoff          │
│  ✅ Agent Workspace — AI summary + internal notes           │
│  ✅ Ticket Lifecycle — State machine + immutable audit      │
│  ✅ Analytics — Chart.js dashboards + CSV/PDF export        │
│  ✅ RBAC — 5 roles: Customer, Agent, Editor, Manager, Admin│
│  ✅ Audit Trail — Read-only system logs for compliance      │
│                                                             │
│  Stack: Spring Boot 3.3 · Vue 3 · PostgreSQL + pgvector   │
│         Google Gemini · STOMP WebSockets · Chart.js         │
└─────────────────────────────────────────────────────────────┘
```

> **NARRATION:**
> "To summarize — the CODAFRIQA AI Customer Support Chatbot delivers a complete customer support platform: RAG-grounded AI responses via pgvector and Gemini, real-time agent collaboration over STOMP WebSockets, a full ticket lifecycle with immutable activity logs, role-based access control across five user roles, analytics dashboards, and a comprehensive audit trail. Thank you."

---

## On-Screen Callout Checklist

Use these as lower-third overlays or subtitle callouts during recording:

| Timestamp | Callout Text |
|-----------|--------------|
| 0:10 | `TECH STACK: Spring Boot 3.3 · Vue 3 · PostgreSQL + pgvector · Gemini API · STOMP WebSockets` |
| 0:30 | `RAG Pipeline: pgvector similarity search → Gemini context injection → grounded response` |
| 1:05 | `Escalation: AI detects intent → creates ticket → pauses AI → notifies agent via WebSocket` |
| 1:25 | `RBAC: Agent role → Live Workspace + Ticket Queue only` |
| 1:50 | `Gemini API: Auto-generates conversation summary on escalation` |
| 2:05 | `Internal Note: is_internal = true → hidden from customer, broadcast to agent WebSocket topic` |
| 2:20 | `STOMP WebSocket: Real-time message delivery — no page refresh needed` |
| 2:50 | `State Machine: NEW → OPEN → PENDING → RESOLVED → CLOSED` |
| 3:20 | `Immutable Audit Trail: Append-only activity log with before/after snapshots` |
| 3:50 | `Manager RBAC: Analytics + Audit + Knowledge Base + Ticket Queue` |
| 4:10 | `Chart.js: Containment Rate · CSAT · First Response Time · Volume` |
| 4:35 | `Export: CSV and PDF report generation` |
| 4:50 | `Admin Audit Log: Read-only · Filterable · Compliance-ready` |
| 5:10 | `5 Roles: Customer · Agent · Editor · Manager · Admin` |

---

## Key Technical Talking Points

Mention these naturally during narration:

1. **Spring Boot 3.3** — Backend framework with Spring AI integration for Gemini chat and embeddings.

2. **Vue 3 Composition API** — Frontend SPA with reactive state management (Pinia stores).

3. **pgvector** — PostgreSQL extension for vector similarity search. Knowledge base documents are chunked, embedded (768-dim via `text-embedding-004`), and stored in a `vector_store` table with HNSW indexing.

4. **Google Gemini API** — Used for both chat responses (`gemini-3.6-flash`) and text embeddings (`text-embedding-004`). The RAG pipeline retrieves relevant chunks and injects them as context.

5. **STOMP WebSockets** — Real-time bidirectional communication. Three topics per session:
   - `/topic/chat/{sessionId}` — broadcast to all subscribers
   - `/topic/agent/{sessionId}` — agent-only internal notes
   - `/ws-chat` — WebSocket endpoint

6. **RBAC (Role-Based Access Control)** — Five roles enforced both frontend (route guards) and backend (`@PreAuthorize`):
   - `CUSTOMER` — chat + my tickets
   - `AGENT` — live workspace + ticket queue
   - `EDITOR` — knowledge base management
   - `MANAGER` — analytics + all agent features
   - `ADMIN` — full system access + audit logs

7. **Ticket State Machine** — Enforced transitions: `NEW → OPEN → PENDING_CUSTOMER / PENDING_INTERNAL → RESOLVED → CLOSED / REOPENED → OPEN`. Invalid transitions throw `IllegalArgumentException`.

8. **Immutable Audit Trail** — `TicketActivityLog` and `AuditLog` entities are append-only. No update or delete operations are exposed.

9. **CSAT Feedback** — Post-chat satisfaction surveys with 1–5 star ratings and optional comments, linked to chat sessions.

10. **Multi-Stage Docker Build** — Backend: Maven + Java 21 multi-stage. Frontend: Node.js build + Nginx serving with reverse proxy.

---

## Post-Recording Notes

- **Trim** any dead air between scenes (aim for tight cuts).
- **Add** scene title cards at each transition point.
- **Overlay** the callout text as animated lower-thirds in post-production.
- **Normalize** audio levels across all scenes.
- **Export** at 1080p minimum, 30fps.

---

## File Locations Reference

| File | Purpose |
|------|---------|
| `src/main/java/.../config/StagingDataSeeder.java` | Boot-time seed data (users, KB, conversations, tickets) |
| `src/main/java/.../service/ChatService.java` | RAG chat pipeline + escalation logic |
| `src/main/java/.../service/EscalationService.java` | Ticket creation + AI summary on handoff |
| `src/main/java/.../controller/WebSocketChatController.java` | STOMP WebSocket message handling |
| `src/main/java/.../model/SupportTicket.java` | Ticket entity with state machine |
| `src/main/java/.../model/TicketActivityLog.java` | Immutable activity audit trail |
| `frontend/src/App.vue` | Main SPA with role-based routing |
| `frontend/src/components/agent/AgentWorkspace.vue` | Agent live conversation view |
| `frontend/src/components/admin/AnalyticsDashboard.vue` | Chart.js metrics dashboard |
| `frontend/src/components/admin/AuditLogViewer.vue` | System audit log viewer |
| `frontend/src/components/tickets/TicketTimeline.vue` | Chronological activity timeline |
| `docker-compose.staging.yml` | Staging environment orchestration |
