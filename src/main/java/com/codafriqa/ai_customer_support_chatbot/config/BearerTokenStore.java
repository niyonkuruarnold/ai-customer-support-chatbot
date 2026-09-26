package com.codafriqa.ai_customer_support_chatbot.config;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BearerTokenStore {

    private static final long TOKEN_LIFETIME_SECONDS = 8 * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    public IssuedToken issue(Authentication authentication) {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        Instant expiresAt = Instant.now().plusSeconds(TOKEN_LIFETIME_SECONDS);
        tokens.put(token, new TokenEntry(authentication, expiresAt));
        return new IssuedToken(token, TOKEN_LIFETIME_SECONDS);
    }

    public Authentication authenticate(String token) {
        TokenEntry entry = tokens.get(token);
        if (entry == null) return null;
        if (!entry.expiresAt().isAfter(Instant.now())) {
            tokens.remove(token, entry);
            return null;
        }
        return entry.authentication();
    }

    public record IssuedToken(String accessToken, long expiresIn) { }

    private record TokenEntry(Authentication authentication, Instant expiresAt) { }
}