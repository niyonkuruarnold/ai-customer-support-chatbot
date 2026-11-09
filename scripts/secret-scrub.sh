#!/usr/bin/env bash
# ============================================================
# CODAFRIQA — Secret & Security Scrubbing Script
# ============================================================
# Standalone script to scan the codebase for hardcoded secrets.
# Run independently or as part of final-submission.sh
#
# Usage:
#   chmod +x scripts/secret-scrub.sh
#   ./scripts/secret-scrub.sh
#   ./scripts/secret-scrub.sh --json    # Machine-readable output
# ============================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

JSON_OUTPUT=false
[[ "${1:-}" == "--json" ]] && JSON_OUTPUT=true

ISSUES=0
WARNINGS=0

log_pass() { echo -e "${GREEN}  ✓ PASS${NC} — $1"; }
log_fail() { echo -e "${RED}  ✗ FAIL${NC} — $1"; ISSUES=$((ISSUES + 1)); }
log_warn() { echo -e "${YELLOW}  ⚠ WARN${NC} — $1"; WARNINGS=$((WARNINGS + 1)); }

echo -e "${BOLD}${CYAN}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║   CODAFRIQA — Security & Secret Scrubbing Report     ║${NC}"
echo -e "${BOLD}${CYAN}║   $(date -u +%Y-%m-%dT%H:%M:%SZ)                                  ║${NC}"
echo -e "${BOLD}${CYAN}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

# ── File extensions to scan ────────────────────────────────
SCAN_EXTS="*.java,*.vue,*.js,*.ts,*.yml,*.yaml,*.properties,*.xml,*.json,*.sh"
EXCLUDE_DIRS="--exclude-dir=node_modules --exclude-dir=target --exclude-dir=.git --exclude-dir=dist"

# ── 1. Google API Keys ────────────────────────────────────
echo -e "${BOLD}[1/10] Google API Keys (AIza...)${NC}"
HITS=$(grep -rn $EXCLUDE_DIRS --include="*.java" --include="*.vue" --include="*.js" \
    --include="*.ts" --include="*.yml" --include="*.properties" \
    -E "AIza[0-9A-Za-z_-]{35}" . 2>/dev/null | \
    grep -v "your-" | grep -v "placeholder" | grep -v "\${" || true)
if [[ -n "$HITS" ]]; then
    log_fail "Google API key found in source code"
    echo "$HITS" | head -5 | sed 's/^/    /'
else
    log_pass "No Google API keys in source"
fi

# ── 2. OpenAI API Keys ───────────────────────────────────
echo -e "${BOLD}[2/10] OpenAI API Keys (sk-...)${NC}"
HITS=$(grep -rn $EXCLUDE_DIRS --include="*.java" --include="*.vue" --include="*.js" \
    --include="*.ts" --include="*.yml" --include="*.properties" \
    -E "sk-[0-9A-Za-z]{32,}" . 2>/dev/null | grep -v "your-" || true)
if [[ -n "$HITS" ]]; then
    log_fail "OpenAI API key found"
    echo "$HITS" | head -5 | sed 's/^/    /'
else
    log_pass "No OpenAI API keys in source"
fi

# ── 3. AWS Credentials ────────────────────────────────────
echo -e "${BOLD}[3/10] AWS Credentials${NC}"
HITS=$(grep -rn $EXCLUDE_DIRS --include="*.java" --include="*.vue" --include="*.js" \
    --include="*.ts" --include="*.yml" --include="*.properties" \
    -E "(AKIA[0-9A-Z]{16}|aws_secret_access_key)" . 2>/dev/null | \
    grep -v "your-" | grep -v "placeholder" || true)
if [[ -n "$HITS" ]]; then
    log_fail "AWS credentials found"
    echo "$HITS" | head -5 | sed 's/^/    /'
else
    log_pass "No AWS credentials in source"
fi

# ── 4. GitHub Personal Access Tokens ──────────────────────
echo -e "${BOLD}[4/10] GitHub PATs (ghp_...)${NC}"
HITS=$(grep -rn $EXCLUDE_DIRS --include="*.java" --include="*.vue" --include="*.js" \
    --include="*.ts" --include="*.yml" --include="*.properties" \
    -E "ghp_[0-9A-Za-z]{36}" . 2>/dev/null || true)
if [[ -n "$HITS" ]]; then
    log_fail "GitHub PAT found"
    echo "$HITS" | head -5 | sed 's/^/    /'
else
    log_pass "No GitHub PATs in source"
fi

# ── 5. Private Keys ───────────────────────────────────────
echo -e "${BOLD}[5/10] Private Key Headers${NC}"
HITS=$(grep -rn $EXCLUDE_DIRS --include="*.java" --include="*.vue" --include="*.js" \
    --include="*.ts" --include="*.yml" --include="*.properties" \
    -E "BEGIN (RSA |EC |DSA )?PRIVATE KEY" . 2>/dev/null || true)
if [[ -n "$HITS" ]]; then
    log_fail "Private key header found"
    echo "$HITS" | head -5 | sed 's/^/    /'
else
    log_pass "No private keys in source"
fi

# ── 6. Hardcoded Database Passwords ───────────────────────
echo -e "${BOLD}[6/10] Hardcoded Database Passwords${NC}"
HITS=$(grep -rn $EXCLUDE_DIRS --include="*.java" --include="*.vue" --include="*.js" \
    --include="*.ts" --include="*.yml" --include="*.properties" \
    -iE "datasource.password\s*[=:]\s*\"[^\"]{6,}\"" . 2>/dev/null | \
    grep -v "\${" | grep -v "postgres" | grep -v "your-" | grep -v "placeholder" || true)
if [[ -n "$HITS" ]]; then
    log_fail "Hardcoded database password found"
    echo "$HITS" | head -5 | sed 's/^/    /'
else
    log_pass "No hardcoded DB passwords"
fi

# ── 7. JWT Secrets ────────────────────────────────────────
echo -e "${BOLD}[7/10] JWT Secrets${NC}"
HITS=$(grep -rn $EXCLUDE_DIRS --include="*.java" --include="*.vue" --include="*.js" \
    --include="*.ts" --include="*.yml" --include="*.properties" \
    -iE "jwt[_-]?secret\s*[=:]\s*\"[^\"]{8,}\"" . 2>/dev/null | \
    grep -v "\${" | grep -v "your-" | grep -v "placeholder" || true)
if [[ -n "$HITS" ]]; then
    log_fail "Hardcoded JWT secret found"
    echo "$HITS" | head -5 | sed 's/^/    /'
else
    log_pass "No hardcoded JWT secrets"
fi

# ── 8. Tracked .env Files ─────────────────────────────────
echo -e "${BOLD}[8/10] Tracked .env Files${NC}"
TRACKED=$(git ls-files | grep -E "\.env$|\.env\." | grep -v "\.env\.example" || true)
if [[ -n "$TRACKED" ]]; then
    log_fail "Tracked .env file(s) found:"
    echo "$TRACKED" | sed 's/^/    /'
else
    log_pass "No .env files tracked in git"
fi

# ── 9. .gitignore Coverage ────────────────────────────────
echo -e "${BOLD}[9/10] .gitignore Coverage${NC}"
MISSING=0
for entry in ".env" ".env.local" ".env.*.local" "node_modules/" "target/" "*.log"; do
    if ! grep -qF "$entry" .gitignore 2>/dev/null; then
        log_warn ".gitignore missing: $entry"
        MISSING=$((MISSING + 1))
    fi
done
if [[ $MISSING -eq 0 ]]; then
    log_pass ".gitignore covers all required patterns"
fi

# ── 10. Git History Scan ──────────────────────────────────
echo -e "${BOLD}[10/10] Git History Leak Scan${NC}"
LEAKS=$(git log --all --diff-filter=A -p 2>/dev/null | \
    grep -E "^\+.*(AIza[0-9A-Za-z_-]{35}|sk-[0-9A-Za-z]{32,}|PRIVATE KEY-----)" | \
    head -5 || true)
if [[ -n "$LEAKS" ]]; then
    log_warn "Potential secrets in git history (review recommended)"
    echo "$LEAKS" | head -3 | sed 's/^/    /'
else
    log_pass "No obvious secrets in git history"
fi

# ── Summary ───────────────────────────────────────────────
echo ""
echo -e "${BOLD}═══════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  SCRUBBING COMPLETE${NC}"
echo -e "${BOLD}═══════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  Issues:   ${RED}${ISSUES}${NC}"
echo -e "  Warnings: ${YELLOW}${WARNINGS}${NC}"
echo ""

if [[ $ISSUES -gt 0 ]]; then
    echo -e "${RED}${BOLD}  ✗ SECURITY ISSUES DETECTED — Do not submit until resolved!${NC}"
    echo ""
    echo -e "  Remediation steps:"
    echo -e "    1. Move secrets to .env file (already in .gitignore)"
    echo -e "    2. Replace hardcoded values with \${ENV_VAR} references"
    echo -e "    3. If secrets were committed: git filter-repo --path <file> --invert-paths"
    echo -e "    4. Rotate any exposed credentials immediately"
    exit 1
elif [[ $WARNINGS -gt 0 ]]; then
    echo -e "${YELLOW}${BOLD}  ⚠  WARNINGS PRESENT — Review recommended before submission${NC}"
    exit 0
else
    echo -e "${GREEN}${BOLD}  ✓  ALL CHECKS PASSED — Safe to submit${NC}"
    exit 0
fi
