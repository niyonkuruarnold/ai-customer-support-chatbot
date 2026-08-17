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
handoff) appear in the customer's feed, and the session status drives the
green "connected to a human agent" banner.

The agent workspace (`src/stores/agent.js` + `src/components/agent/`) signs
in with HTTP Basic credentials (kept in memory, never persisted), lists the
escalated/open ticket queue, shows the pinned AI handoff summary, and lets
agents take over, reply, add internal notes, and resolve tickets.

### Knowledge Base (RAG) manager

The workspace has a **📚 Knowledge Base** tab (`src/components/admin/` +
`src/stores/knowledgeBase.js` + `src/api/admin.js`) for managing the RAG
corpus. It reuses the same Basic sign-in as the agent workspace (one login
covers both). From there you can drag-and-drop `.txt`/`.md`/`.pdf` support
files or paste raw FAQ text, see every indexed document with its chunk
count, expand a document to preview its chunks, and delete documents (which
removes their vectors from pgvector too).

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
│       └── KnowledgeBaseManager.vue # KB tab: drag-drop, paste text, manage docs
├── App.vue                  # Chat layout + Agent Mode toggle (?mode=agent)
├── main.js
└── style.css                # Tailwind entry
```

## Scripts

- `npm run dev` — start the Vite dev server with HMR
- `npm run build` — production build to `dist/`
- `npm run preview` — preview the production build locally
- `npm test` / `npm run test:watch` — run the Vitest suite
