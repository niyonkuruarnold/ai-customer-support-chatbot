package com.codafriqa.ai_customer_support_chatbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration using STOMP protocol with SockJS fallback.
 * 
 * Sets up:
 * - In-memory message broker for topics
 * - STOMP endpoints for client connections
 * - Message routing for chat channels
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure the message broker.
     * - In-memory broker for /topic (broadcast) and /queue (point-to-point)
     * - Application destination prefix for messages sent from clients
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory message broker
        // /topic: broadcast to all subscribers (chat messages, summaries)
        // /queue: point-to-point messaging (future use for agent assignment)
        config.enableSimpleBroker("/topic", "/queue");
        
        // Prefix for messages bound for @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Register STOMP endpoints for WebSocket connections.
     * SockJS fallback enabled for browsers that don't support WebSocket.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(
                    "http://localhost:5173",
                    "http://localhost:3000",
                    "http://localhost:8080"
                )
                .withSockJS();
    }
}
