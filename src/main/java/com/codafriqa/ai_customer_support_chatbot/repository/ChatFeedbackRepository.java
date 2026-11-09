package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.ChatFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for ChatFeedback entity operations.
 */
@Repository
public interface ChatFeedbackRepository extends JpaRepository<ChatFeedback, Long> {

    /**
     * Find feedback for a specific chat session.
     */
    Optional<ChatFeedback> findBySessionId(Long sessionId);

    /**
     * Check if feedback already exists for a session.
     */
    boolean existsBySessionId(Long sessionId);

    /**
     * Calculate average CSAT rating.
     */
    @Query("SELECT AVG(f.rating) FROM ChatFeedback f")
    Optional<Double> findAverageRating();

    /**
     * Calculate average CSAT rating for sessions escalated to agents.
     */
    @Query("SELECT AVG(f.rating) FROM ChatFeedback f WHERE f.sessionId IN " +
           "(SELECT cs.id FROM ChatSession cs WHERE cs.status = 'ESCALATED')")
    Optional<Double> findAverageEscalatedRating();

    /**
     * Calculate average CSAT rating for AI-only sessions.
     */
    @Query("SELECT AVG(f.rating) FROM ChatFeedback f WHERE f.sessionId IN " +
           "(SELECT cs.id FROM ChatSession cs WHERE cs.status != 'ESCALATED')")
    Optional<Double> findAverageAiOnlyRating();
}
