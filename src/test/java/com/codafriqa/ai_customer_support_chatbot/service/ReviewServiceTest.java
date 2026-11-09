package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.AverageRatingDto;
import com.codafriqa.ai_customer_support_chatbot.dto.CreateReviewRequest;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.Reservation;
import com.codafriqa.ai_customer_support_chatbot.model.ReservationStatus;
import com.codafriqa.ai_customer_support_chatbot.model.Review;
import com.codafriqa.ai_customer_support_chatbot.repository.ReviewRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private ReviewService service;

    private static final Long TOOL_ID = 1L;
    private static final Long REVIEWER_ID = 10L;
    private static final Long RESERVATION_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository, reservationRepository);
    }

    private Reservation returnedReservation() {
        Reservation r = new Reservation(TOOL_ID, REVIEWER_ID,
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));
        r.setId(RESERVATION_ID);
        r.setStatus(ReservationStatus.RETURNED);
        return r;
    }

    private Review savedReview() {
        Review review = new Review(TOOL_ID, REVIEWER_ID, RESERVATION_ID, 5, "Great tool!");
        review.setId(1L);
        return review;
    }

    private CreateReviewRequest validRequest() {
        return new CreateReviewRequest(TOOL_ID, REVIEWER_ID, RESERVATION_ID, 5, "Great tool!");
    }

    // ---- submitReview ----

    @Test
    void submitReviewSuccess() {
        when(reservationRepository.findById(RESERVATION_ID))
                .thenReturn(Optional.of(returnedReservation()));
        when(reviewRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(inv -> {
                    Review r = inv.getArgument(0);
                    r.setId(1L);
                    return r;
                });

        Review result = service.submitReview(validRequest());

        assertEquals(TOOL_ID, result.getToolId());
        assertEquals(REVIEWER_ID, result.getReviewerId());
        assertEquals(RESERVATION_ID, result.getReservationId());
        assertEquals(5, result.getRating());
        assertEquals("Great tool!", result.getComment());
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void submitReviewRejectsMissingToolId() {
        CreateReviewRequest noTool = new CreateReviewRequest(null, REVIEWER_ID, RESERVATION_ID, 5, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReview(noTool));
        assertTrue(ex.getMessage().contains("required"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReviewRejectsMissingReviewerId() {
        CreateReviewRequest noReviewer = new CreateReviewRequest(TOOL_ID, null, RESERVATION_ID, 5, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReview(noReviewer));
        assertTrue(ex.getMessage().contains("required"));
    }

    @Test
    void submitReviewRejectsMissingReservationId() {
        CreateReviewRequest noReservation = new CreateReviewRequest(TOOL_ID, REVIEWER_ID, null, 5, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReview(noReservation));
        assertTrue(ex.getMessage().contains("required"));
    }

    @Test
    void submitReviewRejectsInvalidRating() {
        CreateReviewRequest lowRating = new CreateReviewRequest(TOOL_ID, REVIEWER_ID, RESERVATION_ID, 0, null);
        CreateReviewRequest highRating = new CreateReviewRequest(TOOL_ID, REVIEWER_ID, RESERVATION_ID, 6, null);

        assertThrows(IllegalArgumentException.class, () -> service.submitReview(lowRating));
        assertThrows(IllegalArgumentException.class, () -> service.submitReview(highRating));
    }

    @Test
    void submitReviewRejectsNonReturnedReservation() {
        Reservation pending = returnedReservation();
        pending.setStatus(ReservationStatus.PENDING);
        when(reservationRepository.findById(RESERVATION_ID))
                .thenReturn(Optional.of(pending));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReview(validRequest()));
        assertTrue(ex.getMessage().contains("returned"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReviewRejectsWrongBorrower() {
        Reservation r = returnedReservation();
        r.setBorrowerId(99L); // Different borrower
        when(reservationRepository.findById(RESERVATION_ID))
                .thenReturn(Optional.of(r));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReview(validRequest()));
        assertTrue(ex.getMessage().contains("borrower"));
    }

    @Test
    void submitReviewRejectsWrongToolId() {
        CreateReviewRequest wrongTool = new CreateReviewRequest(99L, REVIEWER_ID, RESERVATION_ID, 5, null);
        when(reservationRepository.findById(RESERVATION_ID))
                .thenReturn(Optional.of(returnedReservation()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReview(wrongTool));
        assertTrue(ex.getMessage().contains("Tool ID"));
    }

    @Test
    void submitReviewRejectsDuplicateReview() {
        when(reservationRepository.findById(RESERVATION_ID))
                .thenReturn(Optional.of(returnedReservation()));
        when(reviewRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(savedReview()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReview(validRequest()));
        assertTrue(ex.getMessage().contains("already exists"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReviewThrowsNotFoundForMissingReservation() {
        when(reservationRepository.findById(999L))
                .thenReturn(Optional.empty());

        CreateReviewRequest missing = new CreateReviewRequest(TOOL_ID, REVIEWER_ID, 999L, 5, null);
        assertThrows(ResourceNotFoundException.class, () -> service.submitReview(missing));
    }

    // ---- getReview ----

    @Test
    void getReviewFound() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(savedReview()));

        Review result = service.getReview(1L);
        assertEquals(1L, result.getId());
    }

    @Test
    void getReviewNotFound() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getReview(999L));
    }

    // ---- list queries ----

    @Test
    void getReviewsByToolDelegatesToRepository() {
        when(reviewRepository.findByToolIdOrderByTimestampDesc(TOOL_ID))
                .thenReturn(List.of(savedReview()));

        List<Review> result = service.getReviewsByTool(TOOL_ID);
        assertEquals(1, result.size());
        verify(reviewRepository).findByToolIdOrderByTimestampDesc(TOOL_ID);
    }

    @Test
    void getReviewsByUserDelegatesToRepository() {
        when(reviewRepository.findByReviewerIdOrderByTimestampDesc(REVIEWER_ID))
                .thenReturn(List.of(savedReview()));

        List<Review> result = service.getReviewsByUser(REVIEWER_ID);
        assertEquals(1, result.size());
    }

    // ---- average ratings ----

    @Test
    void getAverageRatingForTool() {
        when(reviewRepository.findAverageRatingByToolId(TOOL_ID))
                .thenReturn(Optional.of(4.5));
        when(reviewRepository.countByToolId(TOOL_ID))
                .thenReturn(10L);

        AverageRatingDto result = service.getAverageRatingForTool(TOOL_ID);
        assertEquals(4.5, result.averageRating());
        assertEquals(10L, result.reviewCount());
    }

    @Test
    void getAverageRatingForToolWithNoReviews() {
        when(reviewRepository.findAverageRatingByToolId(TOOL_ID))
                .thenReturn(Optional.empty());
        when(reviewRepository.countByToolId(TOOL_ID))
                .thenReturn(0L);

        AverageRatingDto result = service.getAverageRatingForTool(TOOL_ID);
        assertNull(result.averageRating());
        assertEquals(0L, result.reviewCount());
    }

    @Test
    void getAverageRatingForUser() {
        when(reviewRepository.findAverageRatingByUserId(REVIEWER_ID))
                .thenReturn(Optional.of(4.0));
        when(reviewRepository.countByReviewerId(REVIEWER_ID))
                .thenReturn(5L);

        AverageRatingDto result = service.getAverageRatingForUser(REVIEWER_ID);
        assertEquals(4.0, result.averageRating());
        assertEquals(5L, result.reviewCount());
    }

    // ---- hasReview ----

    @Test
    void hasReviewReturnsTrue() {
        when(reviewRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(savedReview()));

        assertTrue(service.hasReview(RESERVATION_ID));
    }

    @Test
    void hasReviewReturnsFalse() {
        when(reviewRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.empty());

        assertFalse(service.hasReview(RESERVATION_ID));
    }

    // ---- toDto ----

    @Test
    void toDtoConvertsCorrectly() {
        Review review = savedReview();
        review.setTimestamp(LocalDateTime.of(2024, 1, 15, 10, 30));

        var dto = service.toDto(review);

        assertEquals(review.getId(), dto.id());
        assertEquals(review.getToolId(), dto.toolId());
        assertEquals(review.getReviewerId(), dto.reviewerId());
        assertEquals(review.getReservationId(), dto.reservationId());
        assertEquals(review.getRating(), dto.rating());
        assertEquals(review.getComment(), dto.comment());
        assertEquals(review.getTimestamp(), dto.timestamp());
    }
}
