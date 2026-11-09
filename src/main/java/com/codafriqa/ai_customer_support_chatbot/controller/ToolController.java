package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.CreateToolRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.ToolDto;
import com.codafriqa.ai_customer_support_chatbot.dto.UpdateToolStatusRequest;
import com.codafriqa.ai_customer_support_chatbot.model.ToolStatus;
import com.codafriqa.ai_customer_support_chatbot.service.ToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for tool management.
 *
 * - Create and manage tools
 * - Update tool availability status
 * - Query tools by owner or status
 */
@RestController
@RequestMapping({"/api/tools", "/api/v1/tools"})
@Tag(name = "Tool Management", description = "Create tools, manage availability status, and query by owner/status")
public class ToolController {

    private final ToolService toolService;

    public ToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    @Operation(
            summary = "Create a new tool",
            description = "Register a new tool in the system with initial AVAILABLE status.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tool created"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    @PostMapping
    public ResponseEntity<ToolDto> createTool(@RequestBody CreateToolRequest request) {
        ToolDto dto = toolService.toDto(toolService.createTool(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(
            summary = "List all tools",
            description = "Return every tool in the system. Used by the System Indexer dashboard.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tool list returned"),
    })
    @GetMapping
    public ResponseEntity<List<ToolDto>> listAllTools() {
        List<ToolDto> dtos = toolService.getAllTools()
                .stream().map(toolService::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get tool by ID",
            description = "Retrieve a single tool's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tool found"),
            @ApiResponse(responseCode = "404", description = "Tool not found"),
    })
    @GetMapping("/{id}")
    public ResponseEntity<ToolDto> getTool(@PathVariable Long id) {
        return ResponseEntity.ok(toolService.toDto(toolService.getTool(id)));
    }

    @Operation(
            summary = "Get tools by owner",
            description = "List all tools owned by a specific user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tool list returned"),
    })
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<ToolDto>> getToolsByOwner(@PathVariable Long ownerId) {
        List<ToolDto> dtos = toolService.getToolsByOwner(ownerId)
                .stream().map(toolService::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get tools by status",
            description = "List all tools with a specific availability status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tool list returned"),
    })
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ToolDto>> getToolsByStatus(@PathVariable ToolStatus status) {
        List<ToolDto> dtos = toolService.getToolsByStatus(status)
                .stream().map(toolService::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Update tool status",
            description = "Change a tool's availability status (AVAILABLE, BORROWED, IN_MAINTENANCE).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @ApiResponse(responseCode = "404", description = "Tool not found"),
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ToolDto> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateToolStatusRequest request) {
        return ResponseEntity.ok(toolService.toDto(toolService.updateStatus(id, request)));
    }
}
