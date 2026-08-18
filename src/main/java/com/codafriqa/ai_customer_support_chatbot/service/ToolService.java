package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.CreateToolRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.ToolDto;
import com.codafriqa.ai_customer_support_chatbot.dto.UpdateToolStatusRequest;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.Tool;
import com.codafriqa.ai_customer_support_chatbot.model.ToolStatus;
import com.codafriqa.ai_customer_support_chatbot.repository.ToolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tool management service: handles tool CRUD, status updates, and availability.
 *
 * Status transitions:
 * - AVAILABLE → IN_MAINTENANCE (owner puts tool in maintenance)
 * - IN_MAINTENANCE → AVAILABLE (maintenance complete)
 * - AVAILABLE → BORROWED (reservation checkout)
 * - BORROWED → AVAILABLE (reservation return)
 */
@Service
public class ToolService {

    private final ToolRepository toolRepository;

    public ToolService(ToolRepository toolRepository) {
        this.toolRepository = toolRepository;
    }

    /**
     * Create a new tool.
     *
     * @throws IllegalArgumentException if required fields are missing
     */
    @Transactional
    public Tool createTool(CreateToolRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Tool name is required");
        }
        if (request.ownerId() == null) {
            throw new IllegalArgumentException("Owner ID is required");
        }
        if (request.category() == null || request.category().isBlank()) {
            throw new IllegalArgumentException("Category is required");
        }

        Tool tool = new Tool(
                request.name(),
                request.description(),
                request.category(),
                request.ownerId());
        return toolRepository.save(tool);
    }

    /**
     * Get a tool by ID.
     */
    public Tool getTool(Long id) {
        return toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + id));
    }

    /**
     * Get all tools owned by a user.
     */
    public List<Tool> getToolsByOwner(Long ownerId) {
        return toolRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    /**
     * Get all tools with a specific status.
     */
    public List<Tool> getToolsByStatus(ToolStatus status) {
        return toolRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /**
     * Update tool availability status.
     *
     * @throws IllegalArgumentException if the status transition is invalid
     */
    @Transactional
    public Tool updateStatus(Long toolId, UpdateToolStatusRequest request) {
        if (request.status() == null) {
            throw new IllegalArgumentException("Status is required");
        }

        Tool tool = getTool(toolId);
        ToolStatus newStatus = request.status();

        // Validate status transition
        if (!canTransition(tool.getStatus(), newStatus)) {
            throw new IllegalArgumentException(
                    "Invalid status transition: " + tool.getStatus() + " -> " + newStatus
                            + " (tool " + toolId + ")");
        }

        tool.setStatus(newStatus);
        return toolRepository.save(tool);
    }

    /**
     * Validate status transitions:
     *
     * AVAILABLE → IN_MAINTENANCE
     * IN_MAINTENANCE → AVAILABLE
     * AVAILABLE → BORROWED (managed by ReservationService)
     * BORROWED → AVAILABLE (managed by ReservationService)
     */
    private static boolean canTransition(ToolStatus from, ToolStatus to) {
        return switch (to) {
            case IN_MAINTENANCE -> from == ToolStatus.AVAILABLE;
            case AVAILABLE -> from == ToolStatus.IN_MAINTENANCE || from == ToolStatus.BORROWED;
            case BORROWED -> from == ToolStatus.AVAILABLE;
        };
    }

    /**
     * Convert entity to DTO.
     */
    public ToolDto toDto(Tool tool) {
        return new ToolDto(
                tool.getId(),
                tool.getName(),
                tool.getDescription(),
                tool.getCategory(),
                tool.getOwnerId(),
                tool.getStatus(),
                tool.getCreatedAt(),
                tool.getUpdatedAt());
    }
}
