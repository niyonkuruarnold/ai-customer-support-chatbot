# Final Submission — Command Reference

## Quick Start

```bash
# Run the full automated submission script
chmod +x scripts/final-submission.sh
./scripts/final-submission.sh

# Or run with dry-run first (safe preview)
./scripts/final-submission.sh --dry-run

# Or skip the final push
./scripts/final-submission.sh --skip-push
```

---

## 1. Branch Consolidation

### Check Current State

```bash
# See all branches (local + remote)
git branch -a

# See current branch
git branch --show-current

# See latest commits on each branch
git log --oneline --graph --all --decorate | head -30
```

### Create Feature Branches (if they don't exist yet)

```bash
# Create from main
git checkout main
git checkout -b feature/chat-experience
git checkout main
git checkout -b feature/live-agent
git checkout main
git checkout -b feature/ticket-management
git checkout main
git checkout -b feature/analytics
git checkout main
git checkout -b feature/staging-deploy
```

### Merge Feature Branches into Staging

```bash
# Create/checkout staging
git checkout -b staging main  # or: git checkout staging

# Merge each feature branch
git merge --no-edit feature/chat-experience
git merge --no-edit feature/live-agent
git merge --no-edit feature/ticket-management
git merge --no-edit feature/analytics
git merge --no-edit feature/staging-deploy

# If merge conflicts occur:
git merge --abort          # cancel the failed merge
git merge feature/<name>   # retry and resolve manually
```

### Merge Staging into Main

```bash
git checkout main
git merge --no-edit staging
```

### Delete Merged Feature Branches (Local)

```bash
git branch -d feature/chat-experience
git branch -d feature/live-agent
git branch -d feature/ticket-management
git branch -d feature/analytics
git branch -d feature/staging-deploy
```

### Delete Remote Feature Branches

```bash
git push origin --delete feature/chat-experience
git push origin --delete feature/live-agent
git push origin --delete feature/ticket-management
git push origin --delete feature/analytics
git push origin --delete feature/staging-deploy
```

---

## 2. Secret & Security Scrubbing

### Run Automated Scrub

```bash
chmod +x scripts/secret-scrub.sh
./scripts/secret-scrub.sh
```

### Manual Grep Commands

```bash
# Scan for Google API keys
grep -rn -E "AIza[0-9A-Za-z_-]{35}" src/ frontend/ docker-compose*.yml

# Scan for OpenAI API keys
grep -rn -E "sk-[0-9A-Za-z]{32,}" src/ frontend/

# Scan for AWS credentials
grep -rn -E "(AKIA[0-9A-Z]{16}|aws_secret_access_key)" src/ frontend/

# Scan for GitHub PATs
grep -rn -E "ghp_[0-9A-Za-z]{36}" src/ frontend/

# Scan for private key headers
grep -rn "BEGIN.*PRIVATE KEY" src/ frontend/

# Scan for hardcoded passwords (excluding env var refs and test data)
grep -rn -iE "password\s*=\s*\"[^\"]{8,}\"" src/ frontend/ \
    | grep -v "\${" | grep -v "your-" | grep -v "placeholder" \
    | grep -v "admin123" | grep -v "Password123!" | grep -v "postgres"

# Scan for hardcoded API keys
grep -rn -iE "api[_-]?key\s*=\s*\"[^\"]{16,}\"" src/ frontend/ \
    | grep -v "\${" | grep -v "your-" | grep -v "placeholder"

# Scan for JWT secrets
grep -rn -iE "jwt[_-]?secret\s*=\s*\"[^\"]{8,}\"" src/ frontend/ \
    | grep -v "\${" | grep -v "your-"

# Scan all .properties files
find . -name "*.properties" -not -path "*/node_modules/*" \
    -exec grep -l -iE "(password|secret|key)\s*=" {} \;

# Check for tracked .env files
git ls-files | grep -E "\.env$|\.env\." | grep -v "\.env\.example"
```

### Verify .gitignore

```bash
# Check root .gitignore
cat .gitignore | grep -E "\.env|node_modules|target|\.log"

# Check frontend .gitignore
cat frontend/.gitignore 2>/dev/null | grep -E "node_modules|dist|\.local"

# Verify .env is NOT tracked
git ls-files --error-unmatch .env 2>&1 || echo ".env is not tracked (GOOD)"
```

---

## 3. Release Tagging

### Create the Release Tag

```bash
# Tag the current HEAD on main
git checkout main
git tag -a v1.0.0-final-submission -m "CODAFRIQA AI Customer Support Chatbot - Final Submission"
```

### Verify the Tag

```bash
# List all tags
git tag -l

# Show tag details
git show v1.0.0-final-submission

# Verify tag points to HEAD
git rev-parse v1.0.0-final-submission
git rev-parse HEAD
```

### If Tag Already Exists (delete and recreate)

```bash
git tag -d v1.0.0-final-submission
git push origin --delete v1.0.0-final-submission 2>/dev/null
git tag -a v1.0.0-final-submission -m "CODAFRIQA AI Customer Support Chatbot - Final Submission"
```

---

## 4. Final Push

### Push Everything

```bash
# Push main branch
git push origin main

# Push staging branch
git push origin staging

# Push all tags
git push origin --tags

# Push and set upstream (if needed)
git push -u origin main
```

### Verify Push

```bash
# Check remote branches
git ls-remote --heads origin

# Check remote tags
git ls-remote --tags origin

# Check GitHub URL
git remote get-url origin
```

---

## 5. Final Verification Checklist

```bash
# 1. Clean working tree
git status

# 2. All tests pass
mvn -B clean test

# 3. No secrets in codebase
./scripts/secret-scrub.sh

# 4. Correct branch
git branch --show-current  # should be: main

# 5. Tag exists
git tag -l | grep v1.0.0-final-submission

# 6. All commits pushed
git log --oneline origin/main..main  # should be empty (all pushed)

# 7. Remote is up to date
git fetch origin
git status  # should say "Your branch is up to date"
```

---

## Troubleshooting

### Merge Conflicts

```bash
# Abort failed merge
git merge --abort

# See what changed in the conflicting files
git diff --name-only --diff-filter=U

# After resolving, stage and commit
git add <resolved-files>
git commit -m "resolve: merge conflict in <file>"
```

### Accidentally Committed Secrets

```bash
# Remove file from tracking (keeps local copy)
git rm --cached .env

# Add to .gitignore
echo ".env" >> .gitignore

# Remove from entire git history (DANGEROUS — rewrites history)
git filter-repo --path .env --invert-paths

# Force push after history rewrite
git push origin main --force
```

### Tag Already Pushed

```bash
# Delete remote tag
git push origin --delete v1.0.0-final-submission

# Delete local tag
git tag -d v1.0.0-final-submission

# Recreate
git tag -a v1.0.0-final-submission -m "..."
git push origin --tags
```
