# Week 2: Database Architecture & Spring AI Setup

## Overview
Week 2 completes the database schema design and integrates Spring AI with PostgreSQL + pgvector for RAG (Retrieval-Augmented Generation) capabilities.

## Database Schema

### Tables

#### 1. **users** Table
Stores user account information and authentication data.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Unique user identifier |
| email | VARCHAR(255) | NOT NULL, UNIQUE | User email address |
| password_hash | VARCHAR(255) | NOT NULL | Hashed user password |
| role | VARCHAR(50) | NOT NULL, ENUM | User role: CUSTOMER, SUPPORT_AGENT, ADMIN |
| created_at | TIMESTAMP | NOT NULL | Account creation timestamp |

**Entity Class:** `User.java`

---

#### 2. **chat_sessions** Table
Tracks individual chat sessions between users and the AI chatbot.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Unique session identifier |
| user_id | BIGINT | NOT NULL | Foreign key to users table |
| status | VARCHAR(50) | NOT NULL, DEFAULT 'ACTIVE' | Session status: ACTIVE, CLOSED, ESCALATED |
| created_at | TIMESTAMP | NOT NULL | Session creation timestamp |
| updated_at | TIMESTAMP | NOT NULL | Last session update timestamp |

**Entity Class:** `ChatSession.java`

---

#### 3. **chat_messages** Table
Stores individual messages within chat sessions with vector embeddings for RAG.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Unique message identifier |
| session_id | BIGINT | NOT NULL | Foreign key to chat_sessions table |
| sender | VARCHAR(50) | NOT NULL | Message sender: USER, AI, AGENT |
| content | TEXT | NOT NULL | Message text content |
| timestamp | TIMESTAMP | NOT NULL | Message timestamp |
| embedding | vector(1536) | (Future) | OpenAI embedding for RAG retrieval |

**Entity Class:** `ChatMessage.java`

**Note:** Vector embeddings (pgvector) will be added in Week 3 for RAG pipeline implementation.

---

#### 4. **support_tickets** Table
Stores escalated support tickets for human agent handling.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Unique ticket identifier |
| user_id | BIGINT | NOT NULL | Foreign key to users table |
| session_id | BIGINT | NOT NULL | Foreign key to chat_sessions table |
| subject | VARCHAR(255) | NOT NULL | Ticket subject line |
| description | TEXT | NOT NULL | Detailed issue description |
| status | VARCHAR(50) | NOT NULL, DEFAULT 'OPEN' | Ticket status: OPEN, IN_PROGRESS, RESOLVED, CLOSED |
| priority | VARCHAR(50) | NOT NULL, DEFAULT 'MEDIUM' | Priority level: LOW, MEDIUM, HIGH, URGENT |
| created_at | TIMESTAMP | NOT NULL | Ticket creation timestamp |
| updated_at | TIMESTAMP | NOT NULL | Last ticket update timestamp |

**Entity Class:** `SupportTicket.java`

---

## Architecture Components

### Data Layer
- **JPA/Hibernate ORM:** Manages entity-to-table mapping
- **PostgreSQL Database:** Primary data store with JSON and vector support
- **pgvector Extension:** Enables vector similarity search for RAG retrieval
- **Spring Data JPA Repositories:** 
  - `UserRepository.java`
  - `ChatSessionRepository.java`
  - `ChatMessageRepository.java`
  - `SupportTicketRepository.java`

### Configuration
- **Database:** PostgreSQL running in Docker (localhost:5432)
- **Credentials:** postgres/postgres (configurable in `application.properties`)
- **Connection Pool:** HikariCP (auto-configured by Spring Boot)
- **DDL Strategy:** `spring.jpa.hibernate.ddl-auto=update` (auto-creates/updates schema)

### Spring AI Integration
- **OpenAI Integration:** Configured in `application.properties`
- **Vector Embeddings:** pgvector extension pre-installed for Week 3 RAG pipeline
- **Message Persistence:** All AI/user interactions stored for training and retrieval

## Entity Relationships

```
users (1) ──────── (many) chat_sessions
  │                              │
  │                              └──── (many) chat_messages
  │
  └────── (many) support_tickets ─────┘
```

## Development Setup

### Prerequisites
```bash
# Java 17+
java -version

# Maven 3.8+
mvn --version

# Docker & Docker Compose
docker --version
docker-compose --version
```

### Database Initialization

1. **Start PostgreSQL Container**
   ```bash
   docker-compose up -d
   ```

2. **Enable pgvector Extension**
   ```bash
   docker exec -it chatbot_postgres psql -U postgres -d ai_customer_support_chatbot -c "CREATE EXTENSION IF NOT EXISTS vector;"
   ```

3. **Verify Connection**
   ```bash
   docker exec -it chatbot_postgres psql -U postgres -d ai_customer_support_chatbot -c "\dt"
   ```

### Running the Application

```bash
# Build
mvn clean compile

# Start Server (creates tables automatically via Hibernate)
mvn spring-boot:run
```

The application will:
1. Auto-detect `application.properties` configuration
2. Create connection pool to PostgreSQL
3. Run Hibernate schema validation/update
4. Generate the 4 tables if they don't exist
5. Start listening on `http://localhost:8080`

### Verify Tables

Check in PostgreSQL:
```sql
-- Connect to database
psql -U postgres -d ai_customer_support_chatbot

-- List all tables
\dt

-- Expected output:
-- public | chat_messages        | table | postgres
-- public | chat_sessions        | table | postgres
-- public | support_tickets      | table | postgres
-- public | users                | table | postgres
```

## API Endpoints (Week 2)

### User Management
- `POST /api/users/register` - Register new user
- `POST /api/users/login` - User login
- `GET /api/users/{id}` - Get user profile

### Chat Endpoints (Stub)
- `POST /api/chat/session` - Create chat session
- `GET /api/chat/session/{id}` - Get session details
- `POST /api/chat/message` - Send message (AI response in Week 3)

### Test Endpoints
- `GET /test/health` - Health check
- `GET /test/db-status` - Database connection status

## Week 2 Completion Checklist

- [x] Design 4-table database schema
- [x] Create JPA Entity classes with annotations
- [x] Create Spring Data JPA Repository interfaces
- [x] Configure PostgreSQL connection in `application.properties`
- [x] Enable pgvector extension for RAG support
- [x] Verify Hibernate auto-generates tables on startup
- [x] Create UserService for business logic
- [x] Create basic controllers (UserController, TestController)
- [x] Create DTOs for API requests/responses (UserRequestDto, UserResponseDto)
- [x] Document architecture and schema

## Next Steps: Week 3

Week 3 will focus on implementing the **RAG (Retrieval-Augmented Generation) Pipeline**:

1. **Vector Embeddings:**
   - Integrate OpenAI embedding API
   - Store chat message embeddings in pgvector

2. **Similarity Search:**
   - Query vector store for relevant past conversations
   - Implement semantic search using pgvector

3. **AI Chat Service:**
   - Create ChatService with Spring AI integration
   - Implement RAG-enhanced chat responses
   - Integrate OpenAI GPT for response generation

4. **Chat API Implementation:**
   - Complete `/api/chat/message` endpoint with RAG retrieval
   - Add message persistence and embedding creation
   - Implement streaming responses

5. **Testing:**
   - Integration tests with PostgreSQL testcontainers
   - E2E tests for chat flow

## References

- [Spring Boot JPA Documentation](https://spring.io/projects/spring-data-jpa)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [PostgreSQL JSON Support](https://www.postgresql.org/docs/current/datatype-json.html)
- [Hibernate ORM Guide](https://hibernate.org/orm/)

---

**Last Updated:** Week 2 Development  
**Status:** Complete
