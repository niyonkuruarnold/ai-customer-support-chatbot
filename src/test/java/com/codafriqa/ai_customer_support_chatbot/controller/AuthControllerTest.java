package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.config.BearerTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the password-required sign-in contract:
 *
 * <ul>
 *   <li>{@code POST /api/auth/token} issues a Bearer token only after the
 *       AuthenticationManager accepts the username/password pair — there is
 *       no passwordless shortcut into a staff role.</li>
 *   <li>Rejected credentials answer 401 with the JSON error payload the
 *       frontend renders on the AgentSignIn card.</li>
 * </ul>
 */
class AuthControllerTest {

    private final BearerTokenStore tokenStore = new BearerTokenStore();

    /** Stand-in manager that accepts any credentials for the given user. */
    private static AuthenticationManager accepting(String username) {
        return authentication -> new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    /** Stand-in manager that fails every credential check, like a wrong password. */
    private static AuthenticationManager rejecting() {
        return authentication -> {
            throw new BadCredentialsException("Bad credentials");
        };
    }

    @Test
    void issuesBearerTokenWhenThePasswordCheckPasses() {
        AuthController controller = new AuthController(accepting("admin"), tokenStore);

        var response = controller.issueToken(new AuthController.TokenRequest("admin", "admin123"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("Bearer", body.get("tokenType"));

        var authenticated = tokenStore.authenticate((String) body.get("accessToken"));
        assertNotNull(authenticated);
        assertEquals("admin", authenticated.getName());
    }

    @Test
    void rejectsBadPasswordsWithJson401() {
        AuthController controller = new AuthController(rejecting(), tokenStore);

        var response = controller.issueToken(new AuthController.TokenRequest("admin", "wrong-password"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Map.of("status", 401, "error", "Unauthorized"), response.getBody());
    }
}