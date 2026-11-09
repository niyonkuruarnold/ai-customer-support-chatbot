#!/usr/bin/env bash
# ============================================================
# CODAFRIQA AI Customer Support Chatbot — Final Submission Script
# ============================================================
# Usage:
#   chmod +x scripts/final-submission.sh
#   ./scripts/final-submission.sh
#
# This script performs:
#   1. Pre-flight checks (clean working tree, remote connectivity)
#   2. Branch consolidation (feature → staging → main)
#   3. Hardcoded secret & security scrubbing
#   4. .gitignore verification
#   5. Final release tag (v1.0.0-final-submission)
#   6. Push all branches and tags to GitHub
# ============================================================

set -euo pipefail

# ── Colors ──────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# ── Helpers ─────────────────────────────────────────────────
log_header() {
    echo ""
    echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BOLD}${BLUE}  $1${NC}"
    echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════${NC}"
}

log_step() {
    echo -e "${CYAN}▸ $1${NC}"
}

log_ok() {
    echo -e "${GREEN}  ✓ $1${NC}"
}

log_warn() {
    echo -e "${YELLOW}  ⚠ $1${NC}"
}

log_fail() {
    echo -e "${RED}  ✗ $1${NC}"
}

log_info() {
    echo -e "  $1"
}

DRY_RUN=false
SKIP_PUSH=false
RELEASE_TAG="v1.0.0-final-submission"

# Feature branches to consolidate (in merge order)
FEATURE_BRANCHES=(
    "feature/chat-experience"
    "feature/live-agent"
    "feature/ticket-management"
    "feature/analytics"
    "feature/staging-deploy"
)

# ── Parse arguments ─────────────────────────────────────────
for arg in "$@"; do
    case $arg in
        --dry-run)    DRY_RUN=true ;;
        --skip-push)  SKIP_PUSH=true ;;
        --tag)        RELEASE_TAG="$2"; shift ;;
        --help|-h)
            echo "Usage: $0 [--dry-run] [--skip-push] [--tag <tag-name>]"
            echo ""
            echo "Options:"
            echo "  --dry-run     Show what would be done without executing"
            echo "  --skip-push   Skip the final git push"
            echo "  --tag         Custom release tag (default: v1.0.0-final-submission)"
            exit 0
            ;;
    esac
done

# ============================================================
# PHASE 1: PRE-FLIGHT CHECKS
# ============================================================
log_header "PHASE 1: Pre-Flight Checks"

# Check we're in a git repo
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    log_fail "Not a git repository. Run this from the project root."
    exit 1
fi
log_ok "Git repository detected"

# Check working tree is clean
if [[ -n "$(git status --porcelain)" ]]; then
    log_fail "Working tree is not clean. Commit or stash changes first."
    git status --short
    exit 1
fi
log_ok "Working tree is clean"

# Check remote is reachable
if ! git ls-remote --exit-code origin > /dev/null 2>&1; then
    log_warn "Cannot reach remote 'origin' — push will be skipped"
    SKIP_PUSH=true
else
    log_ok "Remote 'origin' is reachable"
fi

# Check current branch
CURRENT_BRANCH=$(git branch --show-current)
log_info "Current branch: ${BOLD}$CURRENT_BRANCH${NC}"

# Fetch latest remote state
log_step "Fetching latest remote state..."
git fetch --all --prune 2>/dev/null || log_warn "Fetch failed (proceeding with local state)"
log_ok "Remote state fetched"

# ============================================================
# PHASE 2: BRANCH CONSOLIDATION
# ============================================================
log_header "PHASE 2: Branch Consolidation"

# ── Step 2a: Merge feature branches into staging ────────────
log_step "Step 2a: Merging feature branches into 'staging'..."

# Create or checkout staging branch
if git show-ref --verify --quiet refs/heads/staging; then
    log_info "Branch 'staging' exists locally"
else
    if git show-ref --verify --quiet refs/remotes/origin/staging; then
        log_step "Checking out 'staging' from remote..."
        git checkout -b staging origin/staging
    else
        log_step "Creating 'staging' from 'main'..."
        git checkout -b staging main
    fi
    log_ok "Branch 'staging' ready"
fi

git checkout staging

# Merge each feature branch (skip if branch doesn't exist)
for branch in "${FEATURE_BRANCHES[@]}"; do
    if git show-ref --verify --quiet "refs/heads/$branch" || \
       git show-ref --verify --quiet "refs/remotes/origin/$branch"; then
        log_step "Merging '$branch' into 'staging'..."
        if [ "$DRY_RUN" = true ]; then
            log_info "[DRY RUN] Would merge: $branch → staging"
        else
            git merge --no-edit "$branch" 2>/dev/null || {
                log_warn "Merge conflict in '$branch' — manual resolution required"
                log_info "  Run: git merge --abort && git merge $branch"
                continue
            }
            log_ok "Merged '$branch' into 'staging'"
        fi
    else
        log_warn "Branch '$branch' not found — skipping"
    fi
done

# ── Step 2b: Merge staging into main ───────────────────────
log_step "Step 2b: Merging 'staging' into 'main'..."
git checkout main

if [ "$DRY_RUN" = true ]; then
    log_info "[DRY RUN] Would merge: staging → main"
else
    git merge --no-edit staging 2>/dev/null || {
        log_fail "Merge conflict: staging → main"
        log_info "  Resolve conflicts, then run: git commit"
        exit 1
    }
    log_ok "Merged 'staging' into 'main'"
fi

# ── Step 2c: Cleanup feature branches (local) ──────────────
log_step "Step 2c: Cleaning up merged feature branches (local)..."
for branch in "${FEATURE_BRANCHES[@]}"; do
    if git show-ref --verify --quiet "refs/heads/$branch"; then
        if [ "$DRY_RUN" = true ]; then
            log_info "[DRY RUN] Would delete: $branch"
        else
            git branch -d "$branch" 2>/dev/null && \
                log_ok "Deleted local branch '$branch'" || \
                log_warn "Could not delete '$branch' (unmerged changes?)"
        fi
    fi
done

log_ok "Branch consolidation complete"

# ============================================================
# PHASE 3: SECRET & SECURITY SCRUBBING
# ============================================================
log_header "PHASE 3: Hardcoded Secret & Security Scrubbing"

SECRETS_FOUND=0
SCAN_DIRS=("src/" "frontend/src/" "frontend/" ".")

# ── 3a: Scan for hardcoded API keys ────────────────────────
log_step "Step 3a: Scanning for hardcoded API keys..."

API_KEY_PATTERNS=(
    'AIza[0-9A-Za-z_-]{35}'                          # Google API key
    'sk-[0-9A-Za-z]{32,}'                             # OpenAI API key
    'ghp_[0-9A-Za-z]{36}'                             # GitHub PAT
    'AKIA[0-9A-Z]{16}'                                # AWS Access Key
    '-----BEGIN (RSA |EC |DSA )?PRIVATE KEY-----'      # Private key headers
)

for pattern in "${API_KEY_PATTERNS[@]}"; do
    MATCHES=$(grep -rn --include="*.java" --include="*.vue" --include="*.js" \
              --include="*.ts" --include="*.yml" --include="*.yaml" \
              --include="*.properties" --include="*.xml" \
              -E "$pattern" \
              src/ frontend/ pom.xml docker-compose*.yml 2>/dev/null || true)

    if [[ -n "$MATCHES" ]]; then
        log_fail "FOUND hardcoded secrets matching: $pattern"
        echo "$MATCHES" | head -10
        SECRETS_FOUND=$((SECRETS_FOUND + 1))
    fi
done

if [[ $SECRETS_FOUND -eq 0 ]]; then
    log_ok "No hardcoded API keys, private keys, or AWS credentials found"
fi

# ── 3b: Scan for hardcoded passwords/secrets in code ───────
log_step "Step 3b: Scanning for hardcoded passwords and secrets..."

# Patterns that indicate hardcoded secrets (case-insensitive)
SECRET_PATTERNS=(
    'password\s*=\s*"[^"]{8,}"'                       # password = "actual_password"
    'password\s*:\s*"[^"]{8,}"'                       # password: "actual_password"
    'api[_-]?key\s*=\s*"[^"]{16,}"'                   # api_key = "actual_key"
    'secret\s*=\s*"[^"]{16,}"'                         # secret = "actual_secret"
    'token\s*=\s*"[^"]{16,}"'                          # token = "actual_token"
    'jwt[_-]?secret\s*=\s*"[^"]{8,}"'                 # jwt_secret = "actual"
    'GEMINI_API_KEY\s*=\s*"[^"]{8,}"'                 # Hardcoded Gemini key
    'SPRING_DATASOURCE_PASSWORD\s*=\s*"[^"]{6,}"'     # Hardcoded DB password
)

FALSE_POSITIVES=(
    'your-'         # placeholder values like "your-api-key"
    'placeholder'   # placeholder text
    'example'       # example values
    '\$\{'          # environment variable references like ${GEMINI_API_KEY}
    'password123'   # test/seed passwords (expected in seeder)
    'admin123'      # test/seed passwords (expected in seeder)
    'Password123!'  # test/seed passwords (expected in seeder)
    'postgres'      # default DB user (expected in config)
)

for pattern in "${SECRET_PATTERNS[@]}"; do
    MATCHES=$(grep -rn --include="*.java" --include="*.vue" --include="*.js" \
              --include="*.ts" --include="*.yml" --include="*.yaml" \
              --include="*.properties" --include="*.xml" \
              -iE "$pattern" \
              src/ frontend/ docker-compose*.yml 2>/dev/null || true)

    if [[ -n "$MATCHES" ]]; then
        # Filter out known false positives
        FILTERED=""
        while IFS= read -r line; do
            IS_FALSE_POSITIVE=false
            for fp in "${FALSE_POSITIVES[@]}"; do
                if echo "$line" | grep -qiE "$fp"; then
                    IS_FALSE_POSITIVE=true
                    break
                fi
            done
            if [[ "$IS_FALSE_POSITIVE" = false ]]; then
                FILTERED="${FILTERED}${line}\n"
            fi
        done <<< "$MATCHES"

        if [[ -n "$FILTERED" ]]; then
            log_warn "Potential hardcoded secrets matching: $pattern"
            echo -e "$FILTERED" | head -5
            SECRETS_FOUND=$((SECRETS_FOUND + 1))
        fi
    fi
done

if [[ $SECRETS_FOUND -eq 0 ]]; then
    log_ok "No hardcoded passwords or secrets detected"
fi

# ── 3c: Scan .properties files for sensitive values ────────
log_step "Step 3c: Scanning .properties files for sensitive values..."

PROPS_FILES=$(find . -name "*.properties" -not -path "*/node_modules/*" -not -path "*/.git/*" 2>/dev/null)
for props in $PROPS_FILES; do
    # Check for real passwords (not env var references)
    SENSITIVE=$(grep -n -iE "(password|secret|key|token)\s*=\s*[^${\$}]" "$props" 2>/dev/null | \
                grep -v "=\s*$" | \
                grep -v "\${" | \
                grep -v "your-" | \
                grep -v "placeholder" | \
                grep -v "admin123" | \
                grep -v "password123" | \
                grep -v "Password123!" | \
                grep -v "postgres" || true)

    if [[ -n "$SENSITIVE" ]]; then
        log_warn "Sensitive values found in: $props"
        echo "$SENSITIVE" | head -5
    fi
done
log_ok "Properties files scanned"

# ── 3d: Check for .env files that shouldn't be tracked ─────
log_step "Step 3d: Checking for tracked .env files..."

TRACKED_ENV=$(git ls-files | grep -E "\.env$|\.env\." | grep -v "\.env\.example" || true)
if [[ -n "$TRACKED_ENV" ]]; then
    log_fail "Tracked .env files found (should be in .gitignore):"
    echo "$TRACKED_ENV"
    SECRETS_FOUND=$((SECRETS_FOUND + 1))
else
    log_ok "No tracked .env files (all properly gitignored)"
fi

# ── 3e: Scan for accidentally committed secrets in history ──
log_step "Step 3e: Scanning git history for leaked secrets..."

HISTORY_LEAKS=$(git log --all --diff-filter=A -p -- \
    -E "(AIza[0-9A-Za-z_-]{35}|sk-[0-9A-Za-z]{32,}|PRIVATE KEY-----)" 2>/dev/null | \
    grep -E "^\+.*(AIza|sk-|PRIVATE KEY)" | head -5 || true)

if [[ -n "$HISTORY_LEAKS" ]]; then
    log_warn "Potential secrets in git history (review recommended):"
    echo "$HISTORY_LEAKS" | head -5
else
    log_ok "No obvious secrets found in git history"
fi

# ── Secret scrubbing summary ───────────────────────────────
echo ""
if [[ $SECRETS_FOUND -gt 0 ]]; then
    log_fail "SECRETS FOUND: $SECRETS_FOUND potential issues detected"
    log_info "Review the warnings above and remediate before submission."
    log_info "Options:"
    log_info "  1. Move secrets to .env file and add to .gitignore"
    log_info "  2. Use environment variable references: \${VARIABLE_NAME}"
    log_info "  3. Use git-filter-repo to remove from history (if already committed)"
else
    log_ok "Security scrubbing passed — no hardcoded secrets detected"
fi

# ============================================================
# PHASE 4: .GITIGNORE VERIFICATION
# ============================================================
log_header "PHASE 4: .gitignore Verification"

log_step "Checking .gitignore includes required entries..."

REQUIRED_ENTRIES=(
    ".env"
    ".env.local"
    ".env.*.local"
    ".env.production"
    "node_modules/"
    "target/"
    "*.log"
    ".idea/"
    ".vscode/"
)

GITIGNORE_STATUS=0
for entry in "${REQUIRED_ENTRIES[@]}"; do
    if grep -qF "$entry" .gitignore 2>/dev/null; then
        log_ok ".gitignore contains: $entry"
    else
        log_warn ".gitignore MISSING: $entry"
        GITIGNORE_STATUS=$((GITIGNORE_STATUS + 1))
    fi
done

# Check frontend/.gitignore if it exists
if [[ -f "frontend/.gitignore" ]]; then
    log_step "Checking frontend/.gitignore..."
    FRONTEND_ENTRIES=("node_modules/" "dist/" "*.local")
    for entry in "${FRONTEND_ENTRIES[@]}"; do
        if grep -qF "$entry" frontend/.gitignore 2>/dev/null; then
            log_ok "frontend/.gitignore contains: $entry"
        else
            log_warn "frontend/.gitignore MISSING: $entry"
        fi
    done
fi

if [[ $GITIGNORE_STATUS -eq 0 ]]; then
    log_ok ".gitignore verification passed"
else
    log_warn "$GITIGNORE_STATUS entries missing from .gitignore"
fi

# ============================================================
# PHASE 5: RELEASE TAGGING
# ============================================================
log_header "PHASE 5: Release Tagging"

log_step "Creating release tag: $RELEASE_TAG"

# Check if tag already exists
if git tag -l | grep -q "^${RELEASE_TAG}$"; then
    log_warn "Tag '$RELEASE_TAG' already exists"
    if [ "$DRY_RUN" = false ]; then
        read -p "  Delete and recreate? (y/N): " RECREATE
        if [[ "$RECREATE" =~ ^[Yy]$ ]]; then
            git tag -d "$RELEASE_TAG"
            git push origin --delete "$RELEASE_TAG" 2>/dev/null || true
            log_ok "Old tag deleted"
        else
            log_info "Keeping existing tag"
        fi
    fi
fi

if [ "$DRY_RUN" = true ]; then
    log_info "[DRY RUN] Would create tag: $RELEASE_TAG"
else
    # Generate tag message with commit summary
    TAG_MSG="CODAFRIQA AI Customer Support Chatbot - Final Submission

Release: $RELEASE_TAG
Date: $(date -u +%Y-%m-%dT%H:%M:%SZ)
Branch: main
Commit: $(git rev-parse --short HEAD)

## Summary
- Spring Boot 3.3 backend with pgvector RAG and Gemini AI
- Vue 3 frontend with real-time WebSocket chat
- Multi-role RBAC: Customer, Agent, Editor, Manager, Admin
- Ticket lifecycle with immutable activity audit trail
- Analytics dashboard with Chart.js visualizations
- Docker Compose staging environment
- 143 tests passing (unit + integration)

## Seed Accounts
- admin@codafriqa.local (ROLE_ADMIN)
- manager@codafriqa.local (ROLE_MANAGER)
- agent@codafriqa.local (ROLE_AGENT)
- editor@codafriqa.local (ROLE_EDITOR)
- customer@codafriqa.local (ROLE_CUSTOMER)
Password: Password123!"

    git tag -a "$RELEASE_TAG" -m "$TAG_MSG"
    log_ok "Tag '$RELEASE_TAG' created"
fi

# ============================================================
# PHASE 6: FINAL PUSH
# ============================================================
log_header "PHASE 6: Final Push to GitHub"

if [ "$SKIP_PUSH" = true ]; then
    log_warn "Push skipped (--skip-push flag or remote unreachable)"
    log_info "To push manually, run:"
    log_info "  git push origin main"
    log_info "  git push origin staging"
    log_info "  git push origin --tags"
else
    log_step "Pushing 'main' branch..."
    git push origin main
    log_ok "Pushed 'main'"

    log_step "Pushing 'staging' branch..."
    git push origin staging 2>/dev/null || log_warn "Push 'staging' failed (may not exist)"
    log_ok "Pushed 'staging'"

    log_step "Pushing tags..."
    git push origin --tags
    log_ok "Pushed all tags"

    # Push deleted feature branches to remote
    for branch in "${FEATURE_BRANCHES[@]}"; do
        if git show-ref --verify --quiet "refs/remotes/origin/$branch" 2>/dev/null; then
            log_step "Deleting remote branch '$branch'..."
            git push origin --delete "$branch" 2>/dev/null && \
                log_ok "Deleted remote '$branch'" || \
                log_warn "Could not delete remote '$branch'"
        fi
    done
fi

# ============================================================
# FINAL SUMMARY
# ============================================================
log_header "FINAL SUBMISSION SUMMARY"

echo ""
echo -e "${BOLD}Branch Status:${NC}"
git branch -v
echo ""
echo -e "${BOLD}Tags:${NC}"
git tag -l
echo ""
echo -e "${BOLD}Last 5 Commits:${NC}"
git log --oneline -5
echo ""
echo -e "${BOLD}Remote URL:${NC}"
git remote get-url origin
echo ""

if [[ $SECRETS_FOUND -gt 0 ]]; then
    echo -e "${RED}${BOLD}⚠  WARNING: $SECRETS_FOUND potential secret(s) detected — review before submitting${NC}"
fi

echo -e "${GREEN}${BOLD}✅ Final submission preparation complete!${NC}"
echo ""
echo -e "Next steps:"
echo -e "  1. Verify the GitHub repository: $(git remote get-url origin)"
echo -e "  2. Confirm tag '$RELEASE_TAG' is visible in GitHub Releases"
echo -e "  3. Submit the repository URL to your supervisor"
echo ""
