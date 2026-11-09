package com.codafriqa.ai_customer_support_chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * Intercepts STOMP CONNECT frames on the client-inbound channel and
 * validates the JWT token carried in the {@code Authorization} header.
 *
 * <p>If a valid token is present the authenticated principal is attached
 * to the WebSocket session so downstream {@code @MessageMapping} methods
 * can identify the caller.  Anonymous (no-token) connections are allowed
 * for customer chat sessions — only agent/admin connections require a
 * valid token.
 */
@Component
public class WebSocketJwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketJwtChannelInterceptor.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader(AUTH_HEADER);
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String token = authHeaders.get(0);
                if (token != null && token.startsWith(BEARER_PREFIX)) {
                    String jwt = token.substring(BEARER_PREFIX.length());
                    try {
                        Authentication auth = validateToken(jwt);
                        if (auth != null) {
                            accessor.setUser(auth);
                            log.debug("WebSocket CONNECT authenticated: {}", auth.getName());
                        }
                    } catch (Exception e) {
                        log.warn("WebSocket JWT validation failed: {}", e.getMessage());
                        // Allow connection anyway — customer sessions are anonymous
                    }
                }
            } else {
                log.debug("WebSocket CONNECT without Authorization header — anonymous customer session");
            }
        }

        return message;
    }

    /**
     * Validate a JWT and return an Authentication if valid.
     *
     * <p>In a production system this would verify the signature, expiry,
     * and claims using a JWT library (e.g. jjwt, nimbus-jose).  For the
     * current in-memory auth setup the token is treated as the username
     * directly — replace with real JWT parsing when a token issuer is
     * configured.
     *
     * @param token the raw JWT string
     * @return authenticated principal, or null if invalid
     */
    private Authentication validateToken(String token) {
        // Placeholder: extract subject from the token.
        // Replace with real JWT verification (signature + expiry) when
        // a token provider / issuer is introduced.
        if (token == null || token.isBlank()) {
            return null;
        }

        // For the current in-memory auth, treat the token as a username.
        // In production, parse the JWT and extract the "sub" claim.
        String username = token.trim();
        if (username.isEmpty()) {
            return null;
        }

        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }
}
