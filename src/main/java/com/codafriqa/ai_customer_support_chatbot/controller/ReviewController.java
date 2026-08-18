package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.AverageRatingDto;
import com.codafriqa.ai_customer_support_chatbot.dto.CreateReviewRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.ReviewDto;
import com.codafriqa.ai_customer_support_chatbot.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for tool reviews and ratings.
 *
 * - Submit reviews for completed tool rentals
 * - Get average ratings per tool and per user
 * - List reviews for tools and users
 */
@RestController
@RequestMapping({"/api/reviews", "/api/v1/reviews"})
@Tag(name = "Tool Reviews", description = "Star ratings and review feedback for borrowed tools")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(
            summary = "Submit a review",
            description = "Submit a rating and optional comment for a completed tool rental.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Review submitted"),
            @ApiResponse(responseCode = "400", description = "Invalid rating or reservation not eligible for review"),
    })
    @PostMapping
    public ResponseEntity<ReviewDto> submitReview(@RequestBody CreateReviewRequest request) {
        ReviewDto dto = reviewService.toDto(reviewService.submitReview(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(
            summary = "Get review by ID",
            description = "Retrieve a single review's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review found"),
            @ApiResponse(responseCode = "404", description = "Review not found"),
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReviewDto> getReview(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.toDto(reviewService.getReview(id)));
    }

    @Operation(
            summary = "Get reviews for a tool",
            description = "List all reviews for a specific tool, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review list returned"),
    })
    @GetMapping("/tool/{toolId}")
    public ResponseEntity<List<ReviewDto>> getReviewsByTool(@PathVariable Long toolId) {
        List<ReviewDto> dtos = reviewService.getReviewsByTool(toolId)
                .stream().map(reviewService::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get reviews by user",
            description = "List all reviews submitted by a specific user, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review list returned"),
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReviewDto>> getReviewsByUser(@PathVariable Long userId) {
        List<ReviewDto> dtos = reviewService.getReviewsByUser(userId)
                .stream().map(reviewService::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get average rating for a tool",
            description = "Returns the average rating and total review count for a tool.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Average rating returned"),
    })
    @GetMapping("/tool/{toolId}/average")
    public ResponseEntity<AverageRatingDto> getAverageRatingForTool(@PathVariable Long toolId) {
        return ResponseEntity.ok(reviewService.getAverageRatingForTool(toolId));
    }

    @Operation(
            summary = "Get average rating for a user",
            description = "Returns the average rating given by a user and their total review count.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Average rating returned"),
    })
    @GetMapping("/user/{userId}/average")
    public ResponseEntity<AverageRatingDto> getAverageRatingForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(reviewService.getAverageRatingForUser(userId));
    }

    @Operation(
            summary = "Check if reservation has been reviewed",
            description = "Returns whether a reservation already has a review.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check result"),
    })
    @GetMapping("/reservation/{reservationId}/status")
    public ResponseEntity<Map<String, Object>> checkReviewStatus(@PathVariable Long reservationId) {
        boolean reviewed = reviewService.hasReview(reservationId);
        return ResponseEntity.ok(Map.of(
                "reservationId", reservationId,
                "reviewed", reviewed));
    }
}
