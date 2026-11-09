package com.codafriqa.ai_customer_support_chatbot.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String DEMO_ACCOUNT_PASSWORD = "Password123!";

    @Value("${spring.security.user.name:admin}")
    private String defaultUsername;

    @Value("${spring.security.user.password:admin123}")
    private String defaultPassword;

    /**
     * In-memory user store with role-based accounts.
     *
     * - admin / admin123 → ROLE_ADMIN (full access: delete, knowledge base)
     * - agent / agent123  → ROLE_AGENT (ticket management only)
     *
     * Passwords are bcrypt-hashed. The default admin account is always
     * created; the agent account is optional and created on first boot.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var admin = User.builder()
                .username(defaultUsername)
                .password(encoder.encode(defaultPassword))
                .roles("ADMIN")
                .build();

        var agent = User.builder()
                .username("agent")
                .password(encoder.encode("agent123"))
                .roles("AGENT")
                .build();

        var manager = User.builder()
                .username("manager")
                .password(encoder.encode("manager123"))
                .roles("MANAGER")
                .build();

        var editor = User.builder()
                .username("editor")
                .password(encoder.encode("editor123"))
                .roles("EDITOR")
                .build();

        var demoAdmin = User.builder()
            .username("admin@codafriqa.local")
            .password(encoder.encode(DEMO_ACCOUNT_PASSWORD))
            .roles("ADMIN")
            .build();

        var demoAgent = User.builder()
            .username("agent@codafriqa.local")
            .password(encoder.encode(DEMO_ACCOUNT_PASSWORD))
            .roles("AGENT")
            .build();

        var demoCustomer = User.builder()
            .username("customer@codafriqa.local")
            .password(encoder.encode(DEMO_ACCOUNT_PASSWORD))
            .roles("CUSTOMER")
            .build();

        return new InMemoryUserDetailsManager(admin, agent, manager, editor, demoAdmin, demoAgent, demoCustomer);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, BearerTokenFilter bearerTokenFilter) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            // HTTP Basic is OFF: a Basic challenge (`WWW-Authenticate`) is what
            // makes browsers pop up their native "Sign in to access this site"
            // dialog instead of letting the Vue app render its in-page sign-in
            // card. Credentials are exchanged for a bearer token at
            // POST /api/auth/token instead.
            .httpBasic(httpBasic -> httpBasic.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    // JSON 401 — deliberately WITHOUT a WWW-Authenticate header,
                    // so the browser never opens its native Basic-Auth popup and
                    // the frontend can show its own sign-in UI instead.
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"Unauthorized\"}");
                }))
            .addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Swagger / OpenAPI docs — always accessible
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                // Chat endpoint — public (anonymous customer sessions)
                .requestMatchers("/api/chat/**").permitAll()
                // Token exchange is the ONLY auth endpoint — a password check
                // is always required (no passwordless shortcut exists).
                .requestMatchers("/api/auth/token").permitAll()

                // ── Agent workspace (authenticated, role checked at method level) ──
                .requestMatchers("/api/agent/**", "/api/v1/agent/**").hasAnyRole("ADMIN", "AGENT")

                // ── Knowledge base: vector uploads → ADMIN only ──
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**", "/api/v1/admin/**").authenticated()

                // ── Ticket lifecycle dashboard ──
                // DELETE → ADMIN only
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/tickets/**", "/api/v1/tickets/**").hasRole("ADMIN")
                // PATCH (status/agent updates) → ADMIN or AGENT
                .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/tickets/**", "/api/v1/tickets/**").hasAnyRole("ADMIN", "AGENT")
                // GET (list/view) → ADMIN, AGENT or MANAGER
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/tickets/**", "/api/v1/tickets/**").hasAnyRole("ADMIN", "AGENT", "MANAGER")
                // POST (close, etc.) → ADMIN or AGENT
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/tickets/**", "/api/v1/tickets/**").hasAnyRole("ADMIN", "AGENT")

                // Tool management — public read access for the System Indexer dashboard;
                // writes are open (demo mode) or can be locked down with role checks.
                .requestMatchers("/api/tools/**", "/api/v1/tools/**").permitAll()
                // Maintenance log endpoints — public for the dashboard
                .requestMatchers("/api/maintenance/**", "/api/v1/maintenance/**").permitAll()
                // Tool reservation endpoints require authentication
                .requestMatchers("/api/reservations/**", "/api/v1/reservations/**").authenticated()
                // User profile (current user) requires authentication
                .requestMatchers("/api/users/me").authenticated()
                .anyRequest().permitAll()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // Allow browser origins (dev server and plain-port production hosts)
        corsConfig.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",      // Vite dev server
            "http://127.0.0.1:5173",      // Vite dev server via loopback address
            "http://localhost",           // Production Nginx on port 80
            "http://127.0.0.1",           // Production Nginx via loopback on port 80
            "http://localhost:3000",      // Alternative frontend port
            "http://localhost:8081",      // Alternative port
            "https://yourdomain.com"      // Production domain (replace with actual)
        ));
        
        // Allow HTTP methods needed for chat and admin APIs
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        
        // Allow headers from client
        corsConfig.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials (cookies, auth headers)
        corsConfig.setAllowCredentials(true);
        
        // Cache preflight requests for 10 minutes
        corsConfig.setMaxAge(600L);
        
        // Expose custom headers to client if needed
        corsConfig.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        
        return source;
    }
}