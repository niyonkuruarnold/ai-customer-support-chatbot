# AI Customer Support Chatbot

An intelligent customer support chatbot system built with Spring Boot 3.3+, PostgreSQL, and Spring AI. Features RAG (Retrieval-Augmented Generation) knowledge base retrieval, automated ticketing, and human agent escalation.

**[📄 View Full Project Proposal](./doc/CODAFRIQA_AI_Chatbot_Proposal.pdf) | [📊 Week 2 Architecture](./doc/WEEK-2-ARCHITECTURE.md)**

## Project Status

### Week 2: Complete ✅
- Database schema design (4 tables: users, chat_sessions, chat_messages, support_tickets)
- JPA entity models with Hibernate annotations
- Spring Data JPA repositories
- PostgreSQL + pgvector integration
- Basic REST API controllers (User, Chat, Test endpoints)
- DTO layer for API request/response handling

### Week 3: In Progress 🚀
- RAG (Retrieval-Augmented Generation) pipeline with OpenAI embeddings
- Vector similarity search using pgvector
- Spring AI chat service integration
- Complete chat message API with AI responses

### Week 5: Human Handoff & Agent Workspace ✅
- Escalation detection (e.g. "Talk to a human agent") → session/ticket status ESCALATED
- AI handoff summary (2-3 bullets + sentiment) generated with Spring AI on escalation
- Chat sessions persisted to PostgreSQL (transcript available to agents)
- Agent REST endpoints: ticket queue, takeover, reply, internal notes, resolve
- Vue agent workspace (togglable Agent Mode / `?mode=agent`): ticket list, pinned AI summary, live status banner, internal notes, customer-side handoff banner + polling

## Architecture Overview

### Tech Stack
- **Backend:** Spring Boot 3.3+, Spring Data JPA, Spring Security
- **Database:** PostgreSQL 15+ with pgvector extension
- **AI/ML:** Spring AI, OpenAI GPT API
- **Build:** Maven 3.8+
- **Container:** Docker & Docker Compose
- **Java:** OpenJDK 17+

### Core Components

#### Database Layer
| Table | Purpose | Key Fields |
|-------|---------|-----------|
| `users` | User accounts & auth | id, email, role, created_at |
| `chat_sessions` | Chat context tracking | id, user_id, status, created_at |
| `chat_messages` | Message history & embeddings | id, session_id, sender, content, embedding |
| `support_tickets` | Escalated issues | id, user_id, subject, priority, status |

#### Service Layer
- **UserService:** User authentication and management
- **ChatService:** (Week 3) RAG-powered chat with vector search
- **TicketService:** (Future) Support ticket management

#### API Endpoints

**User Management**
```
POST   /api/users/register       - Register new user
POST   /api/users/login          - User authentication
GET    /api/users/{id}           - Get user profile
```

**Chat**
```
POST   /api/chat                 - Send message { message, sessionId? } -> { response, sessionId, status }
GET    /api/chat/session/{id}    - Session status + transcript (restores history, picks up agent replies)
GET    /api/chat/health          - Health check
```

**Agent Workspace (Week 5, requires Basic auth — default admin/admin123)**
```
GET    /api/agent/tickets                  - List escalated/open/in-progress tickets
GET    /api/agent/tickets/{id}             - Ticket detail (transcript + internal notes)
POST   /api/agent/tickets/{id}/takeover    - Assign ticket to current agent
POST   /api/agent/tickets/{id}/reply       - Send agent reply { message }
POST   /api/agent/tickets/{id}/notes       - Add internal note { content }
POST   /api/agent/tickets/{id}/resolve     - Resolve ticket
```

**Health Checks**
```
GET    /test/health              - Server health status
GET    /test/db-status           - Database connection check
```

## Quick Start

### Prerequisites
```bash
# Check Java version
java -version          # Requires Java 17+

# Check Maven
mvn --version          # Requires Maven 3.8+

# Check Docker
docker --version
docker-compose --version
```

### Setup & Run

1. **Clone & Navigate**
   ```bash
   cd ai-customer-support-chatbot
   ```

2. **Start PostgreSQL Container**
   ```bash
   docker-compose up -d
   ```

3. **Enable pgvector Extension**
   ```bash
   docker exec -it chatbot_postgres psql -U postgres -d ai_customer_support_chatbot \
     -c "CREATE EXTENSION IF NOT EXISTS vector;"
   ```

4. **Build & Run**
   ```bash
   mvn clean compile
   mvn spring-boot:run
   ```

5. **Verify Startup**
   ```bash
   curl http://localhost:8080/test/health
   curl http://localhost:8080/test/db-status
   ```

### Environment Configuration

Edit `src/main/resources/application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai_customer_support_chatbot
spring.datasource.username=postgres
spring.datasource.password=postgres

# OpenAI (for Week 3 RAG)
spring.ai.openai.api-key=${OPENAI_API_KEY}

# Server
server.port=8080
```

## Development Workflow

### Build
```bash
mvn clean compile
```

### Test
```bash
mvn test
```

### Run Locally
```bash
# Stop any existing Java process
Stop-Process -Name "java" -Force -ErrorAction SilentlyContinue

# Start app (creates tables automatically)
mvn spring-boot:run
```

### Database Access
```bash
# Connect to PostgreSQL
docker exec -it chatbot_postgres psql -U postgres -d ai_customer_support_chatbot

# List tables
\dt

# Query users
SELECT * FROM users;
SELECT * FROM chat_sessions;
```

### Docker Logs
```bash
docker logs chatbot_postgres -f
```

## Project Structure

```
ai-customer-support-chatbot/
├── src/main/java/com/codafriqa/ai_customer_support_chatbot/
│   ├── AiCustomerSupportChatbotApplication.java    # Spring Boot entry point
│   ├── controller/                                   # REST endpoints
│   │   ├── UserController.java
│   │   ├── ChatController.java                      # Week 3
│   │   └── TestController.java
│   ├── dto/                                          # API data transfer objects
│   │   ├── UserRequestDto.java
│   │   └── UserResponseDto.java
│   ├── model/                                        # JPA entities
│   │   ├── User.java
│   │   ├── UserRole.java
│   │   ├── ChatSession.java
│   │   ├── ChatMessage.java
│   │   └── SupportTicket.java
│   ├── repository/                                   # Spring Data JPA repos
│   │   ├── UserRepository.java
│   │   ├── ChatSessionRepository.java
│   │   ├── ChatMessageRepository.java
│   │   └── SupportTicketRepository.java
│   └── service/                                      # Business logic
│       ├── UserService.java
│       └── ChatService.java                         # Week 3
├── src/main/resources/
│   └── application.properties                        # Configuration
├── docker-compose.yml                                # PostgreSQL container config
├── pom.xml                                           # Maven dependencies
├── README.md                                         # This file
└── doc/
    ├── CODAFRIQA_AI_Chatbot_Proposal.pdf
    └── WEEK-2-ARCHITECTURE.md                        # Detailed schema & setup

```

## Key Dependencies

See `pom.xml` for full list. Key libraries:

- **spring-boot-starter-web:** REST API support
- **spring-boot-starter-data-jpa:** ORM and data access
- **spring-boot-starter-security:** Authentication & authorization
- **spring-ai-openai-spring-boot-starter:** OpenAI integration
- **postgresql:** PostgreSQL JDBC driver
- **jakarta.persistence-api:** JPA annotations
- **lombok:** Boilerplate reduction (optional)

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests (Week 3)
```bash
mvn verify
```

### Manual Testing with curl
```bash
# Register user
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"pass123","role":"CUSTOMER"}'

# Create chat session
curl -X POST http://localhost:8080/api/chat/session \
  -H "Content-Type: application/json" \
  -d '{"userId":1}'
```

## Troubleshooting

### PostgreSQL Connection Issues
```bash
# Check if container is running
docker ps | grep chatbot_postgres

# View container logs
docker logs chatbot_postgres

# Restart container
docker-compose restart chatbot_postgres
```

### Java Build Issues
```bash
# Clean Maven cache
rm -r ~/.m2/repository
mvn clean install -DskipTests
```

### Port Already in Use
```bash
# Find process using port 8080
netstat -ano | findstr :8080

# Kill process
taskkill /PID <PID> /F
```

## Contributing

1. Create feature branch: `git checkout -b feature/description`
2. Make changes and commit: `git commit -m "feat(scope): description"`
3. Push to branch: `git push origin feature/description`
4. Create Pull Request

## License

Proprietary - CODAFRIQA AI

## Human Handoff Flow

1. Customer sends a trigger like *"Talk to a human agent"* in the chat UI.
2. The backend marks the session ESCALATED, creates/updates the support
   ticket, and asks Spring AI to summarize the transcript into 2-3 bullet
   points with a sentiment label (priority is derived from sentiment).
3. Open **🎧 Agent Workspace** (header toggle, or `http://localhost:5173/?mode=agent`)
   and sign in with the Spring Security credentials (`admin` / `admin123`).
4. Pick the escalated ticket, review the pinned AI Handoff Summary, click
   **Take over**, then reply — replies are saved into the customer's
   transcript, which the customer chat picks up via polling and shows with
   a green "connected to a human agent" banner.

## Next Steps

**Week 3 Deliverables:**
- [ ] Vector embedding generation with OpenAI API
- [ ] RAG retrieval system with pgvector similarity search
- [ ] Complete ChatService with AI response generation
- [ ] Streaming message API
- [ ] Integration tests with Testcontainers
- [ ] API documentation (OpenAPI/Swagger)

---

**Last Updated:** Week 2 Complete (August 2026)  
**Maintainer:** CODAFRIQA Development Team