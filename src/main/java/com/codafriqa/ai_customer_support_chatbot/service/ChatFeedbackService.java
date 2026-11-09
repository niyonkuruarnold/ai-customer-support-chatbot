package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.ChatFeedbackDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.ChatFeedback;
import com.codafriqa.ai_customer_support_chatbot.model.ChatSession;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatFeedbackRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing chat feedback (CSAT scores).
 * Handles submission and retrieval of post-chat satisfaction feedback.
 */
@Service
public class ChatFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ChatFeedbackService.class);

    private final ChatFeedbackRepository feedbackRepository;
    private final ChatSessionRepository sessionRepository;

    public ChatFeedbackService(ChatFeedbackRepository feedbackRepository,
                               ChatSessionRepository sessionRepository) {
        this.feedbackRepository = feedbackRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Submit feedback for a chat session.
     * 
     * @param request the feedback DTO containing sessionId, rating, and optional comment
     * @return the saved feedback entity
     * @throws ResourceNotFoundException if session doesn't exist
     * @throws IllegalArgumentException if feedback already exists for this session
     */
    @Transactional
    public ChatFeedback submitFeedback(ChatFeedbackDto request) {
        // Validate session exists
        ChatSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + request.sessionId()));

        // Check if feedback already exists
        if (feedbackRepository.existsBySessionId(request.sessionId())) {
            throw new IllegalArgumentException("Feedback already submitted for session: " + request.sessionId());
        }

        // Create and save feedback
        ChatFeedback feedback = new ChatFeedback(
                request.sessionId(),
                request.rating(),
                request.comment()
        );

        ChatFeedback saved = feedbackRepository.save(feedback);
        log.info("Chat feedback submitted for session {}: rating={}, hasComment={}",
                request.sessionId(), request.rating(), request.comment() != null);
        return saved;
    }

    /**
     * Get feedback for a specific chat session.
     * 
     * @param sessionId the chat session ID
     * @return Optional containing the feedback if it exists
     */
    public Optional<ChatFeedback> getFeedbackBySession(Long sessionId) {
        return feedbackRepository.findBySessionId(sessionId);
    }

    /**
     * Check if feedback has already been submitted for a session.
     * 
     * @param sessionId the chat session ID
     * @return true if feedback exists
     */
    public boolean hasFeedback(Long sessionId) {
        return feedbackRepository.existsBySessionId(sessionId);
    }

    /**
     * Get average CSAT rating across all sessions.
     * 
     * @return Optional containing the average rating, or empty if no feedback exists
     */
    public Optional<Double> getAverageRating() {
        return feedbackRepository.findAverageRating();
    }

    /**
     * Get average CSAT rating for sessions that were escalated to agents.
     * 
     * @return Optional containing the average rating, or empty if no feedback exists
     */
    public Optional<Double> getAverageEscalatedRating() {
        return feedbackRepository.findAverageEscalatedRating();
    }

    /**
     * Get average CSAT rating for AI-only sessions (not escalated).
     * 
     * @return Optional containing the average rating, or empty if no feedback exists
     */
    public Optional<Double> getAverageAiOnlyRating() {
        return feedbackRepository.findAverageAiOnlyRating();
    }

    /**
     * Get total feedback count.
     * 
     * @return the number of feedback entries
     */
    public long getFeedbackCount() {
        return feedbackRepository.count();
    }
}
