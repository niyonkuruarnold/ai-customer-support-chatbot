package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.CreateReservationRequest;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.Reservation;
import com.codafriqa.ai_customer_support_chatbot.model.ReservationStatus;
import com.codafriqa.ai_customer_support_chatbot.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    private ReservationService service;

    private static final Long TOOL_ID = 1L;
    private static final Long BORROWER_ID = 10L;
    private static final LocalDate START = LocalDate.now().plusDays(1);
    private static final LocalDate END = LocalDate.now().plusDays(5);

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository);
    }

    private CreateReservationRequest validRequest() {
        return new CreateReservationRequest(TOOL_ID, BORROWER_ID, START, END, "Need it for weekend project");
    }

    private Reservation savedReservation() {
        Reservation r = new Reservation(TOOL_ID, BORROWER_ID, START, END);
        r.setId(1L);
        r.setStatus(ReservationStatus.PENDING);
        r.setNotes("Need it for weekend project");
        return r;
    }

    // ---- createReservation ----

    @Test
    void createReservationSuccess() {
        when(reservationRepository.findConflicting(TOOL_ID, START, END))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(inv -> {
                    Reservation r = inv.getArgument(0);
                    r.setId(1L);
                    return r;
                });

        Reservation result = service.createReservation(validRequest());

        assertEquals(ReservationStatus.PENDING, result.getStatus());
        assertEquals(TOOL_ID, result.getToolId());
        assertEquals(BORROWER_ID, result.getBorrowerId());
        assertEquals(START, result.getStartDate());
        assertEquals(END, result.getEndDate());
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void createReservationRejectsOverlappingDates() {
        Reservation existing = savedReservation();
        when(reservationRepository.findConflicting(TOOL_ID, START, END))
                .thenReturn(List.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createReservation(validRequest()));
        assertTrue(ex.getMessage().contains("not available"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservationRejectsPastStartDate() {
        CreateReservationRequest past = new CreateReservationRequest(
                TOOL_ID, BORROWER_ID, LocalDate.now().minusDays(1), END, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createReservation(past));
        assertTrue(ex.getMessage().contains("past"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservationRejectsEndBeforeStart() {
        CreateReservationRequest reversed = new CreateReservationRequest(
                TOOL_ID, BORROWER_ID, END, START, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createReservation(reversed));
        assertTrue(ex.getMessage().contains("on or before"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservationRejectsMissingToolId() {
        CreateReservationRequest noTool = new CreateReservationRequest(
                null, BORROWER_ID, START, END, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createReservation(noTool));
        assertTrue(ex.getMessage().contains("required"));
    }

    @Test
    void createReservationRejectsMissingBorrowerId() {
        CreateReservationRequest noBorrower = new CreateReservationRequest(
                TOOL_ID, null, START, END, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createReservation(noBorrower));
        assertTrue(ex.getMessage().contains("required"));
    }

    // ---- availability check ----

    @Test
    void isAvailableReturnsTrueWhenNoConflicts() {
        when(reservationRepository.findConflicting(TOOL_ID, START, END))
                .thenReturn(Collections.emptyList());

        assertTrue(service.isAvailable(TOOL_ID, START, END));
    }

    @Test
    void isAvailableReturnsFalseWhenConflictsExist() {
        when(reservationRepository.findConflicting(TOOL_ID, START, END))
                .thenReturn(List.of(savedReservation()));

        assertFalse(service.isAvailable(TOOL_ID, START, END));
    }

    @Test
    void isAvailableReturnsFalseForNullDates() {
        assertFalse(service.isAvailable(TOOL_ID, null, END));
        assertFalse(service.isAvailable(TOOL_ID, START, null));
    }

    @Test
    void isAvailableReturnsFalseForReversedDates() {
        assertFalse(service.isAvailable(TOOL_ID, END, START));
    }

    // ---- getReservation ----

    @Test
    void getReservationFound() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(savedReservation()));

        Reservation result = service.getReservation(1L);
        assertEquals(1L, result.getId());
    }

    @Test
    void getReservationNotFound() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getReservation(999L));
    }

    // ---- lifecycle transitions ----

    @Test
    void approvePendingReservation() {
        Reservation r = savedReservation();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Reservation result = service.approve(1L);
        assertEquals(ReservationStatus.APPROVED, result.getStatus());
    }

    @Test
    void rejectPendingReservation() {
        Reservation r = savedReservation();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Reservation result = service.reject(1L);
        assertEquals(ReservationStatus.REJECTED, result.getStatus());
    }

    @Test
    void checkoutApprovedReservation() {
        Reservation r = savedReservation();
        r.setStatus(ReservationStatus.APPROVED);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Reservation result = service.checkout(1L);
        assertEquals(ReservationStatus.CHECKED_OUT, result.getStatus());
    }

    @Test
    void returnCheckedOutReservation() {
        Reservation r = savedReservation();
        r.setStatus(ReservationStatus.CHECKED_OUT);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Reservation result = service.returnTool(1L);
        assertEquals(ReservationStatus.RETURNED, result.getStatus());
    }

    @Test
    void illegalTransitionsThrow() {
        Reservation r = savedReservation();

        // Cannot checkout a pending reservation directly
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));
        assertThrows(IllegalArgumentException.class, () -> service.checkout(1L));

        // Cannot return an approved (not checked out) reservation
        r.setStatus(ReservationStatus.APPROVED);
        assertThrows(IllegalArgumentException.class, () -> service.returnTool(1L));

        // Cannot approve an already-approved reservation
        assertThrows(IllegalArgumentException.class, () -> service.approve(1L));

        // Cannot reject a returned reservation
        r.setStatus(ReservationStatus.RETURNED);
        assertThrows(IllegalArgumentException.class, () -> service.reject(1L));
    }

    @Test
    void illegalTransitionLeavesStatusUntouched() {
        Reservation r = savedReservation();
        r.setStatus(ReservationStatus.APPROVED);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(r));

        assertThrows(IllegalArgumentException.class, () -> service.returnTool(1L));

        assertEquals(ReservationStatus.APPROVED, r.getStatus());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void missingReservationThrowsNotFoundOnTransition() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.approve(999L));
    }

    // ---- list queries ----

    @Test
    void getMyReservationsDelegatesToRepository() {
        when(reservationRepository.findByBorrowerIdOrderByCreatedAtDesc(BORROWER_ID))
                .thenReturn(List.of(savedReservation()));

        List<Reservation> result = service.getMyReservations(BORROWER_ID);
        assertEquals(1, result.size());
        verify(reservationRepository).findByBorrowerIdOrderByCreatedAtDesc(BORROWER_ID);
    }

    @Test
    void getToolReservationsDelegatesToRepository() {
        when(reservationRepository.findByToolIdOrderByCreatedAtDesc(TOOL_ID))
                .thenReturn(List.of(savedReservation()));

        List<Reservation> result = service.getToolReservations(TOOL_ID);
        assertEquals(1, result.size());
    }

    @Test
    void getAllActiveDelegatesToRepository() {
        when(reservationRepository.findByStatusInOrderByCreatedAtDesc(
                List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED, ReservationStatus.CHECKED_OUT)))
                .thenReturn(List.of(savedReservation()));

        List<Reservation> result = service.getAllActive();
        assertEquals(1, result.size());
    }
}
