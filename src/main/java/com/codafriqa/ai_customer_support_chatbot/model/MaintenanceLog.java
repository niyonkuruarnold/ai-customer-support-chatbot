package com.codafriqa.ai_customer_support_chatbot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maintenance log entry for tool service records.
 * Tracks service history, costs, and upcoming maintenance schedules.
 */
@Entity
@Table(name = "maintenance_logs")
public class MaintenanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotNull(message = "Tool ID is required")
    private Long toolId;

    @Column(nullable = false)
    @NotNull(message = "Service date is required")
    private LocalDate serviceDate;

    @Column(columnDefinition = "TEXT", nullable = false)
    @NotNull(message = "Description is required")
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    private LocalDate nextServiceDue;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public MaintenanceLog() {
    }

    public MaintenanceLog(Long toolId, LocalDate serviceDate, String description) {
        this.toolId = toolId;
        this.serviceDate = serviceDate;
        this.description = description;
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }

    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public LocalDate getNextServiceDue() { return nextServiceDue; }
    public void setNextServiceDue(LocalDate nextServiceDue) { this.nextServiceDue = nextServiceDue; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
