package com.codafriqa.ai_customer_support_chatbot.config;

import com.codafriqa.ai_customer_support_chatbot.model.AuditLog;
import com.codafriqa.ai_customer_support_chatbot.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * AOP aspect that intercepts methods annotated with {@link AuditAction}
 * and writes an immutable audit log entry when the method completes.
 *
 * Actor information is resolved from the current SecurityContext.
 * If no authentication is present, "SYSTEM" is used as the actor.
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Around("@annotation(com.codafriqa.ai_customer_support_chatbot.config.AuditAction)")
    public Object auditMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            writeAuditLog(joinPoint);
        } catch (Exception e) {
            // Never let audit logging break the business flow
            log.error("Failed to write audit log: {}", e.getMessage());
        }

        return result;
    }

    private void writeAuditLog(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditAction annotation = method.getAnnotation(AuditAction.class);
        if (annotation == null) return;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actorEmail = resolveActorEmail(auth);
        String actorRole = resolveActorRole(auth);
        String ipAddress = resolveIpAddress();

        AuditLog auditLog = new AuditLog(actorEmail, annotation.action(),
                defaultDescription(annotation, method));
        auditLog.setActorRole(actorRole);
        auditLog.setIpAddress(ipAddress);

        if (!annotation.resourceType().isEmpty()) {
            auditLog.setResourceType(annotation.resourceType());
        }

        auditLogRepository.save(auditLog);
    }

    private String resolveActorEmail(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }

    private String resolveActorRole(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return "SYSTEM";
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("UNKNOWN");
    }

    private String resolveIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String defaultDescription(AuditAction annotation, Method method) {
        if (!annotation.description().isEmpty()) {
            return annotation.description();
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
