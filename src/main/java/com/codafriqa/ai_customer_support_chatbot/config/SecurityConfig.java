package com.codafriqa.ai_customer_support_chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

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

        return new InMemoryUserDetailsManager(admin, agent);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Defines authorization rules, disables CSRF for stateless REST APIs,
        // and enables HTTP Basic auth/request authorization.
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            // Agent workspace endpoints use HTTP Basic (agent sign-in in the frontend)
            .httpBasic(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                // Swagger / OpenAPI docs — always accessible
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                // Chat endpoint — public (anonymous customer sessions)
                .requestMatchers("/api/chat/**").permitAll()

                // ── Agent workspace (authenticated, role checked at method level) ──
                .requestMatchers("/api/agent/**", "/api/v1/agent/**").authenticated()

                // ── Knowledge base: vector uploads → ADMIN only ──
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**", "/api/v1/admin/**").authenticated()

                // ── Ticket lifecycle dashboard ──
                // DELETE → ADMIN only
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/tickets/**", "/api/v1/tickets/**").hasRole("ADMIN")
                // PATCH (status/agent updates) → ADMIN or AGENT
                .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/tickets/**", "/api/v1/tickets/**").hasAnyRole("ADMIN", "AGENT")
                // GET (list/view) → ADMIN or AGENT
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/tickets/**", "/api/v1/tickets/**").hasAnyRole("ADMIN", "AGENT")
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
        
        // Allow Vue.js frontend origins
        corsConfig.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",      // Vue dev server
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