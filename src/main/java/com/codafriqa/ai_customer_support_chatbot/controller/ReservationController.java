package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.CreateReservationRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.ReservationDto;
import com.codafriqa.ai_customer_support_chatbot.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for tool borrowing and reservations.
 *
 * - Create reservations with date-range availability checks
 * - Approve / reject / checkout / return lifecycle transitions
 * - Query "my reservations" for a borrower
 */
@RestController
@RequestMapping({"/api/reservations", "/api/v1/reservations"})
@Tag(name = "Tool Reservations", description = "Borrowing, reservation scheduling, and availability checks")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(
            summary = "Create a reservation",
            description = "Request a tool borrow window. Fails if the tool has an overlapping active reservation.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reservation created (PENDING)"),
            @ApiResponse(responseCode = "400", description = "Invalid dates or overlapping reservation"),
    })
    @PostMapping
    public ResponseEntity<ReservationDto> create(@RequestBody CreateReservationRequest request) {
        ReservationDto dto = reservationService.toDto(
                reservationService.createReservation(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(
            summary = "Check tool availability",
            description = "Returns whether a tool is available for the given date range (no active reservation overlap).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Availability check result"),
    })
    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @RequestParam Long toolId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        boolean available = reservationService.isAvailable(toolId, startDate, endDate);
        return ResponseEntity.ok(Map.of(
                "toolId", toolId,
                "startDate", startDate,
                "endDate", endDate,
                "available", available));
    }

    @Operation(
            summary = "Get reservation by ID",
            description = "Retrieve a single reservation's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation found"),
            @ApiResponse(responseCode = "404", description = "Reservation not found"),
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReservationDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.toDto(reservationService.getReservation(id)));
    }

    @Operation(
            summary = "My reservations",
            description = "List all reservations for a borrower, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation list returned"),
    })
    @GetMapping("/my/{borrowerId}")
    public ResponseEntity<List<ReservationDto>> myReservations(@PathVariable Long borrowerId) {
        List<ReservationDto> dtos = reservationService.getMyReservations(borrowerId)
                .stream().map(reservationService::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Reservations for a tool",
            description = "List all reservations for a specific tool (owner view).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation list returned"),
    })
    @GetMapping("/tool/{toolId}")
    public ResponseEntity<List<ReservationDto>> toolReservations(@PathVariable Long toolId) {
        List<ReservationDto> dtos = reservationService.getToolReservations(toolId)
                .stream().map(reservationService::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Approve reservation",
            description = "Approve a PENDING reservation (tool owner action).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation approved"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @ApiResponse(responseCode = "404", description = "Reservation not found"),
    })
    @PostMapping("/{id}/approve")
    public ResponseEntity<ReservationDto> approve(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.toDto(reservationService.approve(id)));
    }

    @Operation(
            summary = "Reject reservation",
            description = "Reject a PENDING reservation (tool owner action).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation rejected"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @ApiResponse(responseCode = "404", description = "Reservation not found"),
    })
    @PostMapping("/{id}/reject")
    public ResponseEntity<ReservationDto> reject(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.toDto(reservationService.reject(id)));
    }

    @Operation(
            summary = "Check out tool",
            description = "Mark an APPROVED reservation as CHECKED_OUT (borrower picked up the tool).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tool checked out"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @ApiResponse(responseCode = "404", description = "Reservation not found"),
    })
    @PostMapping("/{id}/checkout")
    public ResponseEntity<ReservationDto> checkout(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.toDto(reservationService.checkout(id)));
    }

    @Operation(
            summary = "Return tool",
            description = "Mark a CHECKED_OUT reservation as RETURNED (borrower returned the tool).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tool returned"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @ApiResponse(responseCode = "404", description = "Reservation not found"),
    })
    @PostMapping("/{id}/return")
    public ResponseEntity<ReservationDto> returnTool(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.toDto(reservationService.returnTool(id)));
    }
}
