package com.codafriqa.ai_customer_support_chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks each automated system scan that indexes knowledge documents
 * into the pgvector store. One row per scan run, recording the timestamp,
 * status, and how many records were processed.
 */
@Entity
@Table(name = "system_scan_logs")
public class SystemScanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status;

    /** Number of documents (records) scanned during this run. */
    @Column(nullable = false)
    private Long recordsScanned;

    /** Number of new/updated documents that were actually ingested. */
    @Column(nullable = false)
    private Long recordsIndexed;

    /** Total number of chunks produced during this run. */
    @Column(nullable = false)
    private Long chunksCreated;

    /** Human-readable message (e.g. error details on failure). */
    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, updatable = false)
    private LocalDateTime scannedAt;

    /** Duration of the scan in milliseconds. */
    private Long durationMs;

    public SystemScanLog() {
    }

    @PrePersist
    public void onCreate() {
        this.scannedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ScanStatus getStatus() { return status; }
    public void setStatus(ScanStatus status) { this.status = status; }

    public Long getRecordsScanned() { return recordsScanned; }
    public void setRecordsScanned(Long recordsScanned) { this.recordsScanned = recordsScanned; }

    public Long getRecordsIndexed() { return recordsIndexed; }
    public void setRecordsIndexed(Long recordsIndexed) { this.recordsIndexed = recordsIndexed; }

    public Long getChunksCreated() { return chunksCreated; }
    public void setChunksCreated(Long chunksCreated) { this.chunksCreated = chunksCreated; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    /** Scan run status. */
    public enum ScanStatus {
        RUNNING,
        COMPLETED,
        FAILED
    }
}
