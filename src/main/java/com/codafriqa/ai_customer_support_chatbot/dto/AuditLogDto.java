package com.codafriqa.ai_customer_support_chatbot.dto;

import com.codafriqa.ai_customer_support_chatbot.model.AuditLog;
import java.time.LocalDateTime;

/**
 * DTO for audit log API responses.
 * Avoids exposing internal entity fields and provides a clean API contract.
 */
public record AuditLogDto(
    Long id,
    Long actorId,
    String actorEmail,
    String actorRole,
    String actionType,
    String description,
    String ipAddress,
    String resourceType,
    Long resourceId,
    String metadata,
    boolean success,
    LocalDateTime timestamp
) {
    /**
     * Map from entity to DTO.
     */
    public static AuditLogDto fromEntity(AuditLog entity) {
        return new AuditLogDto(
            entity.getId(),
            entity.getActorId(),
            entity.getActorEmail(),
            entity.getActorRole(),
            entity.getActionType(),
            entity.getDescription(),
            entity.getIpAddress(),
            entity.getResourceType(),
            entity.getResourceId(),
            entity.getMetadata(),
            entity.isSuccess(),
            entity.getTimestamp()
        );
    }
}
