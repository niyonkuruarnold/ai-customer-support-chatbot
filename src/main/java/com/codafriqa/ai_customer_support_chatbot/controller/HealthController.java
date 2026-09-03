package com.codafriqa.ai_customer_support_chatbot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dedicated health check controller for monitoring system health.
 *
 * <p>Provides detailed status of the application including database
 * connectivity, uptime, and version information. Used by Docker
 * health checks and monitoring systems.</p>
 */
@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@Tag(name = "Health", description = "System health check endpoints")
public class HealthController {

    private static final Instant STARTED_AT = Instant.now();

    @Autowired
    private DataSource dataSource;

    /**
     * Basic health check — returns 200 OK if the app is running.
     * Used by Docker health checks.
     */
    @Operation(
        summary = "Basic health check",
        description = "Returns a simple OK status confirming the application is running."
    )
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        response.put("uptime", STARTED_AT.toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Detailed health check — includes database connectivity and system info.
     */
    @Operation(
        summary = "Detailed health check",
        description = "Returns comprehensive system health including database connectivity, "
                + "server uptime, and version information."
    )
    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> components = new LinkedHashMap<>();

        // Application status
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        response.put("application", "AI Customer Support Chatbot");
        response.put("version", "1.0.0");

        // Database check
        components.put("database", checkDatabase());

        // Memory check
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("totalMB", runtime.totalMemory() / (1024 * 1024));
        memory.put("freeMB", runtime.freeMemory() / (1024 * 1024));
        memory.put("maxMB", runtime.maxMemory() / (1024 * 1024));
        memory.put("usedMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        components.put("memory", memory);

        // Java version
        components.put("javaVersion", System.getProperty("java.version"));

        response.put("components", components);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> dbStatus = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            dbStatus.put("status", "UP");
            dbStatus.put("databaseProductName", meta.getDatabaseProductName());
            dbStatus.put("databaseVersion", meta.getDatabaseProductVersion());
            dbStatus.put("driverName", meta.getDriverName());
            dbStatus.put("url", meta.getURL());
        } catch (Exception e) {
            dbStatus.put("status", "DOWN");
            dbStatus.put("error", e.getMessage());
        }
        return dbStatus;
    }
}
