#!/usr/bin/env bash
# ============================================================
# Staging Verification — Master Runner
# ============================================================
# Runs all test parts in sequence with structured output.
#
# Usage:
#   bash tests/staging/run-all.sh                    # Run all parts
#   bash tests/staging/run-all.sh --wait 30          # Wait 30s first
#   bash tests/staging/run-all.sh --part 1           # Run only Part 1
#   bash tests/staging/run-all.sh --skip-ws          # Skip WebSocket test
#   bash tests/staging/run-all.sh --part 2 --part 3  # Run Parts 2+3
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:80}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'

WAIT_SECONDS=0
SKIP_WS=false
PARTS=()

# Parse args
while [[ $# -gt 0 ]]; do
    case $1 in
        --wait)     WAIT_SECONDS="$2"; shift 2 ;;
        --skip-ws)  SKIP_WS=true; shift ;;
        --part)     PARTS+=("$2"); shift 2 ;;
        *)          echo "Unknown: $1"; exit 1 ;;
    esac
done
[ ${#PARTS[@]} -eq 0 ] && PARTS=(1 2 3)

export BACKEND_URL FRONTEND_URL

echo ""
echo -e "${BOLD}${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║  AI Customer Support Chatbot — Staging Verification Suite   ║${NC}"
echo -e "${BOLD}${BLUE}║                                                              ║${NC}"
echo -e "${BOLD}${BLUE}║  Backend:  ${BACKEND_URL}${NC}"
echo -e "${BOLD}${BLUE}║  Frontend: ${FRONTEND_URL}${NC}"
echo -e "${BOLD}${BLUE}║  Parts:    ${PARTS[*]}${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

if [ "$WAIT_SECONDS" -gt 0 ]; then
    echo -e "${YELLOW}Waiting ${WAIT_SECONDS}s for services to stabilize...${NC}\n"
    sleep "$WAIT_SECONDS"
fi

PASSED_PARTS=0
FAILED_PARTS=0

for PART in "${PARTS[@]}"; do
    case $PART in
        1)
            echo -e "\n${BOLD}${BLUE}▶ Part 1: Service Healthcheck${NC}\n"
            if bash "${SCRIPT_DIR}/01-service-healthcheck.sh"; then
                PASSED_PARTS=$((PASSED_PARTS + 1))
            else
                FAILED_PARTS=$((FAILED_PARTS + 1))
            fi
            ;;
        2)
            echo -e "\n${BOLD}${BLUE}▶ Part 2: RBAC Verification${NC}\n"
            if bash "${SCRIPT_DIR}/02-rbac-verification.sh"; then
                PASSED_PARTS=$((PASSED_PARTS + 1))
            else
                FAILED_PARTS=$((FAILED_PARTS + 1))
            fi
            ;;
        3)
            if [ "$SKIP_WS" = true ]; then
                echo -e "\n${YELLOW}▶ Part 3: WebSocket E2E (SKIPPED)${NC}\n"
            else
                echo -e "\n${BOLD}${BLUE}▶ Part 3: WebSocket E2E Test${NC}\n"
                if command -v node &>/dev/null; then
                    info "Node.js $(node --version)"
                    if node "${SCRIPT_DIR}/03-websocket-e2e-test.js"; then
                        PASSED_PARTS=$((PASSED_PARTS + 1))
                    else
                        FAILED_PARTS=$((FAILED_PARTS + 1))
                    fi
                else
                    echo -e "  ${YELLOW}⚠ Node.js not found — skipping${NC}\n"
                fi
            fi
            ;;
    esac
done

# Final summary
echo ""
echo -e "${BOLD}${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║  Staging Verification — Final Summary                       ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}\n"
echo -e "  ${GREEN}Parts passed:  $PASSED_PARTS${NC}"
echo -e "  ${RED}Parts failed:  $FAILED_PARTS${NC}\n"

[ "$FAILED_PARTS" -gt 0 ] && {
    echo -e "${RED}${BOLD}⚠ Verification finished with failures.${NC}\n"
    exit 1
} || {
    echo -e "${GREEN}${BOLD}✓ All staging verification checks passed!${NC}\n"
    exit 0
}
