package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.SystemScanLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemScanLogRepository extends JpaRepository<SystemScanLog, Long> {

    /**
     * Find the most recent completed or failed scan (i.e. the latest finished run).
     * Ordered by scannedAt descending, limited to 1.
     */
    @Query("SELECT s FROM SystemScanLog s WHERE s.status IN ('COMPLETED', 'FAILED') ORDER BY s.scannedAt DESC LIMIT 1")
    Optional<SystemScanLog> findLatestFinishedScan();

    /**
     * Find the most recent scan with COMPLETED status.
     */
    Optional<SystemScanLog> findFirstByStatusOrderByScannedAtDesc(SystemScanLog.ScanStatus status);

    /**
     * Count total completed scans.
     */
    long countByStatus(SystemScanLog.ScanStatus status);
}
