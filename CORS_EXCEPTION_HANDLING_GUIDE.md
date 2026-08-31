# CORS & Exception Handling Implementation Guide

##  What Was Implemented

### 1. CORS Configuration (SecurityConfig.java)
Allows your Vue.js frontend to communicate with the backend without CORS errors.

**Allowed Origins:**
- `http://localhost:5173` (Vue dev server)
- `http://localhost:3000` (Alternative frontend port)
- `http://localhost:8081` (Alternative frontend port)
- `https://yourdomain.com` (Production domain - update as needed)

**Configuration Details:**
- Supports GET, POST, PUT, DELETE, OPTIONS HTTP methods
- Allows all headers (`*`)
- Enables credentials (cookies, auth headers)
- Preflight requests cached for 10 minutes

---

##  Global Exception Handler (GlobalExceptionHandler.java)
Catches errors throughout the application and returns structured JSON responses instead of raw exceptions.

### Error Response Structure
```json
{
  "timestamp": "2026-08-14T13:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Invalid request parameters",
  "fieldErrors": {
    "message": "Message cannot be empty"
  },
  "path": "/api/chat"
}
```

### Handled Exceptions

| Exception | HTTP Status | Use Case |
|-----------|------------|----------|
| **MethodArgumentNotValidException** | 400 Bad Request | Invalid request body (e.g., empty message) |
| **OpenAIApiException** | Varies (429, 401, 503) | OpenAI API errors (rate limits, auth, downtime) |
| **DatabaseException** | 503 Service Unavailable | Database connection failures |
| **ResourceNotFoundException** | 404 Not Found | Resource doesn't exist |
| **UnauthorizedException** | 401 Unauthorized | User lacks permissions |
| **General Exception** | 500 Internal Server Error | Unexpected errors |

---

##  Improved DTOs (Request/Response)

### ChatRequestDto
```java
@NotBlank(message = "Message cannot be empty")
@Size(min = 1, max = 2000, message = "Message must be between 1 and 2000 characters")
private String message;
```

**Validation Rules:**
- Message cannot be empty (blank spaces rejected)
- Must be between 1-2000 characters
- Violations return 400 Bad Request with field-level error details

### ChatResponseDto
```java
private String response;
```

---

## 🚀 Using with Vue.js Frontend

### Basic Example (Axios)
```javascript
import axios from 'axios';

// Configure CORS origin
const API_URL = 'http://localhost:8080/api/chat';

// Send message to backend
async function sendMessage(userMessage) {
  try {
    const response = await axios.post(API_URL, {
      message: userMessage
    });
    
    console.log('AI Response:', response.data.response);
    return response.data.response;
  } catch (error) {
    // Handle structured error response
    if (error.response?.data) {
      console.error('Error:', error.response.data.message);
      console.error('Status:', error.response.status);
      // Show field errors to user
      if (error.response.data.fieldErrors) {
        Object.entries(error.response.data.fieldErrors).forEach(([field, msg]) => {
          console.error(`${field}: ${msg}`);
        });
      }
    }
  }
}
```

### Using Fetch API (No Dependencies)
```javascript
async function sendMessage(userMessage) {
  try {
    const response = await fetch('http://localhost:8080/api/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include', // Include cookies if using auth
      body: JSON.stringify({ message: userMessage })
    });

    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(`${errorData.status}: ${errorData.message}`);
    }

    const data = await response.json();
    return data.response;
  } catch (error) {
    console.error('Error:', error.message);
  }
}
```

### Health Check Endpoint
```javascript
// Check if backend is running
async function checkBackendHealth() {
  try {
    const response = await fetch('http://localhost:8080/api/chat/health');
    const message = await response.text();
    console.log(message); // "AI Customer Support Chatbot is running"
    return response.ok;
  } catch (error) {
    console.error('Backend is down:', error);
    return false;
  }
}
```

---

## 🔧 Testing with PowerShell

### Test Valid Request
```powershell
$body = @{ message = "Hello! Can you help me?" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body $body
```

### Test Invalid Request (Empty Message)
```powershell
$body = @{ message = "" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Body $body
```
**Expected Response:**
```json
{
  "timestamp": "2026-08-14T13:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Invalid request parameters",
  "fieldErrors": {
    "message": "Message cannot be empty"
  },
  "path": "/api/chat"
}
```

### Test Health Check
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/chat/health" -Method Get
```
**Expected Response:** `"AI Customer Support Chatbot is running"`

---

##  Security Notes

1. **CORS is production-safe:** Only whitelisted origins can access the backend
2. **Frontend origin validation:** Always update `https://yourdomain.com` in SecurityConfig for production
3. **Error details:** Sensitive errors don't leak stack traces to the client
4. **Request validation:** Invalid inputs are rejected before reaching service layer

---


## Next Steps in Roadmap

 **Completed:**
- CORS configuration for Vue.js
- Global exception handling
- Input validation

**Planned:**
- pgvector setup for RAG (Retrieval-Augmented Generation)
- Chat session persistence
- Admin APIs for document management
- Rate limiting middleware
- Database migrations (Flyway/Liquibase)

---

##  Files Created/Modified

**Created:**
- `GlobalExceptionHandler.java` - Centralized error handling
- `ResourceNotFoundException.java` - Custom exception
- `DatabaseException.java` - Custom exception
- `UnauthorizedException.java` - Custom exception
- `OpenAIApiException.java` - Custom exception with HTTP status
- `ChatRequestDto.java` - Validated request DTO
- `ChatResponseDto.java` - Response DTO

**Modified:**
- `SecurityConfig.java` - Added CORS configuration
- `ChatController.java` - Enhanced with validation and DTOs
- `ChatService.java` - Fixed API call method

**Git Commit:** `50b0f43`
