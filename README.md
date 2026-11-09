# AI Customer Support Chatbot

A polished full-stack MVP for AI-assisted customer support with:

- live support chat
- knowledge retrieval
- escalation-aware ticketing
- queue analytics
- a redesigned, no-sidebar operations workspace

[View Full Project Proposal PDF](./doc/CODAFRIQA_AI_Chatbot_Proposal.pdf)

## Stack

- `frontend`: React + TypeScript + Vite + Recharts
- `backend`: Express + TypeScript
- `database`: PostgreSQL + pgvector

## What Is Included

- Beautiful redesigned support workspace with calmer colors and cleaner layout
- Live dashboard metrics and analytics cards
- Customer chat panel with AI copilot replies
- Knowledge-base search and trusted-answer cards
- Smart escalation suggestions and handoff notes
- Queue radar for urgent or SLA-risk tickets
- Activity feed and insight panels
- Demo-mode fallback when the backend or database is unavailable

## Run The App

1. Start PostgreSQL if you want live persistence:

```bash
docker compose up -d
```

2. Start the backend:

```bash
npm run dev:backend
```

3. Start the frontend in a second terminal:

```bash
npm run dev:frontend
```

4. Open the Vite URL shown in the terminal, usually `http://localhost:5173`.

## Environment

Copy `.env.example` values into your own environment if needed.

- `PORT`: backend port, default `4000`
- `CLIENT_ORIGIN`: frontend origin for CORS
- `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`: PostgreSQL connection settings

## Notes

- The backend boots in demo mode if PostgreSQL is not available.
- The schema is in `scripts/schema.sql`.
- Docker mounts the schema into Postgres so a fresh database is initialized automatically.
