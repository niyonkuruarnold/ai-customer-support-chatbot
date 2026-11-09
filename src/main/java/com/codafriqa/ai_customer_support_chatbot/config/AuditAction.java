package com.codafriqa.ai_customer_support_chatbot.config;

import java.lang.annotation.*;

/**
 * Declarative audit logging annotation.
 * Place on any Spring-managed method to automatically write an immutable
 * audit log entry when the method completes successfully.
 *
 * Usage:
 *   @AuditAction(action = "DATA_EXPORT", resourceType = "TICKETS", description = "CSV export")
 *   public byte[] exportTicketsToCsv(...) { ... }
 *
 * The aspect resolves actor information from the current SecurityContext.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditAction {

    /** Action type recorded in the audit log (e.g., DATA_EXPORT, ROLE_UPDATE). */
    String action();

    /** Target resource type (e.g., TICKETS, USERS, DOCUMENT). */
    String resourceType() default "";

    /** Human-readable description. If empty, a default is generated. */
    String description() default "";

    /** Optional SpEL expression to extract the resource ID from method arguments. */
    String resourceIdExpression() default "";
}
