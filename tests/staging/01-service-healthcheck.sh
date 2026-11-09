#!/usr/bin/env bash
# ============================================================
# Part 1: Container Infrastructure & Health Audit
# ============================================================
# Verifies all staging containers, database connectivity,
# migrations, seeder execution, and endpoint health.
#
# Usage:
#   bash tests/staging/01-service-healthcheck.sh [--wait SECONDS]
# ============================================================

set -euo pipefail

# ─── Configuration ──────────────────────────────────────────
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:80}"
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-ai_customer_support_chatbot}"

# Parse --wait flag
WAIT_SECONDS=0
if [ "${1:-}" = "--wait" ] && [ -n "${2:-}" ]; then
    WAIT_SECONDS="$2"
fi

# ─── Colors & Helpers ───────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
PASSED=0; FAILED=0; WARNED=0

pass() { echo -e "  ${GREEN}✓ PASS${NC} $1"; PASSED=$((PASSED + 1)); }
fail() { echo -e "  ${RED}✗ FAIL${NC} $1"; FAILED=$((FAILED + 1)); }
warn() { echo -e "  ${YELLOW}⚠ WARN${NC} $1"; WARNED=$((WARNED + 1)); }
info() { echo -e "  ${CYAN}ℹ INFO${NC} $1"; }
header() { echo -e "\n${BOLD}${BLUE}━━━ $1 ━━━${NC}"; }

# ─── Wait ───────────────────────────────────────────────────
if [ "$WAIT_SECONDS" -gt 0 ]; then
    echo -e "${YELLOW}Waiting ${WAIT_SECONDS}s for services to start...${NC}"
    sleep "$WAIT_SECONDS"
fi

echo -e "${BOLD}${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║  Part 1: Container Infrastructure & Health Audit        ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"

# Detect docker compose command
COMPOSE_CMD=""
if command -v docker &>/dev/null; then
    if docker compose version &>/dev/null 2>&1; then
        COMPOSE_CMD="docker compose -f docker-compose.staging.yml"
    elif command -v docker-compose &>/dev/null; then
        COMPOSE_CMD="docker-compose -f docker-compose.staging.yml"
    fi
fi

PG_CONTAINER=""
if [ -n "$COMPOSE_CMD" ]; then
    PG_CONTAINER=$($COMPOSE_CMD ps postgres --format "{{.Name}}" 2>/dev/null | head -1 || true)
fi

# ============================================================
header "1.1  Docker Container Status"
# ============================================================

if [ -n "$COMPOSE_CMD" ]; then
    for SVC in postgres backend frontend; do
        STATE=$($COMPOSE_CMD ps "$SVC" --format "{{.State}}" 2>/dev/null || echo "not_found")
        INFO=$($COMPOSE_CMD ps "$SVC" --format "{{.Status}}" 2>/dev/null || echo "")
        if [ "$STATE" = "running" ]; then
            pass "$SVC container running ($INFO)"
        elif [ "$STATE" = "not_found" ]; then
            warn "$SVC container not in compose stack"
        else
            fail "$SVC container state: $STATE"
        fi
    done
    echo ""
    $COMPOSE_CMD ps --format "table {{.Name}}\t{{.State}}\t{{.Status}}" 2>/dev/null || true
else
    warn "Docker not available — skipping container checks"
fi

# ============================================================
header "1.2  PostgreSQL Database Connectivity"
# ============================================================

# pg_isready
if command -v pg_isready &>/dev/null; then
    if pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" &>/dev/null; then
        pass "pg_isready: PostgreSQL accepting connections"
    else
        fail "pg_isready: PostgreSQL NOT accepting connections"
    fi
elif [ -n "$PG_CONTAINER" ]; then
    if docker exec "$PG_CONTAINER" pg_isready -U "$POSTGRES_USER" &>/dev/null; then
        pass "pg_isready (docker exec): PostgreSQL accepting connections"
    else
        fail "pg_isready (docker exec): PostgreSQL NOT accepting connections"
    fi
else
    warn "pg_isready not available"
fi

# TCP connectivity
if command -v nc &>/dev/null; then
    nc -z -w3 "$POSTGRES_HOST" "$POSTGRES_PORT" 2>/dev/null && \
        pass "TCP ${POSTGRES_HOST}:${POSTGRES_PORT} reachable" || \
        fail "TCP ${POSTGRES_HOST}:${POSTGRES_PORT} NOT reachable"
fi

# ============================================================
header "1.3  Spring Boot Backend Health"
# ============================================================

echo ""
# /actuator/health
HTTP=$(curl -s -o /tmp/hc_body.txt -w "%{http_code}" --max-time 5 "${BACKEND_URL}/actuator/health" 2>/dev/null || echo "000")
if [ "$HTTP" = "200" ]; then
    pass "GET /actuator/health → 200"
    info "$(cat /tmp/hc_body.txt 2>/dev/null)"
else
    info "GET /actuator/health → $HTTP (actuator may not be enabled)"
fi

# /api/health
HTTP=$(curl -s -o /tmp/hc_body.txt -w "%{http_code}" --max-time 5 "${BACKEND_URL}/api/health" 2>/dev/null || echo "000")
if [ "$HTTP" = "200" ]; then
    pass "GET /api/health → 200"
    info "$(cat /tmp/hc_body.txt 2>/dev/null)"
else
    fail "GET /api/health → $HTTP (expected 200)"
fi

# /api/health/detail
HTTP=$(curl -s -o /tmp/hc_body.txt -w "%{http_code}" --max-time 5 "${BACKEND_URL}/api/health/detail" 2>/dev/null || echo "000")
if [ "$HTTP" = "200" ]; then
    pass "GET /api/health/detail → 200"
    # Check database status in response
    if grep -q '"status":"UP"' /tmp/hc_body.txt 2>/dev/null; then
        pass "Detailed health: database status UP"
    elif grep -q '"database"' /tmp/hc_body.txt 2>/dev/null; then
        info "Detailed health includes database info"
    fi
else
    warn "GET /api/health/detail → $HTTP"
fi

# /api/chat/health
HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${BACKEND_URL}/api/chat/health" 2>/dev/null || echo "000")
[ "$HTTP" = "200" ] && pass "GET /api/chat/health → 200" || warn "GET /api/chat/health → $HTTP"

# ============================================================
header "1.4  Vue 3 / Nginx Frontend"
# ============================================================

HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${FRONTEND_URL}/" 2>/dev/null || echo "000")
if [ "$HTTP" = "200" ]; then pass "GET ${FRONTEND_URL}/ → 200"
else fail "GET ${FRONTEND_URL}/ → $HTTP (expected 200)"; fi

CT=$(curl -s -I --max-time 5 "${FRONTEND_URL}/" 2>/dev/null | grep -i content-type | head -1 || echo "")
echo "$CT" | grep -qi html && pass "Content-Type is HTML (Vue SPA)" || warn "Content-Type: $CT"

# API proxy through Nginx
HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${FRONTEND_URL}/api/health" 2>/dev/null || echo "000")
[ "$HTTP" = "200" ] && pass "Nginx API proxy → 200" || warn "Nginx API proxy → $HTTP"

# Security headers
HEADERS=$(curl -s -I --max-time 5 "${FRONTEND_URL}/" 2>/dev/null || echo "")
echo "$HEADERS" | grep -qi "x-frame-options" && pass "X-Frame-Options header present" || warn "X-Frame-Options header missing"
echo "$HEADERS" | grep -qi "x-content-type-options" && pass "X-Content-Type-Options header present" || warn "X-Content-Type-Options header missing"

# ============================================================
header "1.5  StagingDataSeeder Verification"
# ============================================================

if [ -n "$PG_CONTAINER" ]; then
    echo -e "\n${BOLD}Querying database for seed data...${NC}\n"

    # Users table
    USER_COUNT=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
        "SELECT COUNT(*) FROM users;" 2>/dev/null | tr -d ' \n\r' || echo "")
    if [ -n "$USER_COUNT" ] && [ "$USER_COUNT" -gt 0 ]; then
        pass "users table: $USER_COUNT rows (seeder executed)"
        docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
            "SELECT id, email, role, is_active FROM users ORDER BY id;" 2>/dev/null || true
    else
        warn "users table is empty (seeder may not have run)"
    fi

    # All required tables
    for TABLE in chat_sessions chat_messages chat_feedback support_tickets \
                 ticket_activity_logs audit_logs knowledge_articles vector_store \
                 tools reservations reviews organizations support_teams; do
        COUNT=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
            "SELECT COUNT(*) FROM $TABLE;" 2>/dev/null | tr -d ' \n\r' || echo "error")
        if [ "$COUNT" = "error" ]; then
            warn "Table $TABLE does not exist"
        else
            info "Table $TABLE: $COUNT rows"
        fi
    done
else
    warn "Cannot query database (no container reference)"
fi

# ============================================================
header "1.6  Flyway / Migration Verification"
# ============================================================

echo -e "\n${BOLD}Checking database schema state...${NC}\n"

# Check if flyway_schema_history exists
if [ -n "$PG_CONTAINER" ]; then
    FLYWAY_EXISTS=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
        "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'flyway_schema_history');" 2>/dev/null | tr -d ' \n\r' || echo "false")
    if [ "$FLYWAY_EXISTS" = "true" ]; then
        pass "Flyway schema history table exists"
        FLYWAY_COUNT=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
            "SELECT COUNT(*) FROM flyway_schema_history;" 2>/dev/null | tr -d ' \n\r' || echo "0")
        info "Flyway migrations applied: $FLYWAY_COUNT"
        # Check for failed migrations
        FAILED_MIGR=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false;" 2>/dev/null | tr -d ' \n\r' || echo "0")
        if [ "$FAILED_MIGR" = "0" ]; then
            pass "No failed Flyway migrations"
        else
            fail "$FAILED_MIGR failed Flyway migration(s)"
        fi
    else
        info "Flyway not used (Hibernate ddl-auto=update mode)"
    fi

    # Verify schema has expected columns
    echo -e "\n${BOLD}Verifying schema completeness...${NC}\n"

    # Check users table columns
    COLS=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
        "SELECT column_name FROM information_schema.columns WHERE table_name = 'users' ORDER BY ordinal_position;" 2>/dev/null | tr -d ' \n\r' || echo "")
    for COL in id email password_hash full_name role is_active created_at; do
        echo "$COLS" | grep -q "$COL" && pass "users.$COL column exists" || warn "users.$COL column missing"
    done

    # Check support_tickets table columns
    COLS=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
        "SELECT column_name FROM information_schema.columns WHERE table_name = 'support_tickets' ORDER BY ordinal_position;" 2>/dev/null | tr -d ' \n\r' || echo "")
    for COL in id ticket_reference subject status priority category assigned_agent; do
        echo "$COLS" | grep -q "$COL" && pass "support_tickets.$COL column exists" || warn "support_tickets.$COL column missing"
    done

    # Check ticket_activity_logs table
    COLS=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
        "SELECT column_name FROM information_schema.columns WHERE table_name = 'ticket_activity_logs' ORDER BY ordinal_position;" 2>/dev/null | tr -d ' \n\r' || echo "")
    for COL in id ticket_id actor_name action_type previous_value new_value timestamp customer_visible; do
        echo "$COLS" | grep -q "$COL" && pass "ticket_activity_logs.$COL column exists" || warn "ticket_activity_logs.$COL column missing"
    done

    # Check audit_logs table
    COLS=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
        "SELECT column_name FROM information_schema.columns WHERE table_name = 'audit_logs' ORDER BY ordinal_position;" 2>/dev/null | tr -d ' \n\r' || echo "")
    for COL in id actor_email action_type ip_address correlation_id resource_type timestamp; do
        echo "$COLS" | grep -q "$COL" && pass "audit_logs.$COL column exists" || warn "audit_logs.$COL column missing"
    done

    # Check chat_feedback table
    COLS=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
        "SELECT column_name FROM information_schema.columns WHERE table_name = 'chat_feedback' ORDER BY ordinal_position;" 2>/dev/null | tr -d ' \n\r' || echo "")
    for COL in id session_id rating comment created_at; do
        echo "$COLS" | grep -q "$COL" && pass "chat_feedback.$COL column exists" || warn "chat_feedback.$COL column missing"
    done

    # pgvector extension
    PGVECTOR=$(docker exec "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
        "SELECT extname FROM pg_extension WHERE extname = 'vector';" 2>/dev/null | tr -d ' \n\r' || echo "")
    [ "$PGVECTOR" = "vector" ] && pass "pgvector extension installed" || warn "pgvector extension not found"
else
    warn "Cannot verify schema (no Docker container)"
fi

# ============================================================
header "1.7  API Endpoint Smoke Tests"
# ============================================================

echo -e "\n${BOLD}Testing permitAll endpoints...${NC}\n"

ENDPOINTS=(
    "GET /api/health"
    "GET /api/health/detail"
    "GET /api/chat/health"
    "GET /api/chat/suggested-questions"
    "GET /api/tools"
    "GET /api/maintenance"
)

for EP in "${ENDPOINTS[@]}"; do
    METHOD="${EP%% *}"
    PATH_EP="${EP#* }"
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 -X "$METHOD" "${BACKEND_URL}${PATH_EP}" 2>/dev/null || echo "000")
    if [ "$HTTP" = "200" ] || [ "$HTTP" = "204" ]; then
        pass " $EP → $HTTP"
    else
        warn " $EP → $HTTP"
    fi
done

# ============================================================
# Summary
# ============================================================
echo -e "\n${BOLD}${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║  Part 1 Summary                                        ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════════╝${NC}\n"
echo -e "  ${GREEN}Passed:  $PASSED${NC}"
echo -e "  ${RED}Failed:  $FAILED${NC}"
echo -e "  ${YELLOW}Warned:  $WARNED${NC}\n"

if [ "$FAILED" -gt 0 ]; then
    echo -e "${RED}${BOLD}⚠ Part 1 finished with failures.${NC}\n"
    exit 1
else
    echo -e "${GREEN}${BOLD}✓ Part 1 passed. All services are healthy.${NC}\n"
    exit 0
fi
