package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.AverageRatingDto;
import com.codafriqa.ai_customer_support_chatbot.dto.CreateReviewRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.ReviewDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.Reservation;
import com.codafriqa.ai_customer_support_chatbot.model.ReservationStatus;
import com.codafriqa.ai_customer_support_chatbot.model.Review;
import com.codafriqa.ai_customer_support_chatbot.repository.ReviewRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Review and rating management: submits feedback for completed tool borrows,
 * calculates average ratings per tool, and prevents duplicate reviews.
 *
 * Validation rules:
 * - Rating must be between 1 and 5
 * - Reservation must exist and be RETURNED
 * - One review per reservation (enforced by unique constraint + check)
 * - Reviewer must be the borrower of the reservation
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;

    public ReviewService(ReviewRepository reviewRepository, ReservationRepository reservationRepository) {
        this.reviewRepository = reviewRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * Submit a review for a completed tool borrow.
     *
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Review submitReview(CreateReviewRequest request) {
        if (request.toolId() == null || request.reviewerId() == null || request.reservationId() == null) {
            throw new IllegalArgumentException("toolId, reviewerId, and reservationId are required");
        }
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        // Validate reservation exists
        Reservation reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found: " + request.reservationId()));

        // Validate reservation is RETURNED
        if (reservation.getStatus() != ReservationStatus.RETURNED) {
            throw new IllegalArgumentException(
                    "Can only review returned reservations. Current status: " + reservation.getStatus());
        }

        // Validate reviewer is the borrower
        if (!reservation.getBorrowerId().equals(request.reviewerId())) {
            throw new IllegalArgumentException(
                    "Only the borrower can review this reservation");
        }

        // Validate tool ID matches reservation
        if (!reservation.getToolId().equals(request.toolId())) {
            throw new IllegalArgumentException(
                    "Tool ID does not match reservation");
        }

        // Check for duplicate review
        if (reviewRepository.findByReservationId(request.reservationId()).isPresent()) {
            throw new IllegalArgumentException(
                    "A review already exists for this reservation");
        }

        Review review = new Review(
                request.toolId(),
                request.reviewerId(),
                request.reservationId(),
                request.rating(),
                request.comment());

        return reviewRepository.save(review);
    }

    /**
     * Get a review by ID.
     */
    public Review getReview(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    /**
     * Get all reviews for a specific tool, newest first.
     */
    public List<Review> getReviewsByTool(Long toolId) {
        return reviewRepository.findByToolIdOrderByTimestampDesc(toolId);
    }

    /**
     * Get all reviews by a specific user, newest first.
     */
    public List<Review> getReviewsByUser(Long userId) {
        return reviewRepository.findByReviewerIdOrderByTimestampDesc(userId);
    }

    /**
     * Get average rating for a tool.
     */
    public AverageRatingDto getAverageRatingForTool(Long toolId) {
        Double avg = reviewRepository.findAverageRatingByToolId(toolId).orElse(null);
        Long count = reviewRepository.countByToolId(toolId);
        return new AverageRatingDto(avg, count);
    }

    /**
     * Get average rating for a user as a reviewer.
     */
    public AverageRatingDto getAverageRatingForUser(Long userId) {
        Double avg = reviewRepository.findAverageRatingByUserId(userId).orElse(null);
        Long count = reviewRepository.countByReviewerId(userId);
        return new AverageRatingDto(avg, count);
    }

    /**
     * Check if a reservation has been reviewed.
     */
    public boolean hasReview(Long reservationId) {
        return reviewRepository.findByReservationId(reservationId).isPresent();
    }

    /**
     * Convert entity to DTO.
     */
    public ReviewDto toDto(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getToolId(),
                review.getReviewerId(),
                review.getReservationId(),
                review.getRating(),
                review.getComment(),
                review.getTimestamp());
    }
}
