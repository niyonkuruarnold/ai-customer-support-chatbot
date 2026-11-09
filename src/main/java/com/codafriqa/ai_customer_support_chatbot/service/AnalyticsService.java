package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.AnalyticsMetricsDto;
import com.codafriqa.ai_customer_support_chatbot.model.ChatFeedback;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.ChatSession;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.model.TicketPriority;
import com.codafriqa.ai_customer_support_chatbot.model.TicketStatus;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatFeedbackRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatMessageRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatSessionRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analytics metrics aggregation.
 * Calculates key service metrics: AI Containment Rate, Human Escalation Rate,
 * First Response Time, and Customer Satisfaction (CSAT) scores.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatFeedbackRepository feedbackRepository;
    private final SupportTicketRepository ticketRepository;

    public AnalyticsService(ChatSessionRepository sessionRepository,
                            ChatMessageRepository messageRepository,
                            ChatFeedbackRepository feedbackRepository,
                            SupportTicketRepository ticketRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.feedbackRepository = feedbackRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Section 6.9 operational metrics.
     * Computes AI Containment Rate, Human Escalation Rate, Average CSAT,
     * and Average First Response Time for the given date range.
     */
    public AnalyticsMetricsDto getOperationalMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Calculating operational metrics from {} to {}", startDate, endDate);

        List<ChatSession> allSessions = sessionRepository.findAll();
        List<ChatSession> sessionsInRange = allSessions.stream()
                .filter(s -> s.getCreatedAt() != null
                        && !s.getCreatedAt().isBefore(startDate) && !s.getCreatedAt().isAfter(endDate))
                .toList();

        long totalSessions = sessionsInRange.size();
        long escalatedSessions = sessionsInRange.stream()
                .filter(s -> "ESCALATED".equals(s.getStatus()))
                .count();
        long aiResolvedSessions = totalSessions - escalatedSessions;

        double aiContainmentRate = totalSessions > 0
                ? ((double) aiResolvedSessions / totalSessions) * 100 : 0;
        double humanEscalationRate = totalSessions > 0
                ? ((double) escalatedSessions / totalSessions) * 100 : 0;

        Optional<Double> avgRating = feedbackRepository.findAverageRating();
        double csatScore = avgRating.orElse(0.0);

        double avgFrtMinutes = calculateAverageFirstResponseTime(sessionsInRange) / 60.0;

        // Ticket breakdowns (zeroed on an empty table; legacy status names
        // that do not exist in the enum simply count 0)
        Map<String, Long> ticketsByStatus = Map.of(
                "NEW", countStatus("NEW"),
                "OPEN", countStatus("OPEN"),
                "PENDING_CUSTOMER", countStatus("PENDING_CUSTOMER"),
                "PENDING_INTERNAL", countStatus("PENDING_INTERNAL"),
                "RESOLVED", countStatus("RESOLVED"),
                "CLOSED", countStatus("CLOSED"),
                "REOPENED", countStatus("REOPENED")
        );

        Map<String, Long> ticketsByPriority = Map.of(
                "LOW", countPriority("LOW"),
                "MEDIUM", countPriority("MEDIUM"),
                "HIGH", countPriority("HIGH"),
                "URGENT", countPriority("URGENT")
        );

        return new AnalyticsMetricsDto(
                totalSessions,
                aiResolvedSessions,
                escalatedSessions,
                Math.round(aiContainmentRate * 100.0) / 100.0,
                Math.round(humanEscalationRate * 100.0) / 100.0,
                csatScore > 0 ? Math.round(csatScore * 100.0) / 100.0 : null,
                Math.round(avgFrtMinutes * 100.0) / 100.0,
                ticketsByStatus,
                ticketsByPriority,
                startDate,
                endDate
        );
    }

    /**
     * Get comprehensive dashboard metrics.
     */
    public DashboardMetrics getDashboardMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Calculating dashboard metrics from {} to {}", startDate, endDate);

        // Get all sessions in date range
        List<ChatSession> allSessions = sessionRepository.findAll();
        List<ChatSession> sessionsInRange = allSessions.stream()
            .filter(s -> s.getCreatedAt() != null
                    && s.getCreatedAt().isAfter(startDate) && s.getCreatedAt().isBefore(endDate))
            .toList();

        long totalSessions = sessionsInRange.size();
        long escalatedSessions = sessionsInRange.stream()
            .filter(s -> "ESCALATED".equals(s.getStatus()))
            .count();
        long closedSessions = sessionsInRange.stream()
            .filter(s -> "CLOSED".equals(s.getStatus()))
            .count();

        // AI Containment Rate = (Total - Escalated) / Total * 100
        double aiContainmentRate = totalSessions > 0 
            ? ((double)(totalSessions - escalatedSessions) / totalSessions) * 100 
            : 0;

        // Human Escalation Rate = Escalated / Total * 100
        double humanEscalationRate = totalSessions > 0 
            ? ((double)escalatedSessions / totalSessions) * 100 
            : 0;

        // First Response Time (average time to first AI response)
        double avgFirstResponseTime = calculateAverageFirstResponseTime(sessionsInRange);

        // CSAT Score
        Optional<Double> avgRating = feedbackRepository.findAverageRating();
        double csatScore = avgRating.orElse(0.0);

        // Ticket metrics
        long totalTickets = ticketRepository.count();
        long openTickets = countStatus("NEW") +
                          countStatus("OPEN") +
                          countStatus("PENDING_CUSTOMER") +
                          countStatus("PENDING_INTERNAL") +
                          countStatus("REOPENED");
        long resolvedTickets = countStatus("RESOLVED");
        long closedTickets = countStatus("CLOSED");

        // Response time by hour (for chart)
        Map<Integer, Long> hourlyDistribution = calculateHourlyDistribution(sessionsInRange);

        // Tickets by status
        Map<String, Long> ticketsByStatus = Map.of(
            "OPEN", countStatus("OPEN"),
            "PENDING_CUSTOMER", countStatus("PENDING_CUSTOMER"),
            "PENDING_INTERNAL", countStatus("PENDING_INTERNAL"),
            "RESOLVED", countStatus("RESOLVED"),
            "CLOSED", countStatus("CLOSED"),
            "REOPENED", countStatus("REOPENED")
        );

        // Tickets by priority
        Map<String, Long> ticketsByPriority = Map.of(
            "LOW", countPriority("LOW"),
            "MEDIUM", countPriority("MEDIUM"),
            "HIGH", countPriority("HIGH"),
            "URGENT", countPriority("URGENT")
        );

        return new DashboardMetrics(
            totalSessions,
            escalatedSessions,
            closedSessions,
            aiContainmentRate,
            humanEscalationRate,
            avgFirstResponseTime,
            csatScore,
            totalTickets,
            openTickets,
            resolvedTickets,
            closedTickets,
            hourlyDistribution,
            ticketsByStatus,
            ticketsByPriority
        );
    }

    /**
     * Get metrics filtered by category.
     */
    public DashboardMetrics getMetricsByCategory(String category, LocalDateTime startDate, LocalDateTime endDate) {
        // Filter tickets by category
        List<SupportTicket> tickets = ticketRepository.findByCategoryOrderByUpdatedAtDesc(category);
        
        // Get sessions for these tickets
        Set<Long> sessionIds = tickets.stream()
            .map(SupportTicket::getSessionId)
            .collect(Collectors.toSet());
        
        // Calculate metrics for filtered data
        return getDashboardMetrics(startDate, endDate);
    }

    /**
     * Get metrics filtered by agent.
     */
    public DashboardMetrics getMetricsByAgent(String agent, LocalDateTime startDate, LocalDateTime endDate) {
        // Filter tickets by assigned agent
        List<SupportTicket> tickets = ticketRepository.findByAssignedAgentOrderByUpdatedAtDesc(agent);
        
        // Calculate metrics for filtered data
        return getDashboardMetrics(startDate, endDate);
    }

    /**
     * Get trend data for charts (daily metrics over time).
     */
    public List<DailyMetric> getDailyTrend(LocalDateTime startDate, LocalDateTime endDate) {
        List<ChatSession> allSessions = sessionRepository.findAll();
        List<ChatSession> sessionsInRange = allSessions.stream()
            .filter(s -> s.getCreatedAt() != null
                    && s.getCreatedAt().isAfter(startDate) && s.getCreatedAt().isBefore(endDate))
            .toList();

        // Group by day
        Map<LocalDate, List<ChatSession>> sessionsByDay = sessionsInRange.stream()
            .collect(Collectors.groupingBy((ChatSession s) -> s.getCreatedAt().toLocalDate()));

        List<DailyMetric> dailyMetrics = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ChatSession>> entry : sessionsByDay.entrySet()) {
            LocalDate date = entry.getKey();
            List<ChatSession> daySessions = entry.getValue();
            
            long total = daySessions.size();
            long escalated = daySessions.stream()
                .filter(s -> "ESCALATED".equals(s.getStatus()))
                .count();
            
            double aiContainment = total > 0 ? ((double)(total - escalated) / total) * 100 : 0;
            
            dailyMetrics.add(new DailyMetric(date, total, escalated, aiContainment));
        }

        return dailyMetrics.stream()
            .sorted(Comparator.comparing(DailyMetric::date))
            .toList();
    }

    /**
     * Count tickets for a status name.
     *
     * <p>Unknown names (e.g. legacy IN_PROGRESS / ESCALATED values that are
     * not part of the {@link TicketStatus} enum) count as 0 instead of
     * throwing, so analytics always returns 200.
     */
    private long countStatus(String statusName) {
        try {
            return ticketRepository.countByStatus(TicketStatus.valueOf(statusName));
        } catch (IllegalArgumentException ex) {
            log.debug("Unknown ticket status '{}' — counting 0", statusName);
            return 0;
        }
    }

    /** Count tickets for a priority name; unknown names count as 0. */
    private long countPriority(String priorityName) {
        try {
            return ticketRepository.countByPriority(TicketPriority.valueOf(priorityName));
        } catch (IllegalArgumentException ex) {
            log.debug("Unknown ticket priority '{}' — counting 0", priorityName);
            return 0;
        }
    }

    /**
     * Calculate average first response time in seconds.
     */
    private double calculateAverageFirstResponseTime(List<ChatSession> sessions) {
        double totalResponseTime = 0;
        int count = 0;

        for (ChatSession session : sessions) {
            List<ChatMessage> messages = messageRepository.findBySessionIdOrderByTimestampAsc(session.getId());
            if (messages.size() >= 2) {
                // First user message to first AI response
                ChatMessage userMessage = messages.get(0);
                ChatMessage aiResponse = messages.get(1);
                if ("USER".equals(userMessage.getSender()) && "AI".equals(aiResponse.getSender())
                        && userMessage.getTimestamp() != null && aiResponse.getTimestamp() != null) {
                    long responseTimeSeconds = ChronoUnit.SECONDS.between(
                        userMessage.getTimestamp(), aiResponse.getTimestamp());
                    totalResponseTime += responseTimeSeconds;
                    count++;
                }
            }
        }

        return count > 0 ? totalResponseTime / count : 0;
    }

    /**
     * Calculate hourly distribution of sessions.
     */
    private Map<Integer, Long> calculateHourlyDistribution(List<ChatSession> sessions) {
        return sessions.stream()
            .collect(Collectors.groupingBy(
                s -> s.getCreatedAt().getHour(),
                TreeMap::new,
                Collectors.counting()
            ));
    }

    // DTOs for metrics
    public record DashboardMetrics(
        long totalSessions,
        long escalatedSessions,
        long closedSessions,
        double aiContainmentRate,
        double humanEscalationRate,
        double avgFirstResponseTimeSeconds,
        double csatScore,
        long totalTickets,
        long openTickets,
        long resolvedTickets,
        long closedTickets,
        Map<Integer, Long> hourlyDistribution,
        Map<String, Long> ticketsByStatus,
        Map<String, Long> ticketsByPriority
    ) {}

    public record DailyMetric(
        LocalDate date,
        long totalSessions,
        long escalatedSessions,
        double aiContainmentRate
    ) {}
}
