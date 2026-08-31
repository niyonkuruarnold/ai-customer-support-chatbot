# AI Customer Support Chatbot - Work Summary Documentation

**Project Name:** AI Customer Support Chatbot  
**Current Date:** August 31, 2026  
**Status:** In Development  

---

## Project Overview

An intelligent customer support chatbot system built with **Spring Boot 3.3+**, **PostgreSQL**, and **Spring AI**. Features RAG (Retrieval-Augmented Generation) knowledge base retrieval, automated ticketing, and human agent escalation.

**Repository:** https://github.com/niyonkuruarnold/ai-customer-support-chatbot

---

## ✅ Work Completed

### 1. **Backend Architecture & Setup** (Spring Boot 3.3+)
- ✅ Spring Boot project structure established
- ✅ Maven build configuration (pom.xml)
- ✅ Spring Security integrated
- ✅ PostgreSQL database connectivity configured
- ✅ Spring JPA/Hibernate ORM configured
- ✅ Application properties configured for:
  - PostgreSQL connection (localhost:5432)
  - Spring AI with Google Gemini API
  - pgvector for vector embeddings
  - Email notifications via Mailtrap SMTP
  - Multipart file uploads (10MB limit)

### 2. **Database Schema Design** (Week 2 Architecture)
Implemented four main tables with proper relationships:

| Table | Purpose | Key Features |
|-------|---------|--------------|
| **users** | User accounts & authentication | Email, password hash, role-based access (CUSTOMER, SUPPORT_AGENT, ADMIN) |
| **chat_sessions** | Individual chat sessions | Status tracking (ACTIVE, CLOSED, ESCALATED) |
| **chat_messages** | Message storage | Supports USER, AI, AGENT senders; ready for vector embeddings |
| **support_tickets** | Escalated support tickets | Priority levels (LOW, MEDIUM, HIGH, URGENT); status tracking |

**Data Layer Components:**
- Spring Data JPA Repositories for all entities
- Hibernate DDL auto-management
- pgvector extension for similarity search
- HikariCP connection pooling

**Entity Relationships:**
```
users (1:N) chat_sessions (1:N) chat_messages
users (1:N) support_tickets
```

### 3. **CORS & Exception Handling** (Commit 50b0f43)

#### CORS Configuration (SecurityConfig.java)
- ✅ Configured for Vue.js frontend development
- **Allowed Origins:**
  - `http://localhost:5173` (Vue dev server)
  - `http://localhost:3000` (Alternative port)
  - `http://localhost:8081` (Alternative port)
  - `https://yourdomain.com` (Production domain)
- **Supported Methods:** GET, POST, PUT, DELETE, OPTIONS
- **Features:** Credentials enabled, 10-minute preflight cache

#### Global Exception Handler (GlobalExceptionHandler.java)
- ✅ Centralized error handling via @ControllerAdvice
- **Exception Types Handled:**
  - Validation errors → 400 Bad Request
  - OpenAI API errors → Appropriate HTTP status (429, 401, 503)
  - Database errors → 503 Service Unavailable
  - Resource not found → 404 Not Found
  - Unauthorized access → 401 Unauthorized
  - Generic exceptions → 500 Internal Server Error

- **Response Format:**
  ```json
  {
    "timestamp": "2026-08-14T13:00:00",
    "status": 400,
    "error": "Validation Failed",
    "message": "Invalid request parameters",
    "fieldErrors": {"message": "Message cannot be empty"},
    "path": "/api/chat"
  }
  ```

#### Custom Exceptions
- ✅ `ResourceNotFoundException`
- ✅ `DatabaseException`
- ✅ `UnauthorizedException`
- ✅ `OpenAIApiException` (with HttpStatus mapping)

#### DTOs with Validation
- ✅ **ChatRequestDto**
  - `@NotBlank` - Message cannot be empty
  - `@Size(1-2000)` - Length validation
  
- ✅ **ChatResponseDto**
  - Structured response object format

#### Controller Enhancements
- ✅ ChatController updated with `@Valid` request body validation
- ✅ `@CrossOrigin` annotations applied
- ✅ `/api/chat/health` endpoint for monitoring
- ✅ Returns structured ChatResponseDto instead of raw Map objects

### 4. **Frontend Setup** (Vue 3 + Vite)
- ✅ Vue 3 application scaffolding
- ✅ Vite build configuration
- ✅ Component structure organized:
  - **Core:** ChatWindow, ChatMessage, TypingIndicator, SyncStatusBadge
  - **Admin:** KnowledgeBaseAdmin, KnowledgeBaseManager, MaintenanceHistory, OwnerDashboard
  - **Agent:** AgentWorkspace, AgentTicketList, AgentConversation
  - **Reservations:** DateRangePicker, MyReservations, ReviewForm, ReviewList, StarRating

- ✅ API integration layer:
  - `api/admin.js` - Admin operations
  - `api/agent.js` - Agent operations
  - `api/chat.js` - Chat operations
  - `api/maintenance.js` - Maintenance operations
  - `api/reservation.js` - Reservation operations
  - `api/review.js` - Review operations

- ✅ State Management (Pinia stores):
  - `stores/agent.js` - Agent state
  - `stores/chat.js` - Chat state
  - `stores/knowledgeBase.js` - Knowledge base state
  - `stores/maintenance.js` - Maintenance state
  - `stores/reservation.js` - Reservation state
  - `stores/review.js` - Review state

- ✅ Utilities:
  - `utils/markdown.js` - Markdown rendering support
  - `composables/useToasts.js` - Toast notifications

- ✅ Testing:
  - Component tests in place for: App, ChatMessage, ChatWindow, TicketDashboard, AgentTicketList, KnowledgeBaseAdmin, KnowledgeBaseManager
  - Spec files for store tests

### 5. **Spring AI Integration**
- ✅ Google Gemini API integration (free tier)
- ✅ Model: `gemini-3.6-flash` for chat responses
- ✅ Model: `text-embedding-004` for embeddings
- ✅ pgvector configuration:
  - HNSW indexing support
  - 768-dimension embeddings (Google text-embedding-004 default)
  - Schema auto-initialization

### 6. **Docker & Deployment Configuration**
- ✅ docker-compose.yml configured for PostgreSQL
- ✅ Database initialization scripts
- ✅ Maven wrapper (mvnw/mvnw.cmd) for CI/CD
- ✅ Build output in `/target` directory

### 7. **Email Notifications System**
- ✅ Mailtrap SMTP configuration
- ✅ Configurable email settings via environment variables:
  - `MAIL_HOST`, `MAIL_PORT`
  - `MAIL_USERNAME`, `MAIL_PASSWORD`
  - `MAIL_FROM`
- ✅ Integrated with support ticket lifecycle

---

## 📊 Architecture Overview

### System Components

```
┌─────────────────────────────────────────────────────────────────┐
│                    Vue 3 Frontend (Port 5173)                    │
│  - Chat Widget     - Agent Workspace    - Admin Dashboard       │
│  - Ticket System   - Knowledge Base     - Reservations         │
└────────────────────────┬──────────────────────────────────────┘
                         │
                    REST API (Port 8080)
                         │
┌─────────────────────────┴──────────────────────────────────────┐
│               Spring Boot Backend (Port 8080)                   │
│                                                                  │
│  Controllers:                Services:                          │
│  - ChatController          - ChatService                        │
│  - AgentController         - RagService                         │
│  - TicketController        - EscalationService                  │
│  - KnowledgeBaseController - AgentService                       │
│  - RagController           - KnowledgeBaseService               │
│                            - SupportTicketService               │
│                            - EmailNotificationService           │
│                                                                  │
│  Security:  CORS + Exception Handling + Input Validation        │
└────────────────────────┬──────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
     ┌──▼──┐      ┌──────▼──────┐    ┌───▼────┐
     │ JPA │      │ pgvector    │    │ Gmail/ │
     │ORM  │      │ Vector Store│    │Mailtrap│
     └──┬──┘      └──────┬──────┘    └───┬────┘
        │                │                │
   ┌────▼───────────────▼──────────────┬──┘
   │  PostgreSQL Database               │
   │  - chat_sessions                   │
   │  - chat_messages (with embeddings) │
   │  - support_tickets                 │
   │  - users                           │
   └───────────────────────────────────┘
        │
   ┌────▼──────────────────┐
   │  Google Gemini API    │
   │  - Chat responses     │
   │  - Embeddings         │
   └───────────────────────┘
```

---

## 🛠️ Technology Stack

| Layer | Technologies |
|-------|--------------|
| **Frontend** | Vue 3, Vite, Axios/Fetch, Pinia (state management) |
| **Backend** | Spring Boot 3.3+, Spring Security, Spring Data JPA, Spring AI |
| **Database** | PostgreSQL, pgvector extension, Hibernate ORM |
| **AI/ML** | Google Gemini API (Chat + Embeddings), OpenAI API (fallback) |
| **Communication** | REST APIs, WebSocket-ready for real-time updates |
| **Email** | Mailtrap/SMTP for notifications |
| **Build** | Maven 3.8+, Docker Compose |
| **Testing** | Jest (Vue components), JUnit (Spring tests) |
| **CI/CD** | GitHub Actions |

---

## 📝 Coding Standards & Best Practices Implemented

### Backend
- ✅ Global exception handling with structured error responses
- ✅ Input validation at DTO level (@NotBlank, @Size, etc.)
- ✅ CORS configuration for cross-origin requests
- ✅ JPA repositories for data access
- ✅ Spring Security for authentication/authorization
- ✅ Service layer abstraction
- ✅ Environment variable configuration management

### Frontend
- ✅ Component-based architecture
- ✅ Composition API (Vue 3) with composables
- ✅ Pinia store pattern for state management
- ✅ Separated API layer (independent service files)
- ✅ Unit tests for components and stores
- ✅ Toast notification system for user feedback

---

## 🚀 How to Run the Application

### Prerequisites
```bash
Java 17+ (OpenJDK or Oracle)
Maven 3.8+
Docker & Docker Compose
Node.js 16+ (for frontend development)
```

### Setup Steps

#### 1. Backend Setup
```bash
# Start PostgreSQL
docker-compose up -d

# Build Spring Boot application
mvn clean package

# Run application (or use: mvn spring-boot:run)
java -jar target/ai-customer-support-chatbot-*.jar
```

#### 2. Frontend Setup
```bash
cd frontend

# Install dependencies
npm install

# Start Vite dev server
npm run dev

# Frontend will be available at http://localhost:5173
```

#### 3. Verify Connection
```bash
# Test backend health
curl http://localhost:8080/api/chat/health
# Expected response: "AI Customer Support Chatbot is running"
```

---

## 📋 Configuration Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven dependencies & build configuration |
| `application.properties` | Spring Boot configuration (DB, AI, email, etc.) |
| `docker-compose.yml` | PostgreSQL container setup |
| `vite.config.js` | Vue build configuration |
| `package.json` | Frontend dependencies |
| `schema.sql` | Database initialization script |

---

## ✨ Key Features Implemented

1. **Chat System**
   - User-to-AI conversation
   - Message persistence
   - Status tracking

2. **Escalation System**
   - User can request human agent
   - Automatic ticket creation
   - AI summary generation on escalation

3. **Knowledge Base Management**
   - Document upload & processing
   - Vector embeddings for similarity search
   - Admin dashboard for management

4. **Support Ticketing**
   - Automatic ticket creation on escalation
   - Priority levels & status tracking
   - Agent assignment & response workflow
   - Email notifications

5. **Agent System**
   - Agent workspace for handling tickets
   - Real-time message updates (polling)
   - Agent notes & internal communication

6. **Security**
   - Role-based access control (CUSTOMER, SUPPORT_AGENT, ADMIN)
   - Input validation
   - CORS protection
   - Exception handling

---

## 📞 Integration Points

### Frontend → Backend
- REST API endpoints for all operations
- Fetch/Axios with CORS credentials
- Structured error handling
- Polling mechanism for real-time updates (3-5s intervals)

### Backend → External Services
- **Google Gemini API:** Chat responses & embeddings
- **PostgreSQL:** Data persistence
- **Mailtrap/SMTP:** Email notifications
- **pgvector:** Vector similarity search

---

## 📂 Project Structure Summary

```
ai-customer-support-chatbot/
├── pom.xml                          # Maven build file
├── docker-compose.yml               # PostgreSQL setup
├── application.properties            # Spring configuration
├── CORS_EXCEPTION_HANDLING_GUIDE.md  # Implementation docs
├── README.md                         # Main project documentation
├── doc/
│   └── WEEK-2-ARCHITECTURE.md       # Database & Spring AI setup docs
├── scripts/
│   └── schema.sql                   # Database initialization
├── src/main/java/com/codafriqa/
│   └── ai_customer_support_chatbot/
│       ├── config/                  # Spring configuration beans
│       ├── controller/              # REST controllers
│       ├── dto/                     # Request/Response DTOs
│       ├── exception/               # Custom exceptions
│       ├── model/                   # JPA entities
│       ├── repository/              # Spring Data JPA repositories
│       └── service/                 # Business logic layer
├── src/main/resources/
│   └── application.properties
├── frontend/
│   ├── src/
│   │   ├── components/              # Vue components
│   │   ├── stores/                  # Pinia state management
│   │   ├── api/                     # API integration layer
│   │   ├── composables/             # Vue composables
│   │   ├── utils/                   # Utility functions
│   │   └── test/                    # Test setup
│   ├── package.json
│   └── vite.config.js
└── target/                          # Build output
```

---

## 🧪 Testing

- ✅ Component-level tests for Vue components
- ✅ Store tests for Pinia state management
- ✅ Controller/Service tests for backend (JUnit)
- ✅ JaCoCo coverage reporting enabled
- ✅ GitHub Actions CI pipeline configured

---

## ⚠️ Important Notes

1. **Environment Variables Required:**
   - `GEMINI_API_KEY` - Google Gemini API key
   - `DB_USERNAME` - PostgreSQL username (default: postgres)
   - `DB_PASSWORD` - PostgreSQL password (default: postgres)
   - `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` - Email config

2. **Development URLs:**
   - Frontend: `http://localhost:5173`
   - Backend: `http://localhost:8080`
   - PostgreSQL: `localhost:5432`

3. **CORS:** Currently configured for localhost. Update production domain in SecurityConfig before deployment.

4. **Email:** Email sending is optional—failures are logged as warnings and don't break application flow.

---

## 🎯 Next Steps (Recommended)

1. Integration testing with E2E tests (Playwright/Cypress)
2. Production deployment configuration (Kubernetes/Docker)
3. Performance optimization for large knowledge bases
4. Advanced RAG capabilities (re-ranking, filters)
5. Real-time WebSocket implementation for agent chats
6. Analytics & monitoring dashboard
7. User feedback collection system
8. Mobile-responsive design improvements

---

## 📞 Questions or Issues?

Refer to:
- `README.md` - Project overview
- `doc/WEEK-2-ARCHITECTURE.md` - Database design
- `CORS_EXCEPTION_HANDLING_GUIDE.md` - API integration guide
- GitHub Issues for bug tracking

---

**Last Updated:** August 31, 2026  
**Status:** Development Phase Complete, Ready for Integration Testing
