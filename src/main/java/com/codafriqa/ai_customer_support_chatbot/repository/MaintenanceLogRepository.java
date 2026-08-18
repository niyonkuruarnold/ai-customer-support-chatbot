package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.MaintenanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, Long> {

    /** Get all maintenance logs for a specific tool, newest first. */
    List<MaintenanceLog> findByToolIdOrderByServiceDateDesc(Long toolId);

    /** Get maintenance logs for a tool within a date range. */
    List<MaintenanceLog> findByToolIdAndServiceDateBetweenOrderByServiceDateDesc(
            Long toolId, LocalDate startDate, LocalDate endDate);

    /** Find tools with upcoming maintenance due before a given date. */
    @Query("SELECT ml FROM MaintenanceLog ml WHERE ml.nextServiceDue <= :date AND ml.nextServiceDue IS NOT NULL ORDER BY ml.nextServiceDue ASC")
    List<MaintenanceLog> findUpcomingMaintenance(@Param("date") LocalDate date);

    /** Get the most recent maintenance log for a tool. */
    MaintenanceLog findFirstByToolIdOrderByServiceDateDesc(Long toolId);

    /** Count maintenance logs for a tool. */
    Long countByToolId(Long toolId);
}
