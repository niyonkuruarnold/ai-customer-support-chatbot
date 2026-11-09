package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.CreateMaintenanceLogRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.MaintenanceLogDto;
import com.codafriqa.ai_customer_support_chatbot.dto.ToolDto;
import com.codafriqa.ai_customer_support_chatbot.model.ToolStatus;
import com.codafriqa.ai_customer_support_chatbot.service.MaintenanceLogService;
import com.codafriqa.ai_customer_support_chatbot.service.ToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for tool maintenance logging.
 *
 * - Add maintenance records for tools
 * - Complete maintenance and restore availability
 * - Query maintenance history and upcoming schedules
 */
@RestController
@RequestMapping({"/api/maintenance", "/api/v1/maintenance"})
@Tag(name = "Maintenance Logs", description = "Tool service records, maintenance scheduling, and availability control")
public class MaintenanceLogController {

    private final MaintenanceLogService maintenanceLogService;
    private final ToolService toolService;

    public MaintenanceLogController(MaintenanceLogService maintenanceLogService, ToolService toolService) {
        this.maintenanceLogService = maintenanceLogService;
        this.toolService = toolService;
    }

    @Operation(
            summary = "Add maintenance log",
            description = "Record a service entry for a tool. Automatically sets tool to IN_MAINTENANCE if AVAILABLE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Maintenance log created"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Tool not found"),
    })
    @PostMapping
    public ResponseEntity<MaintenanceLogDto> createMaintenanceLog(@RequestBody CreateMaintenanceLogRequest request) {
        MaintenanceLogDto dto = maintenanceLogService.toDto(maintenanceLogService.createMaintenanceLog(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(
            summary = "Get maintenance log by ID",
            description = "Retrieve a single maintenance log's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Log found"),
            @ApiResponse(responseCode = "404", description = "Log not found"),
    })
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceLogDto> getMaintenanceLog(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceLogService.toDto(maintenanceLogService.getMaintenanceLog(id)));
    }

    @Operation(
            summary = "Get maintenance logs for a tool",
            description = "List all maintenance records for a specific tool, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Log list returned"),
    })
    @GetMapping("/tool/{toolId}")
    public ResponseEntity<List<MaintenanceLogDto>> getLogsByTool(@PathVariable Long toolId) {
        List<MaintenanceLogDto> dtos = maintenanceLogService.getLogsByTool(toolId)
                .stream().map(maintenanceLogService::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get maintenance logs by date range",
            description = "List maintenance records for a tool within a specific date range.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Log list returned"),
    })
    @GetMapping("/tool/{toolId}/range")
    public ResponseEntity<List<MaintenanceLogDto>> getLogsByDateRange(
            @PathVariable Long toolId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<MaintenanceLogDto> dtos = maintenanceLogService.getLogsByToolAndDateRange(toolId, startDate, endDate)
                .stream().map(maintenanceLogService::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get upcoming maintenance",
            description = "Find all tools with maintenance due before a given date.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Upcoming maintenance list returned"),
    })
    @GetMapping("/upcoming")
    public ResponseEntity<List<MaintenanceLogDto>> getUpcomingMaintenance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beforeDate) {
        List<MaintenanceLogDto> dtos = maintenanceLogService.getUpcomingMaintenance(beforeDate)
                .stream().map(maintenanceLogService::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Complete maintenance",
            description = "Mark a tool's maintenance as complete and restore it to AVAILABLE status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tool restored to AVAILABLE"),
            @ApiResponse(responseCode = "400", description = "Tool is not in maintenance"),
            @ApiResponse(responseCode = "404", description = "Tool not found"),
    })
    @PostMapping("/tool/{toolId}/complete")
    public ResponseEntity<ToolDto> completeMaintenance(@PathVariable Long toolId) {
        return ResponseEntity.ok(toolService.toDto(maintenanceLogService.completeMaintenance(toolId)));
    }

    @Operation(
            summary = "Get maintenance stats for a tool",
            description = "Returns the total log count and last service date for a tool.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stats returned"),
    })
    @GetMapping("/tool/{toolId}/stats")
    public ResponseEntity<Map<String, Object>> getMaintenanceStats(@PathVariable Long toolId) {
        Long count = maintenanceLogService.getLogCountForTool(toolId);
        var lastLog = maintenanceLogService.getLastLogForTool(toolId);
        return ResponseEntity.ok(Map.of(
                "toolId", toolId,
                "logCount", count,
                "lastServiceDate", lastLog != null ? lastLog.getServiceDate() : null,
                "lastDescription", lastLog != null ? lastLog.getDescription() : null));
    }
}
