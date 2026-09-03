# Staging Deployment Guide

This guide provides instructions for deploying and verifying the AI Customer Support Chatbot staging environment.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose v2.0+
- 4GB+ RAM available for containers

## Quick Start

### 1. Start the Staging Environment

```bash
# Build and start all services
docker compose -f docker-compose.staging.yml up --build

# Or run in detached mode
docker compose -f docker-compose.staging.yml up --build -d
```

### 2. Verify Services are Running

```bash
# Check container status
docker compose -f docker-compose.staging.yml ps

# View logs
docker compose -f docker-compose.staging.yml logs -f

# Check specific service logs
docker compose -f docker-compose.staging.yml logs backend
docker compose -f docker-compose.staging.yml logs frontend
```

### 3. Access the Application

- **Frontend**: http://localhost
- **Backend API**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/swagger-ui.html

## Seed Accounts

The staging environment automatically creates the following test accounts:

| Role | Email | Password | Access Level |
|------|-------|----------|--------------|
| System Administrator | admin@codafriqa.local | password123 | Full admin access |
| Support Manager | manager@codafriqa.local | password123 | Manager dashboard |
| Support Agent | agent@codafriqa.local | password123 | Agent workspace |
| Knowledge Editor | editor@codafriqa.local | password123 | Knowledge base |
| Customer | customer@codafriqa.local | password123 | Customer chat |

**Note**: Passwords are BCrypt-hashed in the database.

## Health Checks

### Backend Health Endpoint

```bash
curl http://localhost:8080/api/health
# Expected: "AI Customer Support Chatbot is running"
```

### Frontend Health Check

```bash
curl http://localhost:80
# Expected: HTML response (Vue 3 SPA)
```

### Database Connection

```bash
# Connect to PostgreSQL container
docker exec -it chatbot_postgres_staging psql -U postgres -d ai_customer_support_chatbot

# Check users table
SELECT id, email, role FROM users;
```

## Verification Steps

### Step 1: Verify Backend API

```bash
# Test health endpoint
curl -s http://localhost:8080/api/health

# Test chat endpoint (should return AI response or mock)
curl -X POST http://localhost:8080/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello, I need help"}'
```

### Step 2: Verify Frontend

1. Open http://localhost in your browser
2. You should see the CODAFRIQA Smart Assistant chat widget
3. Click the chat bubble to open the widget
4. Send a test message

### Step 3: Test Role-Based Access

1. **Customer View**: Open http://localhost (default view)
2. **Agent View**: 
   - Login with agent@codafriqa.local / password123
   - Navigate to "Live Customer Workspace"
3. **Admin View**:
   - Login with admin@codafriqa.local / password123
   - Access "Knowledge Base Admin" and "System Indexer"

### Step 4: Verify Database Seeding

```bash
# Connect to database
docker exec -it chatbot_postgres_staging psql -U postgres -d ai_customer_support_chatbot

# Check all seeded users
SELECT id, email, role, created_at FROM users ORDER BY id;

# Expected output:
#  1 | admin@codafriqa.local    | ADMIN    | ...
#  2 | manager@codafriqa.local  | ADMIN    | ...
#  3 | agent@codafriqa.local    | AGENT    | ...
#  4 | editor@codafriqa.local   | AGENT    | ...
#  5 | customer@codafriqa.local | CUSTOMER | ...
```

### Step 5: Test WebSocket Connection

1. Open browser developer tools (F12)
2. Go to Network tab → WS filter
3. Send a message in the chat widget
4. You should see WebSocket connection to `/ws`

## Troubleshooting

### Container Won't Start

```bash
# Check logs for errors
docker compose -f docker-compose.staging.yml logs backend

# Common issues:
# - Database connection failed: Wait for PostgreSQL to be ready
# - Port already in use: Stop other services on ports 80, 8080, 5432
```

### Database Connection Issues

```bash
# Verify PostgreSQL is running
docker exec -it chatbot_postgres_staging pg_isready -U postgres

# Check database exists
docker exec -it chatbot_postgres_staging psql -U postgres -l
```

### Frontend Can't Reach Backend

```bash
# Check backend health
curl http://localhost:8080/api/health

# Check Nginx proxy configuration
docker exec -it chatbot_frontend_staging cat /etc/nginx/conf.d/default.conf

# View Nginx logs
docker compose -f docker-compose.staging.yml logs frontend
```

### Gemini API Issues

```bash
# Check if API key is set
echo $GEMINI_API_KEY

# Set API key before starting
export GEMINI_API_KEY=your-api-key-here
docker compose -f docker-compose.staging.yml up --build
```

## Stopping the Environment

```bash
# Stop all services
docker compose -f docker-compose.staging.yml down

# Stop and remove volumes (clean slate)
docker compose -f docker-compose.staging.yml down -v

# Remove built images
docker compose -f docker-compose.staging.yml down --rmi all
```

## Production Considerations

1. **Security**:
   - Change default passwords
   - Use environment variables for secrets
   - Enable HTTPS with SSL certificates

2. **Performance**:
   - Add Redis for session caching
   - Configure connection pooling
   - Enable CDN for static assets

3. **Monitoring**:
   - Add Prometheus metrics
   - Configure log aggregation
   - Set up alerting

4. **Backup**:
   - Regular database backups
   - Volume snapshots
   - Disaster recovery plan

## API Endpoints

### Health & Status
- `GET /api/health` - Backend health check
- `GET /api/analytics/summary` - System metrics

### Chat
- `POST /api/chat/message` - Send chat message
- `GET /api/chat/session/{id}` - Get session info

### Tickets
- `GET /api/tickets` - List tickets
- `POST /api/tickets/{id}/status` - Update ticket status

### Analytics
- `GET /api/analytics/dashboard` - Dashboard metrics
- `GET /api/export/tickets/csv` - Export tickets CSV

### Audit Logs
- `GET /api/audit` - List audit logs
- `GET /api/audit/stats` - Audit statistics

## Support

For issues or questions:
- Check the logs: `docker compose -f docker-compose.staging.yml logs`
- Review the README.md
- Check the API documentation at /swagger-ui.html
