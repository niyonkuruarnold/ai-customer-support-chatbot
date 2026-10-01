package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.config.BearerTokenStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final BearerTokenStore tokenStore;

    public AuthController(AuthenticationManager authenticationManager, BearerTokenStore tokenStore) {
        this.authenticationManager = authenticationManager;
        this.tokenStore = tokenStore;
    }

    /**
     * Exchange username + password for a Bearer token.
     *
     * This is the only sign-in endpoint: the password is always verified by
     * the AuthenticationManager (in-memory Spring Security users), so no
     * passwordless shortcut into a staff role exists.
     */
    @PostMapping("/token")
    public ResponseEntity<?> issueToken(@Valid @RequestBody TokenRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            BearerTokenStore.IssuedToken token = tokenStore.issue(authentication);
            return ResponseEntity.ok(Map.of(
                    "accessToken", token.accessToken(),
                    "tokenType", "Bearer",
                    "expiresIn", token.expiresIn()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", 401, "error", "Unauthorized"));
        }
    }

    public record TokenRequest(@NotBlank String username, @NotBlank String password) { }
}