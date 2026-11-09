package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Get all reviews for a specific tool, newest first. */
    List<Review> findByToolIdOrderByTimestampDesc(Long toolId);

    /** Get all reviews by a specific reviewer, newest first. */
    List<Review> findByReviewerIdOrderByTimestampDesc(Long reviewerId);

    /** Get a review for a specific reservation (to prevent duplicates). */
    Optional<Review> findByReservationId(Long reservationId);

    /** Get reviews for a tool by a specific reviewer. */
    List<Review> findByToolIdAndReviewerIdOrderByTimestampDesc(Long toolId, Long reviewerId);

    /**
     * Calculate average rating for a specific tool.
     * Returns null if no reviews exist.
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.toolId = :toolId")
    Optional<Double> findAverageRatingByToolId(@Param("toolId") Long toolId);

    /**
     * Calculate average rating for a user as a borrower (tool reviewer).
     * Returns null if no reviews exist.
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewerId = :userId")
    Optional<Double> findAverageRatingByUserId(@Param("userId") Long userId);

    /**
     * Count reviews for a specific tool.
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.toolId = :toolId")
    Long countByToolId(@Param("toolId") Long toolId);

    /**
     * Count reviews by a specific reviewer.
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.reviewerId = :userId")
    Long countByReviewerId(@Param("userId") Long userId);
}
