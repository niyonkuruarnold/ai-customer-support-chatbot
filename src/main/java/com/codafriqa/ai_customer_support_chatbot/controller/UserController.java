package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.UserRequestDto;
import com.codafriqa.ai_customer_support_chatbot.dto.UserResponseDto;
import com.codafriqa.ai_customer_support_chatbot.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "User endpoints including current-user profile")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Get the currently authenticated user's profile (id, email, role).
     * GET /api/users/me
     */
    @Operation(
            summary = "Get current user",
            description = "Return the profile (id, email, role) of the currently authenticated user. " +
                    "Requires HTTP Basic authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = auth.getName(); // HTTP Basic username = email
        Optional<UserResponseDto> dbUser = userService.findByEmail(username);
        if (dbUser.isPresent()) {
            return ResponseEntity.ok(dbUser.get());
        }
        // Fallback: derive the role from Spring Security authorities.
        // This covers in-memory users (SecurityConfig) that don't have a
        // database row — the agent store relies on this endpoint to
        // determine the user's role for the frontend navigation.
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .findFirst()
                .orElse("AGENT");
        return ResponseEntity.ok(new UserResponseDto(null, null, username, role));
    }

    /**
     * Create a new user -> POST /api/users
     */
    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody UserRequestDto requestDto) {
        UserResponseDto createdUser = userService.createUser(requestDto);
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
    }

    /**
     * Get all users -> GET /api/users
     */
    @GetMapping
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        List<UserResponseDto> users = userService.getAllUsers();
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    /**
     * Get a user by ID -> GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id) {
        Optional<UserResponseDto> user = userService.getUserById(id);
        return user.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}