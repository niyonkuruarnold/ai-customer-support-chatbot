#!/usr/bin/env bash
# ============================================================
# Master Staging Verification Runner
# ============================================================
# Runs all 3 test parts in sequence:
#   Part 1: Service Healthcheck & Container Status
#   Part 2: Multi-Role RBAC Verification
#   Part 3: WebSocket E2E Messaging Test
#
# Usage:
#   bash tests/staging/run-all.sh
#   bash tests/staging/run-all.sh --wait 30        # Wait 30s before testing
#   bash tests/staging/run-all.sh --skip-ws        # Skip WebSocket test
#   bash tests/staging/run-all.sh --part 2         # Run only Part 2
# ============================================================

set -euo pipefail

# ─── Configuration ──────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:80}"
WAIT_SECONDS=0
SKIP_WS=false
RUN_PART=""
TOTAL_PASSED=0
TOTAL_FAILED=0

# ─── Colors ─────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

# ─── Parse arguments ────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case $1 in
        --wait)
            WAIT_SECONDS="$2"
            shift 2
            ;;
        --skip-ws)
            SKIP_WS=true
            shift
            ;;
        --part)
            RUN_PART="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--wait SECONDS] [--skip-ws] [--part N]"
            exit 1
            ;;
    esac
done

# ─── Banner ─────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║  AI Customer Support Chatbot — Staging Verification Suite    ║${NC}"
echo -e "${BOLD}${BLUE}║                                                              ║${NC}"
echo -e "${BOLD}${BLUE}║  Backend:  ${BACKEND_URL}${NC}"
echo -e "${BOLD}${BLUE}║  Frontend: ${FRONTEND_URL}${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Export environment for all scripts
export BACKEND_URL FRONTEND_URL

# ─── Wait for services ──────────────────────────────────────
if [ "$WAIT_SECONDS" -gt 0 ]; then
    echo -e "${YELLOW}Waiting ${WAIT_SECONDS} seconds for services to start...${NC}\n"
    sleep "$WAIT_SECONDS"
fi

# ─── Run Part 1: Service Healthcheck ────────────────────────
if [ -z "$RUN_PART" ] || [ "$RUN_PART" = "1" ]; then
    echo -e "\n${BOLD}${BLUE}Running Part 1: Service Healthcheck...${NC}\n"
    if bash "${SCRIPT_DIR}/01-service-healthcheck.sh"; then
        TOTAL_PASSED=$((TOTAL_PASSED + 1))
    else
        TOTAL_FAILED=$((TOTAL_FAILED + 1))
    fi
fi

# ─── Run Part 2: RBAC Verification ──────────────────────────
if [ -z "$RUN_PART" ] || [ "$RUN_PART" = "2" ]; then
    echo -e "\n${BOLD}${BLUE}Running Part 2: RBAC Verification...${NC}\n"
    if bash "${SCRIPT_DIR}/02-rbac-verification.sh"; then
        TOTAL_PASSED=$((TOTAL_PASSED + 1))
    else
        TOTAL_FAILED=$((TOTAL_FAILED + 1))
    fi
fi

# ─── Run Part 3: WebSocket E2E Test ─────────────────────────
if [ "$SKIP_WS" = false ]; then
    if [ -z "$RUN_PART" ] || [ "$RUN_PART" = "3" ]; then
        echo -e "\n${BOLD}${BLUE}Running Part 3: WebSocket E2E Test...${NC}\n"

        # Check if Node.js is available
        if command -v node &>/dev/null; then
            NODE_VERSION=$(node --version 2>/dev/null)
            echo -e "  Node.js version: ${NODE_VERSION}\n"

            if node "${SCRIPT_DIR}/03-websocket-e2e-test.js"; then
                TOTAL_PASSED=$((TOTAL_PASSED + 1))
            else
                TOTAL_FAILED=$((TOTAL_FAILED + 1))
            fi
        else
            echo -e "  ${YELLOW}⚠ Node.js not found — skipping WebSocket test${NC}\n"
        fi
    fi
else
    echo -e "\n${YELLOW}Skipping Part 3: WebSocket E2E Test (--skip-ws)${NC}\n"
fi

# ─── Final Summary ──────────────────────────────────────────
echo ""
echo -e "${BOLD}${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║  Staging Verification — Final Summary                       ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  ${GREEN}Parts passed:  $TOTAL_PASSED${NC}"
echo -e "  ${RED}Parts failed:  $TOTAL_FAILED${NC}"
echo ""

if [ "$TOTAL_FAILED" -gt 0 ]; then
    echo -e "${RED}${BOLD}⚠ Verification finished with failures.${NC}\n"
    exit 1
else
    echo -e "${GREEN}${BOLD}✓ All staging verification checks passed!${NC}\n"
    exit 0
fi
