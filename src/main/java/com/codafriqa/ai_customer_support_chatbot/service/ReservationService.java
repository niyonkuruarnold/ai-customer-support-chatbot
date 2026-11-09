package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.CreateReservationRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.ReservationDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.Reservation;
import com.codafriqa.ai_customer_support_chatbot.model.ReservationStatus;
import com.codafriqa.ai_customer_support_chatbot.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Tool borrowing and reservation management: creates reservations with
 * availability validation, enforces the status lifecycle
 * (PENDING → APPROVED → CHECKED_OUT → RETURNED), and provides conflict
 * detection for the scheduling UI.
 *
 * Illegal transitions throw IllegalArgumentException (mapped to 400 by the
 * global exception handler).
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;

    public ReservationService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    /**
     * Create a new reservation. Validates that the date range is valid and
     * that no active reservation already covers the requested tool in the
     * same window.
     *
     * @throws IllegalArgumentException if dates are invalid or there is a conflict
     */
    public Reservation createReservation(CreateReservationRequest request) {
        if (request.toolId() == null || request.borrowerId() == null) {
            throw new IllegalArgumentException("toolId and borrowerId are required");
        }
        if (request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw new IllegalArgumentException("startDate must be on or before endDate");
        }
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("startDate cannot be in the past");
        }

        // Check for overlapping active reservations on the same tool
        List<Reservation> conflicts = reservationRepository.findConflicting(
                request.toolId(), request.startDate(), request.endDate());
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tool " + request.toolId() + " is not available from "
                            + request.startDate() + " to " + request.endDate()
                            + " — overlapping reservation(s) exist");
        }

        Reservation reservation = new Reservation(
                request.toolId(), request.borrowerId(),
                request.startDate(), request.endDate());
        reservation.setNotes(request.notes());
        return reservationRepository.save(reservation);
    }

    /**
     * Check whether a tool is available for the given date range.
     * Returns true if no active reservation overlaps.
     */
    public boolean isAvailable(Long toolId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) return false;
        if (startDate.isAfter(endDate)) return false;
        return reservationRepository.findConflicting(toolId, startDate, endDate).isEmpty();
    }

    /** Get a single reservation by id. */
    public Reservation getReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
    }

    /** List all reservations for a borrower, newest first. */
    public List<Reservation> getMyReservations(Long borrowerId) {
        return reservationRepository.findByBorrowerIdOrderByCreatedAtDesc(borrowerId);
    }

    /** List all reservations for a tool (owner view). */
    public List<Reservation> getToolReservations(Long toolId) {
        return reservationRepository.findByToolIdOrderByCreatedAtDesc(toolId);
    }

    /** List all active reservations across the system (admin view). */
    public List<Reservation> getAllActive() {
        return reservationRepository.findByStatusInOrderByCreatedAtDesc(
                List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED, ReservationStatus.CHECKED_OUT));
    }

    // ---- Lifecycle transitions ----

    /** Approve a pending reservation. */
    public Reservation approve(Long id) {
        Reservation r = getReservation(id);
        transitionTo(r, ReservationStatus.APPROVED);
        return reservationRepository.save(r);
    }

    /** Reject a pending reservation. */
    public Reservation reject(Long id) {
        Reservation r = getReservation(id);
        transitionTo(r, ReservationStatus.REJECTED);
        return reservationRepository.save(r);
    }

    /** Mark as checked out (borrower picked up the tool). */
    public Reservation checkout(Long id) {
        Reservation r = getReservation(id);
        transitionTo(r, ReservationStatus.CHECKED_OUT);
        return reservationRepository.save(r);
    }

    /** Mark as returned (borrower brought the tool back). */
    public Reservation returnTool(Long id) {
        Reservation r = getReservation(id);
        transitionTo(r, ReservationStatus.RETURNED);
        return reservationRepository.save(r);
    }

    /**
     * Enforce the reservation state machine:
     *
     *   PENDING → APPROVED → CHECKED_OUT → RETURNED
     *   PENDING → REJECTED
     *
     * Throws IllegalArgumentException on any invalid transition.
     */
    private void transitionTo(Reservation reservation, ReservationStatus target) {
        ReservationStatus current = reservation.getStatus();
        if (!canTransition(current, target)) {
            throw new IllegalArgumentException(
                    "Invalid reservation status transition: " + current + " -> " + target
                            + " (reservation " + reservation.getId() + ")");
        }
        reservation.setStatus(target);
    }

    private static boolean canTransition(ReservationStatus from, ReservationStatus to) {
        return switch (to) {
            case APPROVED -> from == ReservationStatus.PENDING;
            case REJECTED -> from == ReservationStatus.PENDING;
            case CHECKED_OUT -> from == ReservationStatus.APPROVED;
            case RETURNED -> from == ReservationStatus.CHECKED_OUT;
            default -> false;
        };
    }

    /** Convert entity to DTO. */
    public ReservationDto toDto(Reservation reservation) {
        return new ReservationDto(
                reservation.getId(),
                reservation.getToolId(),
                reservation.getBorrowerId(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getStatus(),
                reservation.getNotes(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
