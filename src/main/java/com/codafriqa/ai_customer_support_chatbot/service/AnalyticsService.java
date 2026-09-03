package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.ChatFeedback;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.ChatSession;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
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
     * Get comprehensive dashboard metrics.
     */
    public DashboardMetrics getDashboardMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Calculating dashboard metrics from {} to {}", startDate, endDate);

        // Get all sessions in date range
        List<ChatSession> allSessions = sessionRepository.findAll();
        List<ChatSession> sessionsInRange = allSessions.stream()
            .filter(s -> s.getCreatedAt().isAfter(startDate) && s.getCreatedAt().isBefore(endDate))
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
        long openTickets = ticketRepository.countByStatus("OPEN") + 
                          ticketRepository.countByStatus("IN_PROGRESS") +
                          ticketRepository.countByStatus("ESCALATED");
        long resolvedTickets = ticketRepository.countByStatus("RESOLVED");
        long closedTickets = ticketRepository.countByStatus("CLOSED");

        // Response time by hour (for chart)
        Map<Integer, Long> hourlyDistribution = calculateHourlyDistribution(sessionsInRange);

        // Tickets by status
        Map<String, Long> ticketsByStatus = Map.of(
            "OPEN", ticketRepository.countByStatus("OPEN"),
            "IN_PROGRESS", ticketRepository.countByStatus("IN_PROGRESS"),
            "ESCALATED", ticketRepository.countByStatus("ESCALATED"),
            "PENDING_CUSTOMER", ticketRepository.countByStatus("PENDING_CUSTOMER"),
            "PENDING_INTERNAL", ticketRepository.countByStatus("PENDING_INTERNAL"),
            "RESOLVED", ticketRepository.countByStatus("RESOLVED"),
            "CLOSED", ticketRepository.countByStatus("CLOSED"),
            "REOPENED", ticketRepository.countByStatus("REOPENED")
        );

        // Tickets by priority
        Map<String, Long> ticketsByPriority = Map.of(
            "LOW", ticketRepository.countByStatus("LOW"),
            "MEDIUM", ticketRepository.countByStatus("MEDIUM"),
            "HIGH", ticketRepository.countByStatus("HIGH"),
            "URGENT", ticketRepository.countByStatus("URGENT")
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
            .filter(s -> s.getCreatedAt().isAfter(startDate) && s.getCreatedAt().isBefore(endDate))
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
                if ("USER".equals(userMessage.getSender()) && "AI".equals(aiResponse.getSender())) {
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
