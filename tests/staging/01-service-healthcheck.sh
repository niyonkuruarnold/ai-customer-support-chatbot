#!/usr/bin/env bash
# ============================================================
# Part 1: Service Healthcheck & Container Status Verification
# ============================================================
# Verifies that all staging containers are running and healthy:
#   - PostgreSQL (pgvector)
#   - Spring Boot Backend
#   - Vue 3 / Nginx Frontend
#
# Usage:
#   bash tests/staging/01-service-healthcheck.sh
#   bash tests/staging/01-service-healthcheck.sh --wait 30
# ============================================================

set -euo pipefail

# ─── Configuration ──────────────────────────────────────────
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:80}"
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-ai_customer_support_chatbot}"
WAIT_TIMEOUT="${1:---wait}"
WAIT_SECONDS="${2:-0}"

# ─── Colors & Helpers ───────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

PASSED=0
FAILED=0
WARNED=0

pass() { echo -e "  ${GREEN}✓ PASS${NC} $1"; PASSED=$((PASSED + 1)); }
fail() { echo -e "  ${RED}✗ FAIL${NC} $1"; FAILED=$((FAILED + 1)); }
warn() { echo -e "  ${YELLOW}⚠ WARN${NC} $1"; WARNED=$((WARNED + 1)); }
info() { echo -e "  ${CYAN}ℹ INFO${NC} $1"; }
header() { echo -e "\n${BOLD}${BLUE}━━━ $1 ━━━${NC}"; }

# ─── Wait for services (optional) ──────────────────────────
if [ "$WAIT_TIMEOUT" = "--wait" ] && [ "$WAIT_SECONDS" -gt 0 ]; then
    echo -e "${YELLOW}Waiting ${WAIT_SECONDS}s for services to start...${NC}"
    sleep "$WAIT_SECONDS"
fi

# ============================================================
echo -e "${BOLD}${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║  Part 1: Service Healthcheck & Container Status         ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"

# ============================================================
header "1.1  Docker Container Status"
# ============================================================

if command -v docker &>/dev/null; then
    echo -e "\n${BOLD}Checking Docker containers...${NC}\n"

    # Check if compose is available
    if docker compose version &>/dev/null 2>&1; then
        COMPOSE_CMD="docker compose -f docker-compose.staging.yml"
    elif command -v docker-compose &>/dev/null; then
        COMPOSE_CMD="docker-compose -f docker-compose.staging.yml"
    else
        warn "Docker Compose not found — skipping container checks"
        COMPOSE_CMD=""
    fi

    if [ -n "$COMPOSE_CMD" ]; then
        # PostgreSQL container
        PG_STATUS=$($COMPOSE_CMD ps postgres --format "{{.State}}" 2>/dev/null || echo "not_found")
        if [ "$PG_STATUS" = "running" ]; then
            PG_HEALTH=$($COMPOSE_CMD ps postgres --format "{{.Status}}" 2>/dev/null || echo "unknown")
            pass "PostgreSQL container running ($PG_HEALTH)"
        elif [ "$PG_STATUS" = "not_found" ]; then
            warn "PostgreSQL container not found in compose stack"
        else
            fail "PostgreSQL container state: $PG_STATUS"
        fi

        # Backend container
        BE_STATUS=$($COMPOSE_CMD ps backend --format "{{.State}}" 2>/dev/null || echo "not_found")
        if [ "$BE_STATUS" = "running" ]; then
            BE_HEALTH=$($COMPOSE_CMD ps backend --format "{{.Status}}" 2>/dev/null || echo "unknown")
            pass "Spring Boot backend container running ($BE_HEALTH)"
        elif [ "$BE_STATUS" = "not_found" ]; then
            warn "Backend container not found in compose stack"
        else
            fail "Backend container state: $BE_STATUS"
        fi

        # Frontend container
        FE_STATUS=$($COMPOSE_CMD ps frontend --format "{{.State}}" 2>/dev/null || echo "not_found")
        if [ "$FE_STATUS" = "running" ]; then
            FE_HEALTH=$($COMPOSE_CMD ps frontend --format "{{.Status}}" 2>/dev/null || echo "unknown")
            pass "Frontend (Nginx) container running ($FE_HEALTH)"
        elif [ "$FE_STATUS" = "not_found" ]; then
            warn "Frontend container not found in compose stack"
        else
            fail "Frontend container state: $FE_STATUS"
        fi

        # Show full status
        echo -e "\n${BOLD}Full container status:${NC}"
        $COMPOSE_CMD ps --format "table {{.Name}}\t{{.State}}\t{{.Status}}" 2>/dev/null || true
    fi
else
    warn "Docker not available — skipping container checks"
fi

# ============================================================
header "1.2  PostgreSQL Database Connectivity"
# ============================================================

echo -e "\n${BOLD}Testing PostgreSQL connectivity...${NC}\n"

# Method 1: pg_isready (if available)
if command -v pg_isready &>/dev/null; then
    if pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" &>/dev/null; then
        pass "pg_isready: PostgreSQL is accepting connections"
    else
        fail "pg_isready: PostgreSQL is NOT accepting connections"
    fi
else
    info "pg_isready not found — using Docker exec fallback"

    if [ -n "${COMPOSE_CMD:-}" ]; then
        PG_CONTAINER=$($COMPOSE_CMD ps postgres --format "{{.Name}}" 2>/dev/null | head -1)
        if [ -n "$PG_CONTAINER" ]; then
            if docker exec "$PG_CONTAINER" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" &>/dev/null; then
                pass "PostgreSQL is accepting connections (via docker exec)"
            else
                fail "PostgreSQL is NOT accepting connections (via docker exec)"
            fi
        else
            warn "Cannot determine PostgreSQL container name"
        fi
    fi
fi

# Method 2: TCP connection test
if command -v nc &>/dev/null; then
    if nc -z -w3 "$POSTGRES_HOST" "$POSTGRES_PORT" 2>/dev/null; then
        pass "TCP: ${POSTGRES_HOST}:${POSTGRES_PORT} is reachable"
    else
        fail "TCP: ${POSTGRES_HOST}:${POSTGRES_PORT} is NOT reachable"
    fi
elif command -v bash &>/dev/null; then
    if (echo >/dev/tcp/"$POSTGRES_HOST"/"$POSTGRES_PORT") 2>/dev/null; then
        pass "TCP: ${POSTGRES_HOST}:${POSTGRES_PORT} is reachable"
    else
        fail "TCP: ${POSTGRES_HOST}:${POSTGRES_PORT} is NOT reachable"
    fi
fi

# ============================================================
header "1.3  Spring Boot Backend Health"
# ============================================================

echo -e "\n${BOLD}Testing backend endpoints...${NC}\n"

# Test Actuator health (if available)
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${BACKEND_URL}/actuator/health" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "GET /actuator/health → 200 OK"
    HEALTH_BODY=$(curl -s --max-time 5 "${BACKEND_URL}/actuator/health" 2>/dev/null || echo "{}")
    info "Health response: $HEALTH_BODY"
else
    info "GET /actuator/health → $HTTP_CODE (actuator may not be enabled)"
fi

# Test custom health endpoint
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${BACKEND_URL}/api/health" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "GET /api/health → 200 OK"
    HEALTH_BODY=$(curl -s --max-time 5 "${BACKEND_URL}/api/health" 2>/dev/null || echo "{}")
    info "Health response: $HEALTH_BODY"
else
    fail "GET /api/health → $HTTP_CODE (expected 200)"
fi

# Test detailed health endpoint
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${BACKEND_URL}/api/health/detail" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "GET /api/health/detail → 200 OK"
    DETAIL_BODY=$(curl -s --max-time 5 "${BACKEND_URL}/api/health/detail" 2>/dev/null || echo "{}")
    info "Detailed health: $DETAIL_BODY"
else
    warn "GET /api/health/detail → $HTTP_CODE"
fi

# Test chat health (permitAll)
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${BACKEND_URL}/api/chat/health" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "GET /api/chat/health → 200 OK"
else
    warn "GET /api/chat/health → $HTTP_CODE"
fi

# ============================================================
header "1.4  Vue 3 / Nginx Frontend"
# ============================================================

echo -e "\n${BOLD}Testing frontend accessibility...${NC}\n"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${FRONTEND_URL}/" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "GET ${FRONTEND_URL}/ → 200 OK"
    # Verify it's actually an HTML page
    CONTENT_TYPE=$(curl -s -I --max-time 5 "${FRONTEND_URL}/" 2>/dev/null | grep -i "content-type" | head -1 || echo "")
    if echo "$CONTENT_TYPE" | grep -qi "html"; then
        pass "Response Content-Type is HTML (Vue SPA served correctly)"
    else
        warn "Response Content-Type: $CONTENT_TYPE (expected text/html)"
    fi
else
    fail "GET ${FRONTEND_URL}/ → $HTTP_CODE (expected 200)"
fi

# Test API proxy through Nginx
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${FRONTEND_URL}/api/health" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "API proxy: GET ${FRONTEND_URL}/api/health → 200 OK (Nginx→Backend)"
else
    warn "API proxy: GET ${FRONTEND_URL}/api/health → $HTTP_CODE"
fi

# Test static assets caching
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${FRONTEND_URL}/favicon.ico" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "404" ]; then
    pass "Static asset check: /favicon.ico → $HTTP_CODE"
else
    warn "Static asset check: /favicon.ico → $HTTP_CODE"
fi

# ============================================================
header "1.5  StagingDataSeeder Verification"
# ============================================================

echo -e "\n${BOLD}Checking seed data in database...${NC}\n"

# Use docker exec to query PostgreSQL directly
if [ -n "${COMPOSE_CMD:-}" ]; then
    PG_CONTAINER=$($COMPOSE_CMD ps postgres --format "{{.Name}}" 2>/dev/null | head -1)
    if [ -n "$PG_CONTAINER" ]; then
        # Check users table
        USER_COUNT=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c "SELECT COUNT(*) FROM users;" 2>/dev/null | tr -d ' ')
        if [ -n "$USER_COUNT" ] && [ "$USER_COUNT" -gt 0 ]; then
            pass "Users table has $USER_COUNT rows (seeder executed)"
            info "Seed users:"
            docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
                "SELECT id, email, role, is_active FROM users ORDER BY id;" 2>/dev/null || true
        else
            warn "Users table is empty (seeder may not have run yet)"
        fi

        # Check chat_sessions table
        SESSION_COUNT=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c "SELECT COUNT(*) FROM chat_sessions;" 2>/dev/null | tr -d ' ')
        if [ -n "$SESSION_COUNT" ]; then
            info "chat_sessions table: $SESSION_COUNT rows"
        fi

        # Check support_tickets table
        TICKET_COUNT=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c "SELECT COUNT(*) FROM support_tickets;" 2>/dev/null | tr -d ' ')
        if [ -n "$TICKET_COUNT" ]; then
            info "support_tickets table: $TICKET_COUNT rows"
        fi

        # Check vector_store table
        VECTOR_COUNT=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c "SELECT COUNT(*) FROM vector_store;" 2>/dev/null | tr -d ' ')
        if [ -n "$VECTOR_COUNT" ]; then
            info "vector_store table: $VECTOR_COUNT rows"
        fi

        # Check pgvector extension
        PGVECTOR=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c "SELECT extname FROM pg_extension WHERE extname = 'vector';" 2>/dev/null | tr -d ' ')
        if [ "$PGVECTOR" = "vector" ]; then
            pass "pgvector extension is installed"
        else
            warn "pgvector extension not found"
        fi
    fi
else
    warn "Docker not available — skipping database verification"
fi

# ============================================================
# Summary
# ============================================================
echo -e "\n${BOLD}${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║  Part 1 Summary                                        ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  ${GREEN}Passed:  $PASSED${NC}"
echo -e "  ${RED}Failed:  $FAILED${NC}"
echo -e "  ${YELLOW}Warned:  $WARNED${NC}"
echo ""

if [ "$FAILED" -gt 0 ]; then
    echo -e "${RED}${BOLD}⚠ Part 1 finished with failures. Review the output above.${NC}\n"
    exit 1
else
    echo -e "${GREEN}${BOLD}✓ Part 1 passed. All services are healthy.${NC}\n"
    exit 0
fi
