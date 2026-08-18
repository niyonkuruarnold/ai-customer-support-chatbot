package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.model.SystemScanLog;
import com.codafriqa.ai_customer_support_chatbot.service.SystemScannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * System-level endpoints for automated scanner status and diagnostics.
 *
 * <p>Exposes the sync-status endpoint that the frontend polls to show
 * the last automated knowledge-base scan result.
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Automated system scanner status and diagnostics")
public class SystemController {

    private final SystemScannerService systemScannerService;

    public SystemController(SystemScannerService systemScannerService) {
        this.systemScannerService = systemScannerService;
    }

    /**
     * System sync status: returns the timestamp and record count of the
     * latest automated scan.
     */
    @Operation(
            summary = "Get system scan sync status",
            description = "Returns the timestamp, record count, and status of the latest automated "
                    + "system scan that indexes knowledge documents into the vector store.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sync status returned")
    })
    @GetMapping("/sync-status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        SystemScanLog latest = systemScannerService.getLatestScan();

        if (latest == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "NO_SCANS",
                    "message", "No system scans have been executed yet",
                    "timestamp", LocalDateTime.now().toString()));
        }

        return ResponseEntity.ok(Map.of(
                "status", latest.getStatus().name(),
                "timestamp", latest.getScannedAt().toString(),
                "recordsScanned", latest.getRecordsScanned(),
                "recordsIndexed", latest.getRecordsIndexed(),
                "chunksCreated", latest.getChunksCreated(),
                "durationMs", latest.getDurationMs() != null ? latest.getDurationMs() : 0,
                "message", latest.getMessage() != null ? latest.getMessage() : ""));
    }
}
