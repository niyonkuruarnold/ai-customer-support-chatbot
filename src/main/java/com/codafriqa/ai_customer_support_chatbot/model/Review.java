package com.codafriqa.ai_customer_support_chatbot.model;

import com.codafriqa.ai_customer_support_chatbot.service.SystemDataSyncListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Review and rating entity for tool borrowing feedback.
 * Stores user feedback for completed tool reservations.
 */
@Entity
@Table(name = "reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"toolId", "reviewerId", "reservationId"})
})
@EntityListeners(SystemDataSyncListener.class)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotNull(message = "Tool ID is required")
    private Long toolId;

    @Column(nullable = false)
    @NotNull(message = "Reviewer ID is required")
    private Long reviewerId;

    @Column(nullable = false)
    @NotNull(message = "Reservation ID is required")
    private Long reservationId;

    @Column(nullable = false)
    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    public Review() {
    }

    public Review(Long toolId, Long reviewerId, Long reservationId, Integer rating, String comment) {
        this.toolId = toolId;
        this.reviewerId = reviewerId;
        this.reservationId = reservationId;
        this.rating = rating;
        this.comment = comment;
    }

    @PrePersist
    public void onCreate() {
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }

    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
