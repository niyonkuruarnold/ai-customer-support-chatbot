# Staging Deployment Verification Test Suite

Automated verification tests for the AI Customer Support Chatbot staging environment.

## Prerequisites

- Docker & Docker Compose running the staging stack
- `curl`, `nc` (netcat) available on PATH
- Node.js 18+ (for WebSocket E2E tests)

## Quick Start

```bash
# Start the staging environment
docker compose -f docker-compose.staging.yml up --build -d

# Wait for services to stabilize
sleep 30

# Run all tests
bash tests/staging/run-all.sh

# Or run individual parts
bash tests/staging/run-all.sh --part 1    # Healthcheck only
bash tests/staging/run-all.sh --part 2    # RBAC only
bash tests/staging/run-all.sh --part 3    # WebSocket E2E only
bash tests/staging/run-all.sh --skip-ws   # Skip WebSocket test
```

## Test Parts

### Part 1: Service Healthcheck (`01-service-healthcheck.sh`)

Verifies all containers are running and healthy:

| Check | Method | Expected |
|-------|--------|----------|
| PostgreSQL connectivity | `pg_isready` / TCP | Accepting connections |
| Backend health | `GET /api/health` | 200 OK |
| Backend detail | `GET /api/health/detail` | 200 + DB status |
| Frontend accessibility | `GET /` | 200 HTML |
| API proxy through Nginx | `GET /api/health` via frontend | 200 OK |
| StagingDataSeeder | DB query `SELECT COUNT(*) FROM users` | 5 rows |
| pgvector extension | DB query | Installed |

### Part 2: Multi-Role RBAC (`02-rbac-verification.sh`)

Tests each seeded role's access permissions:

| Role | Email | Password | Access |
|------|-------|----------|--------|
| Admin | `admin` | `admin123` | Full access (KB, audit, tickets, analytics, exports) |
| Manager | `admin` | `admin123` | Analytics, exports, agent workspace |
| Agent | `agent` | `agent123` | Agent workspace, tickets (KB blocked) |
| Customer | (no auth) | — | Chat only (agent/admin blocked) |

### Part 3: WebSocket E2E (`03-websocket-e2e-test.js`)

Simulates real-time chat between Customer and Agent:

1. Customer connects → sends message → session created
2. Customer requests escalation → status change broadcast
3. Agent connects → takes over conversation
4. Agent sends **internal note** → verified NOT on customer topic
5. Agent sends **public message** → verified received by customer
6. CSAT feedback submission
7. Message history integrity check

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
| `TEST_TIMEOUT` | `30000` | Global test timeout (ms) |

## CI/CD Integration

```yaml
# GitHub Actions example
- name: Run staging tests
  run: |
    docker compose -f docker-compose.staging.yml up --build -d
    sleep 30
    bash tests/staging/run-all.sh
  env:
    BACKEND_URL: http://localhost:8080
    FRONTEND_URL: http://localhost:80
```

## Troubleshooting

**Backend not reachable:**
- Check `docker compose -f docker-compose.staging.yml logs backend`
- Ensure GEMINI_API_KEY is set in `.env`

**PostgreSQL connection refused:**
- Check `docker compose -f docker-compose.staging.yml logs postgres`
- Verify port 5432 is not in use

**WebSocket test fails:**
- Ensure Node.js 18+ is installed: `node --version`
- Check WebSocket endpoint: `curl http://localhost:8080/ws/info`

**RBAC tests show unexpected 401/403:**
- Verify StagingDataSeeder ran: check user count in DB
- Check SecurityConfig for endpoint mappings
