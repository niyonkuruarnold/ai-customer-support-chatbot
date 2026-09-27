package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.AuditLogDto;
import com.codafriqa.ai_customer_support_chatbot.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Plain-list alias for audit log clients.
 *
 * <p>GET /api/audit-logs (and the /api/v1/audit-logs alias) returns a bare
 * JSON array — {@code []} with HTTP 200 when no audit logs exist — instead of
 * the paginated envelope used by /api/audit. The paginated and filtered
 * variants stay on {@link AuditLogController}; this alias covers clients that
 * expect a plain list and previously received 404.
 */
@RestController
@RequestMapping({"/api/audit-logs", "/api/v1/audit-logs"})
@Tag(name = "Audit Logs", description = "Plain-list audit log access")
public class AuditLogAliasController {

    private final AuditLogService auditLogService;

    public AuditLogAliasController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "List audit logs (plain array)",
               description = "Returns a JSON array of audit logs; an empty array with HTTP 200 when none exist.")
    @GetMapping
    public ResponseEntity<List<AuditLogDto>> listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 1000);

        List<AuditLogDto> logs = auditLogService
                .getAllLogDtos(PageRequest.of(safePage, safeSize))
                .getContent();
        return ResponseEntity.ok(logs);
    }
}
