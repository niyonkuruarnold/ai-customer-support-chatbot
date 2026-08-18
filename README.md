# AI Customer Support Chatbot

[![CI Pipeline](https://github.com/niyonkuruarnold/ai-customer-support-chatbot/actions/workflows/ci.yml/badge.svg)](https://github.com/niyonkuruarnold/ai-customer-support-chatbot/actions)
[![JaCoCo Coverage](https://img.shields.io/badge/JaCoCo-coverage%20report-blue)](https://github.com/niyonkuruarnold/ai-customer-support-chatbot/actions/workflows/ci.yml)

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

### Week 3: RAG Pipeline ✅
- **pgvector configuration**: `spring.ai.vectorstore.pgvector.initialize-schema=true` (schema auto-created on boot) and 1536-dimension embeddings (OpenAI `text-embedding-3-small`) — verified in the boot log (`Initializing PGVectorStore schema for table: vector_store`)
- **`RagService`**: dedicated RAG service injecting the pgvector `VectorStore` + `ChatClient.Builder` — ingestion (chunk + store via the knowledge base pipeline) and `retrieveContext` (top-K similarity search, never throws, falls back to a plain prompt)
- **Ingestion endpoint**: `POST /api/v1/rag/ingest` (also `/api/rag/ingest`) — `{ "title": "...", "content": "..." }`; without `OPENAI_API_KEY` it fails fast with a structured 400 (embedding generation) and rolls back cleanly
- **Context-aware chat**: each customer message runs a vector similarity search (top-4 chunks), the retrieved context is injected into the OpenAI system prompt, and the response now carries **`ragUsed` + `contextReferences`** (document id / title / source type) for citation metadata
- Chat endpoint reachable at `POST /api/v1/chat/message` (spec path) and `POST /api/chat` (frontend path), plus `/api/v1/chat`

### Week 4: Knowledge Base & RAG ✅
- Document ingestion pipeline: text, Markdown and PDF support documents parsed with Spring AI document readers
- Token-based chunking with Spring AI `TokenTextSplitter`, embedded and stored in PostgreSQL via the pgvector `VectorStore`
- Admin knowledge base endpoints: upload, list documents/chunks, delete
- `ChatService` now retrieves the top-K relevant chunks per message and enriches the prompt (RAG)
- Vue **Knowledge Base** tab in the Agent Workspace: drag-and-drop upload, paste raw FAQ text, list/index/delete documents

### Week 6: Ticket Lifecycle & Email Notifications ✅
- **Dedicated Knowledge Base Admin page** in the Vue frontend (`?mode=knowledge`): drag-and-drop `.md`/`.txt` upload, indexed-documents table with delete, embedding spinners, and success/error toasts — wired to the spec-exact `/api/v1/admin/knowledge-base/*` endpoints

### Week 6: Ticket Lifecycle & Email Notifications ✅
- `SupportTicketService` state machine: OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED (ESCALATED accepted for handoff), illegal transitions rejected with a 400
- Automated email notifications via `JavaMailSender` (Mailtrap-style SMTP) on ticket **opened / updated / resolved** — best-effort, failures logged, never break ticket operations
- Admin dashboard API: `GET /api/v1/tickets` with `status` / `priority` / `assignedAgentId` filters + pagination/sorting, and `POST /{id}/close`

### Week 5: Human Handoff & Agent Workspace ✅
- Escalation detection (e.g. "Talk to a human agent") → session/ticket status ESCALATED
- AI handoff summary (2-3 bullets + sentiment) generated with Spring AI on escalation; priority derived from sentiment
- Chat sessions persisted to PostgreSQL (transcript available to agents)
- Agent REST endpoints: ticket queue, takeover, reply, internal notes, resolve
- Tickets expose **customer contact details** (email of the backing account — anonymous sessions are resolved to the seeded `customer@codafriqa.local` account on boot)
- Vue agent workspace (togglable Agent Mode / `?mode=agent`): **live ticket queue** (polled every 5s) with status/priority/sentiment badges + AI summary snippet + contact info, pinned AI summary, real-time conversation view (new customer messages appear without manual refresh), live status banner, internal notes, customer-side handoff banner + polling
- **AI pause on handoff:** once a session is ESCALATED, the backend stops generating AI answers — customer messages receive a "sent to the agent" acknowledgement and the customer UI switches to an amber **Agent Active** banner ("AI assistant is paused")

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
| `knowledge_documents` | KB documents (RAG) | id, title, source_type, file_name |
| `knowledge_chunks` | KB chunks mirroring pgvector rows | id, document_id, chunk_index, content |
| `vector_store` | pgvector embeddings (Spring AI) | id, content, embedding, metadata |

#### Service Layer
- **UserService:** User authentication and management
- **ChatService:** RAG-powered chat — retrieves top-K knowledge base chunks per message and enriches the prompt
- **KnowledgeBaseService:** (Week 4) Document ingestion pipeline — Spring AI readers (text/Markdown/PDF) → `TokenTextSplitter` chunks → pgvector `VectorStore`; also admin queries/delete + retrieval
- **EscalationService / AgentService:** (Week 5) Human handoff, AI summary, agent workspace operations
- **SupportTicketService:** (Week 6) Ticket lifecycle state machine (OPEN/IN_PROGRESS/RESOLVED/CLOSED), filtering + pagination for the admin dashboard
- **EmailNotificationService:** (Week 6) Automated ticket emails (opened/updated/resolved) via JavaMailSender + Mailtrap/SMTP

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

**Ticket Lifecycle Dashboard (Week 6, requires Basic auth — default admin/admin123; both `/api/tickets` and `/api/v1/tickets` work)**
```
GET    /api/v1/tickets                 - List tickets (filters: status, priority, assignedAgentId; page=0-based, size, sort)
POST   /api/v1/tickets/{id}/close      - Close a resolved ticket (RESOLVED -> CLOSED)

# Examples
GET    /api/v1/tickets?status=ESCALATED&priority=HIGH&page=0&size=10&sort=updatedAt,desc
GET    /api/v1/tickets?assignedAgentId=1
```

Consumed by the Vue **Ticket Dashboard** (`frontend/src/components/admin/TicketDashboard.vue`, reachable via the 🎫 **Tickets** header button or `http://localhost:5173/?mode=tickets`): status/priority/agent filters, a paginated ticket table with status + priority badges and customer contact details, and a Close action on resolved tickets. Note: `assignedAgentId` resolves through the users table (the stored field is the agent *name*), so unknown ids match nothing.

**Knowledge Base (Week 4, requires Basic auth — default admin/admin123; both `/api/admin` and `/api/v1/admin` work)**
```
POST   /api/v1/admin/knowledge-base/upload   - Upload + index a support file (multipart: file, optional title)
GET    /api/v1/admin/knowledge-base          - List indexed documents (with chunk counts)
DELETE /api/v1/admin/knowledge-base/{id}     - Remove document + its chunks from the vector store

# Equivalent paths on the documents namespace (used by the Vue KB tab):
POST   /api/admin/documents/upload           - Upload + index a support file (multipart: file, optional title)
POST   /api/admin/documents/text             - Index pasted FAQ text { title, content }
GET    /api/admin/documents                  - List indexed documents (with chunk counts)
GET    /api/admin/documents/chunks           - List every indexed chunk
DELETE /api/admin/documents/{id}             - Remove document + its chunks from the vector store
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

# OpenAI (for RAG + AI responses)
spring.ai.openai.api-key=${OPENAI_API_KEY}

# Server
server.port=8080
```

> **Set the OpenAI key before starting the app.** The property is the
> placeholder `${OPENAI_API_KEY:}` (empty default), read from the process
> environment **or a local `.env` file** (via the `spring-dotenv` dependency —
> real environment variables always win over `.env`). Copy the template and
> fill it in:
>
> ```bash
> cp .env.example .env      # then edit .env with your real key
> mvn spring-boot:run
> ```
>
> Or export it in your terminal instead:
>
> ```bash
> # Linux/macOS/Git Bash
> export OPENAI_API_KEY=sk-...
> mvn spring-boot:run
>
> # Windows (PowerShell)
> $env:OPENAI_API_KEY = "sk-..."; mvn spring-boot:run
> ```
>
> `.env` is gitignored (only `.env.example` is committed), so secrets never
> reach git. At startup the app logs `OpenAI API key is configured (N
> characters)` on success or a clear `OpenAI API key is NOT configured`
> warning otherwise.
>
> **Two config flavors** (pick one in `application.properties`):
> - `${OPENAI_API_KEY}` (default) — the placeholder stays unresolved without
>   a key, the app boots and degrades gracefully: chat answers with the
>   fallback message and knowledge base uploads fail with a 400 "Could not
>   generate embeddings" (rolled back cleanly). The exact failure is logged
>   with its full stack trace — look for `Caused by: HttpRetryException ...
>   server authentication` in the app log. A missing or placeholder key is
>   never an unhandled exception.
> - `${OPENAI_API_KEY:}` — resolves to empty when the variable is missing,
>   and Spring AI M6 then **fails fast at boot** with "OpenAI API key must be
>   set". Use this when a key (`.env` or exported) is always expected.
>
> **Email (ticket notifications):** add `MAIL_HOST`, `MAIL_PORT`,
> `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` to `.env` (see
> `.env.example`) to receive real ticket emails via Mailtrap/SMTP. Without
> them the app boots and send attempts fail gracefully (warnings in the log).

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

## Knowledge Base (RAG) Flow

1. Sign in to the **🎧 Agent Workspace** (`admin` / `admin123`) and open the
   **📚 Knowledge Base** tab.
2. Drag-and-drop a `.txt`/`.md`/`.pdf` support document or paste raw FAQ text
   with a title.
3. The backend parses the source with Spring AI document readers, splits it
   into ~500-token chunks with `TokenTextSplitter`, embeds each chunk, and
   stores it in the pgvector `vector_store` table (chunk metadata is tracked
   in `knowledge_documents` / `knowledge_chunks`).
4. Each customer message now retrieves the top-K relevant chunks and the
   prompt is enriched with that context, so the AI answers from the
   knowledge base (RAG).

Programmatic ingestion is also available:

```bash
curl -X POST http://localhost:8080/api/v1/rag/ingest \
  -H "Content-Type: application/json" \
  -d '{"title": "Returns Policy", "content": "Customers may return items within 30 days..."}'
```

Chat responses include RAG metadata — `ragUsed` (whether the answer was
grounded in retrieved context) and `contextReferences` (the source document
id / title / type per retrieved chunk), which the frontend can render as
citations.

> **Note:** embedding generation requires `OPENAI_API_KEY`. Without a key,
> uploads fail fast with a clear 400 (and roll back cleanly — no partial
> documents), and the chat gracefully answers without KB context (retrieval
> logs the exact failure and falls back to a plain prompt).

## Human Handoff Flow

1. Customer sends a trigger like *"Talk to a human agent"* in the chat UI.
2. The backend marks the session ESCALATED, creates/updates the support
   ticket, and asks Spring AI to summarize the transcript into 2-3 bullet
   points with a sentiment label (priority is derived from sentiment).
3. Open **🎧 Agent Workspace** (header toggle, or `http://localhost:5173/?mode=agent`)
   and sign in with the Spring Security credentials (`admin` / `admin123`).
   To manage the RAG knowledge base directly, open **📚 Knowledge Base**
   (or `http://localhost:5173/?mode=knowledge`) — same credentials.
4. Pick the escalated ticket — the queue shows the customer's contact
   (email), sentiment and priority badges, the AI handoff summary snippet,
   and the last message. Review the pinned AI Handoff Summary, click
   **Take over**, then reply — replies are saved into the customer's
   transcript, which the customer chat picks up via polling.
5. The workspace **polls every 5s** while signed in, so newly escalated
   tickets appear in the queue and new customer messages show up in the
   open conversation automatically (no manual refresh needed).
6. Once escalated, **automated AI responses are paused**: the customer's UI
   switches to an amber "🎧 Agent Active — the AI assistant is paused"
   banner, the input placeholder becomes "Message the agent…", and any
   message the customer sends is acknowledged ("sent to the agent") without
   invoking the AI — the agent owns the conversation from then on.

## Ticket Lifecycle & Email Notifications

- **State machine:** `SupportTicketService` owns all status transitions —
  OPEN → IN_PROGRESS (takeover), OPEN/ESCALATED/IN_PROGRESS → RESOLVED,
  RESOLVED → CLOSED. Any other transition returns a structured **400**
  ("Invalid ticket status transition"). Takeover and resolve in the agent
  workspace now route through this service.
- **Emails:** on ticket **opened** (creation, incl. escalation), **updated**
  (agent takeover), and **resolved**, `EmailNotificationService` sends a
  styled HTML + plain-text email to the customer's address via
  `JavaMailSender`. Configured for Mailtrap-style SMTP — set `MAIL_HOST` /
  `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` (see `.env.example`) to
  receive real emails. Without credentials the app still boots and send
  failures are logged as warnings (`Could not send OPENED email ...`).

## Next Steps

- [ ] Streaming message API
- [ ] Integration tests with Testcontainers
- [ ] API documentation (OpenAPI/Swagger)
- [ ] Multi-document ingestion from external sources (URLs, S3, etc.)
- [ ] Embedding batching/retry tuning for large PDFs

---

**Last Updated:** Week 6 Complete (August 2026)  
**Maintainer:** CODAFRIQA Development Team