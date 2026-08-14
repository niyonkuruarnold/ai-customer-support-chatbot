package com.codafriqa.ai_customer_support_chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean

    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Defines authorization rules, disables CSRF for stateless REST APIs, 
        // and enables HTTP Basic auth/request authorization.
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/chat/**").permitAll()
                .anyRequest().permitAll() // Allows access to all endpoints for testing
            );
        
        return http.build();
    }
}