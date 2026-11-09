package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.Reservation;
import com.codafriqa.ai_customer_support_chatbot.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** All reservations for a given borrower, newest first. */
    List<Reservation> findByBorrowerIdOrderByCreatedAtDesc(Long borrowerId);

    /** All reservations for a given tool, newest first. */
    List<Reservation> findByToolIdOrderByCreatedAtDesc(Long toolId);

    /**
     * Find reservations for a tool that overlap with the requested date range
     * and are in an active state (not yet returned or rejected).
     *
     * Two ranges overlap when: startA <= endB AND startB <= endA.
     */
    @Query("""
        SELECT r FROM Reservation r
        WHERE r.toolId = :toolId
          AND r.status IN ('PENDING', 'APPROVED', 'CHECKED_OUT')
          AND r.startDate <= :endDate
          AND r.endDate >= :startDate
    """)
    List<Reservation> findConflicting(
            @Param("toolId") Long toolId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /** All active reservations across the system (admin view). */
    List<Reservation> findByStatusInOrderByCreatedAtDesc(List<ReservationStatus> statuses);
}
