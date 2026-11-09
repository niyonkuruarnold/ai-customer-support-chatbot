package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.CreateMaintenanceLogRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.MaintenanceLogDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.MaintenanceLog;
import com.codafriqa.ai_customer_support_chatbot.model.Tool;
import com.codafriqa.ai_customer_support_chatbot.model.ToolStatus;
import com.codafriqa.ai_customer_support_chatbot.repository.MaintenanceLogRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ToolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Maintenance log management: records service history for tools,
 * schedules upcoming maintenance, and updates tool availability.
 *
 * When a maintenance log is created, the tool is automatically set to
 * IN_MAINTENANCE status if it was AVAILABLE.
 */
@Service
public class MaintenanceLogService {

    private final MaintenanceLogRepository maintenanceLogRepository;
    private final ToolRepository toolRepository;

    public MaintenanceLogService(MaintenanceLogRepository maintenanceLogRepository, ToolRepository toolRepository) {
        this.maintenanceLogRepository = maintenanceLogRepository;
        this.toolRepository = toolRepository;
    }

    /**
     * Create a new maintenance log entry.
     * Automatically sets the tool to IN_MAINTENANCE if it was AVAILABLE.
     *
     * @throws IllegalArgumentException if required fields are missing
     * @throws ResourceNotFoundException if tool does not exist
     */
    @Transactional
    public MaintenanceLog createMaintenanceLog(CreateMaintenanceLogRequest request) {
        if (request.toolId() == null) {
            throw new IllegalArgumentException("Tool ID is required");
        }
        if (request.serviceDate() == null) {
            throw new IllegalArgumentException("Service date is required");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }

        // Verify tool exists
        Tool tool = toolRepository.findById(request.toolId())
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + request.toolId()));

        // Create maintenance log
        MaintenanceLog log = new MaintenanceLog(
                request.toolId(),
                request.serviceDate(),
                request.description());
        log.setCost(request.cost());
        log.setNextServiceDue(request.nextServiceDue());

        MaintenanceLog saved = maintenanceLogRepository.save(log);

        // Update tool status to IN_MAINTENANCE if currently AVAILABLE
        if (tool.getStatus() == ToolStatus.AVAILABLE) {
            tool.setStatus(ToolStatus.IN_MAINTENANCE);
            toolRepository.save(tool);
        }

        return saved;
    }

    /**
     * Get a maintenance log by ID.
     */
    public MaintenanceLog getMaintenanceLog(Long id) {
        return maintenanceLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance log not found: " + id));
    }

    /**
     * Get all maintenance logs for a specific tool, newest first.
     */
    public List<MaintenanceLog> getLogsByTool(Long toolId) {
        return maintenanceLogRepository.findByToolIdOrderByServiceDateDesc(toolId);
    }

    /**
     * Get maintenance logs for a tool within a date range.
     */
    public List<MaintenanceLog> getLogsByToolAndDateRange(Long toolId, LocalDate startDate, LocalDate endDate) {
        return maintenanceLogRepository.findByToolIdAndServiceDateBetweenOrderByServiceDateDesc(
                toolId, startDate, endDate);
    }

    /**
     * Find tools with upcoming maintenance due.
     */
    public List<MaintenanceLog> getUpcomingMaintenance(LocalDate beforeDate) {
        return maintenanceLogRepository.findUpcomingMaintenance(beforeDate);
    }

    /**
     * Get the most recent maintenance log for a tool.
     */
    public MaintenanceLog getLastLogForTool(Long toolId) {
        return maintenanceLogRepository.findFirstByToolIdOrderByServiceDateDesc(toolId);
    }

    /**
     * Get maintenance log count for a tool.
     */
    public Long getLogCountForTool(Long toolId) {
        return maintenanceLogRepository.countByToolId(toolId);
    }

    /**
     * Complete maintenance for a tool (set status back to AVAILABLE).
     *
     * @throws ResourceNotFoundException if tool does not exist
     * @throws IllegalArgumentException if tool is not in maintenance
     */
    @Transactional
    public Tool completeMaintenance(Long toolId) {
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + toolId));

        if (tool.getStatus() != ToolStatus.IN_MAINTENANCE) {
            throw new IllegalArgumentException(
                    "Tool is not in maintenance. Current status: " + tool.getStatus());
        }

        tool.setStatus(ToolStatus.AVAILABLE);
        return toolRepository.save(tool);
    }

    /**
     * Convert entity to DTO.
     */
    public MaintenanceLogDto toDto(MaintenanceLog log) {
        return new MaintenanceLogDto(
                log.getId(),
                log.getToolId(),
                log.getServiceDate(),
                log.getDescription(),
                log.getCost(),
                log.getNextServiceDue(),
                log.getCreatedAt(),
                log.getUpdatedAt());
    }
}
