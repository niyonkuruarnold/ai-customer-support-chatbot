# Staging Deployment Verification Test Suite

Automated verification tests for the AI Customer Support Chatbot staging environment.

## Quick Start

```bash
# 1. Start staging environment
docker compose -f docker-compose.staging.yml up --build -d

# 2. Wait for services
sleep 30

# 3. Run all tests
bash tests/staging/run-all.sh

# 4. Or run individual parts
bash tests/staging/run-all.sh --part 1    # Healthcheck
bash tests/staging/run-all.sh --part 2    # RBAC
bash tests/staging/run-all.sh --part 3    # WebSocket E2E
bash tests/staging/run-all.sh --skip-ws   # Skip WebSocket
bash tests/staging/run-all.sh --wait 30   # Wait before running
```

## Test Parts

### Part 1: Container Infrastructure & Health Audit

**Script:** `01-service-healthcheck.sh`

| Check | Method | Expected |
|-------|--------|----------|
| Docker container status | `docker compose ps` | All 3 containers running |
| PostgreSQL connectivity | `pg_isready` + TCP | Accepting connections |
| Backend health | `GET /api/health` | 200 OK |
| Backend detail | `GET /api/health/detail` | 200 + DB status UP |
| Frontend accessibility | `GET /` | 200 HTML |
| Nginx API proxy | `GET /api/health` via frontend | 200 OK |
| Security headers | X-Frame-Options, X-Content-Type-Options | Present |
| StagingDataSeeder | `SELECT COUNT(*) FROM users` | 5 rows |
| Flyway migrations | `flyway_schema_history` check | No failed migrations |
| Schema validation | All required columns verified | Exists |
| pgvector extension | `pg_extension` query | Installed |
| PermitAll endpoints | 6 endpoint smoke tests | 200 OK |

### Part 2: Multi-Role RBAC Verification

**Script:** `02-rbac-verification.sh`

Tests HTTP Basic auth with captured credentials for each role:

| Role | Credentials | Token | Access |
|------|-------------|-------|--------|
| **Admin** | `admin:admin123` | `Basic YWRtaW46YWRtaW4xMjM=` | Full (KB, audit, analytics, exports, agent) |
| **Manager** | `admin:admin123` | `Basic YWRtaW46YWRtaW4xMjM=` | Analytics, exports, audit (KB admin restricted) |
| **Agent** | `agent:agent123` | `Basic YWdlbnQ6YWdlbnQxMjM=` | Agent workspace, tickets (KB blocked 403) |
| **Editor** | `agent:agent123` | `Basic YWdlbnQ6YWdlbnQxMjM=` | Agent workspace (ROLE_AGENT mapping) |
| **Customer** | (anonymous) | — | Chat only (401 on agent/admin) |

**Endpoints tested per role:**
- Admin: 15 endpoints (all pass 200)
- Manager: 8 endpoints (analytics + exports)
- Agent: 12 endpoints (workspace OK, KB blocked 403)
- Editor: 3 endpoints (agent workspace)
- Customer: 10 endpoints (chat permitAll, agent/admin blocked 401)

**Edge cases tested:**
- Wrong password → 401
- Unknown user → 401
- Empty auth header → 401
- Cross-role access (agent → admin KB) → 403

### Part 3: WebSocket E2E Messaging Test

**Script:** `03-websocket-e2e-test.js`

Simulates live multi-user interaction:

| Step | Action | Assertion |
|------|--------|-----------|
| 3.1 | Customer connects to `/ws` | WebSocket connected |
| 3.1 | Send message via REST | Session created |
| 3.2 | Request escalation | Badge → "Waiting for Agent" |
| 3.3 | Gemini summary generated | Summary in WebSocket/REST |
| 3.4 | Agent connects | WebSocket connected |
| 3.4 | Agent subscribes to topics | `/topic/chat/` + `/topic/agent/` |
| 3.4 | Agent takes over | REST 200 OK |
| 3.5 | Agent sends internal note | Private channel receives it |
| 3.5 | **ASSERT: No leak to customer** | Customer topic: CLEAN |
| 3.5 | **ASSERT: No leak in history** | Message history: CLEAN |
| 3.6 | Agent sends public reply | Customer receives instantly |
| 3.7 | Badge update | "Connected to Agent" confirmed |
| 3.8 | CSAT feedback | Rating persisted (5 stars) |
| 3.9 | Connection stability | Both WS still alive |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BACKEND_URL` | `http://localhost:8080` | Backend API base URL |
| `FRONTEND_URL` | `http://localhost:80` | Frontend base URL |
| `POSTGRES_HOST` | `localhost` | PostgreSQL host |
| `POSTGRES_PORT` | `5432` | PostgreSQL port |
| `POSTGRES_USER` | `postgres` | PostgreSQL user |
| `POSTGRES_DB` | `ai_customer_support_chatbot` | Database name |
| `WS_URL` | `http://localhost:8080` | WebSocket URL |
| `TEST_TIMEOUT` | `45000` | Global test timeout (ms) |

## Prerequisites

- Docker & Docker Compose
- `curl`, `nc` (netcat)
- Node.js 18+ (for Part 3 WebSocket tests)

## CI/CD Integration

```yaml
# GitHub Actions
- name: Staging verification
  run: |
    docker compose -f docker-compose.staging.yml up --build -d
    sleep 30
    bash tests/staging/run-all.sh --skip-ws
  env:
    BACKEND_URL: http://localhost:8080
```

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Backend not reachable | `docker compose -f docker-compose.staging.yml logs backend` |
| PostgreSQL connection refused | Check port 5432: `lsof -i :5432` |
| WebSocket test fails | Verify Node.js: `node --version` |
| RBAC shows unexpected 401 | Check StagingDataSeeder: query users table |
| Seed data missing | Verify `StagingDataSeeder` ran: check app logs |
