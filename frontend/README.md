# AI Customer Support Chat — Frontend

Vue 3 frontend for the AI customer support chatbot, built with Vite, Tailwind CSS, and Axios.

## Stack

- [Vue 3](https://vuejs.org/) (`<script setup>` SFCs)
- [Vite](https://vite.dev/) dev server & build tool
- [Tailwind CSS v4](https://tailwindcss.com/) via the `@tailwindcss/vite` plugin
- [Axios](https://axios-http.com/) for HTTP calls to the Spring Boot backend

## Getting started

```bash
npm install
npm run dev
```

The dev server runs at `http://localhost:5173` — this origin is already
allowlisted in the backend's CORS configuration.

## Configuration

The backend API base URL is read from the `VITE_API_BASE_URL` environment
variable (see `.env`). The current value points at the Spring Boot backend
mapped at `http://localhost:8080/api/chat`. If your backend exposes the API
under a different prefix (e.g. `http://localhost:8080/api/v1/chat`), update
`.env` accordingly and restart the dev server:

```
VITE_API_BASE_URL=http://localhost:8080/api
```

## State management & persistence

Chat state lives in a [Pinia](https://pinia.vuejs.org/) store (`src/stores/chat.js`).
The conversation is persisted server-side in a chat session (created on the
first message); `loadHistory()` restores it from `GET /chat/session/{id}` on
startup, with `localStorage` as an offline fallback. While a session is
active the store polls the backend every 3s so agent replies (after a human
handoff) appear in the customer's feed.

**Agent Active mode:** once the session is escalated, the customer UI
switches from "AI Support" to "Agent Active" — an amber banner ("🎧 Agent
Active — the AI assistant is paused"), the header status and input
placeholder change, and the backend no longer generates AI answers (each
message gets a "sent to the agent" acknowledgement).

The agent workspace (`src/stores/agent.js` + `src/components/agent/`) signs
in with HTTP Basic credentials (kept in memory, never persisted), lists the
escalated/open ticket queue (status / priority / sentiment badges + the
customer's contact email), shows the pinned AI handoff summary, and lets
agents take over, reply, add internal notes, and resolve tickets. While
signed in the store **polls the backend every 5s** (structured polling —
the same mechanism the customer chat uses), so newly escalated tickets
appear in the queue and new customer messages stream into the open
conversation without a manual refresh; polling stops on logout or a 401.

### Knowledge Base (RAG) manager

There are two entry points for managing the RAG corpus, both backed by
`src/stores/knowledgeBase.js` + `src/api/admin.js` (which talks to the
spec-exact `/v1/admin/knowledge-base/*` endpoints):

- **Dedicated page** — `src/components/admin/KnowledgeBaseAdmin.vue`,
  reachable via the **📚 Knowledge Base** header button or
  `http://localhost:5173/?mode=knowledge`. Drag-and-drop `.md`/`.txt`
  files (or browse), watch the indexing spinner while embeddings are
  generated, review the indexed-documents **table** (title, source,
  chunk count, date) and delete entries — with **toast notifications**
  (`src/composables/useToasts.js`) for every success/error.
- **Agent workspace tab** — `src/components/admin/KnowledgeBaseManager.vue`
  inside the 🎧 Agent Workspace, which additionally supports pasting raw
  FAQ text and expanding chunk previews.

Both reuse the same Basic sign-in as the agent workspace (one login covers
all areas). Deleting a document removes its vectors from pgvector too.

### Ticket Dashboard

`src/components/admin/TicketDashboard.vue`, reachable via the **🎫 Tickets**
header button or `http://localhost:5173/?mode=tickets`, consumes the Week 6
lifecycle API (`GET /api/v1/tickets` via `src/api/admin.js`):

- **Filters** — status (Open / Escalated / In progress / Resolved / Closed),
  priority (Low / Medium / High / Urgent), and assigned agent ID; any
  change restarts at the first page, with a Clear filters shortcut.
- **Pagination** — pageable table (10 per page) with prev/next controls and
  a page counter; the backend response is the `PageResponse` wrapper.
- **Close action** — resolved tickets get a Close button
  (`POST /api/v1/tickets/{id}/close`), with success/error toasts.
- Status + priority badges, customer email, assigned agent, and last-updated
  timestamps per row; shares the same Basic sign-in and 401 handling as the
  other admin areas.

## Testing

Tests run with [Vitest](https://vitest.dev/) + Vue Test Utils in jsdom.

```bash
npm test          # run once
npm run test:watch
```

Coverage:
- `src/stores/chat.spec.js` — sending, trim/empty/over-limit validation,
  in-flight blocking, failure + retry, persistence, clearing
- `src/components/ChatMessage.spec.js` — role-based rendering, failed-state
  retry chip
- `src/App.spec.js` — submit flow, disabled send button, inline retry, clear
  conversation (two-step confirm)
- `src/stores/knowledgeBase.spec.js` — fetch documents/chunks, upload,
  paste-text ingestion, delete, 401/error handling
- `src/components/admin/KnowledgeBaseManager.spec.js` — drag-and-drop
  upload, paste form, document list/chunk previews, delete, session-expiry
  prompt
- `src/components/admin/TicketDashboard.spec.js` — sign-in gate, ticket list
  rendering, status/priority/agent filters, pagination, close action, 401
  handling

## Project structure

```
src/
├── api/
│   ├── chat.js              # Axios client (session-aware chat/history/reset)
│   ├── agent.js             # Agent API client (HTTP Basic, 401 handling)
│   └── admin.js             # Admin API client (knowledge base, shared Basic auth)
├── stores/
│   ├── chat.js              # Customer chat store (session, polling, escalation)
│   ├── agent.js             # Agent workspace store (login, tickets, actions)
│   └── knowledgeBase.js     # Knowledge base store (documents, chunks, ingestion)
├── components/
│   ├── ChatMessage.vue      # Message bubble (user / assistant / agent roles)
│   ├── TypingIndicator.vue  # Animated "typing" loading dots
│   ├── agent/
│   │   ├── AgentWorkspace.vue      # Workspace shell + login gate + tabs
│   │   ├── AgentTicketList.vue     # Left panel: ticket queue with badges
│   │   └── AgentConversation.vue   # Center panel: summary, transcript, notes, reply
│   └── admin/
│       ├── KnowledgeBaseManager.vue # KB tab: drag-drop, paste text, manage docs
│       └── TicketDashboard.vue      # Ticket lifecycle: filters, pagination, close
├── App.vue                  # Chat layout + mode toggle (?mode=agent | ?mode=knowledge | ?mode=tickets)
├── main.js
├── composables/
│   └── useToasts.js         # Module-scoped toast notifications
└── style.css                # Tailwind entry
```

## Scripts

- `npm run dev` — start the Vite dev server with HMR
- `npm run build` — production build to `dist/`
- `npm run preview` — preview the production build locally
- `npm test` / `npm run test:watch` — run the Vitest suite
