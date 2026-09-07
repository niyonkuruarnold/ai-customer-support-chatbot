package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.AnalyticsMetricsDto;
import com.codafriqa.ai_customer_support_chatbot.model.AuditLog;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.repository.AuditLogRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SupportTicketRepository ticketRepository;
    private final AuditLogRepository auditLogRepository;

    public ExportService(SupportTicketRepository ticketRepository, AuditLogRepository auditLogRepository) {
        this.ticketRepository = ticketRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public String exportTicketsToCsv(List<SupportTicket> tickets) {
        StringWriter writer = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeNext(new String[]{
                "ID", "Reference", "Subject", "Description", "Status", "Priority",
                "Category", "Assigned Agent", "Sentiment", "Created At", "Updated At"
            });
            for (SupportTicket ticket : tickets) {
                csvWriter.writeNext(new String[]{
                    String.valueOf(ticket.getId()),
                    ticket.getTicketReference(),
                    ticket.getSubject(),
                    ticket.getDescription(),
                    ticket.getStatus() != null ? ticket.getStatus().name() : "",
                    ticket.getPriority() != null ? ticket.getPriority().name() : "",
                    ticket.getCategory(),
                    ticket.getAssignedAgent() != null ? ticket.getAssignedAgent() : "",
                    ticket.getSentiment() != null ? ticket.getSentiment() : "",
                    ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DATE_FORMAT) : "",
                    ticket.getUpdatedAt() != null ? ticket.getUpdatedAt().format(DATE_FORMAT) : ""
                });
            }
        } catch (Exception e) {
            log.error("Failed to export tickets to CSV: {}", e.getMessage());
            throw new RuntimeException("CSV export failed", e);
        }
        return writer.toString();
    }

    public String exportAuditLogsToCsv(List<AuditLog> logs) {
        StringWriter writer = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeNext(new String[]{
                "ID", "Actor Email", "Action Type", "Description", "IP Address",
                "Resource Type", "Resource ID", "Success", "Timestamp"
            });
            for (AuditLog auditLog : logs) {
                csvWriter.writeNext(new String[]{
                    String.valueOf(auditLog.getId()),
                    auditLog.getActorEmail(),
                    auditLog.getActionType(),
                    auditLog.getDescription(),
                    auditLog.getIpAddress() != null ? auditLog.getIpAddress() : "",
                    auditLog.getResourceType() != null ? auditLog.getResourceType() : "",
                    auditLog.getResourceId() != null ? String.valueOf(auditLog.getResourceId()) : "",
                    String.valueOf(auditLog.isSuccess()),
                    auditLog.getTimestamp() != null ? auditLog.getTimestamp().format(DATE_FORMAT) : ""
                });
            }
        } catch (Exception e) {
            log.error("Failed to export audit logs to CSV: {}", e.getMessage());
            throw new RuntimeException("CSV export failed", e);
        }
        return writer.toString();
    }

    public byte[] exportTicketsToPdf(List<SupportTicket> tickets) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);
            document.add(new Paragraph("Support Tickets Report").setFontSize(18).setBold().setFontColor(ColorConstants.DARK_GRAY).setMarginBottom(20));
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DATE_FORMAT)).setFontSize(10).setFontColor(ColorConstants.GRAY).setMarginBottom(20));
            float[] columnWidths = {1, 2, 3, 2, 1.5f, 1.5f, 2};
            Table table = new Table(UnitValue.createPercentArray(columnWidths)).useAllAvailableWidth().setHorizontalAlignment(HorizontalAlignment.CENTER);
            DeviceRgb headerBg = new DeviceRgb(79, 70, 229);
            String[] headers = {"ID", "Reference", "Subject", "Status", "Priority", "Category", "Agent"};
            for (String header : headers) {
                Cell cell = new Cell().add(new Paragraph(header).setFontSize(9).setBold()).setBackgroundColor(headerBg).setFontColor(ColorConstants.WHITE).setPadding(5);
                table.addHeaderCell(cell);
            }
            for (SupportTicket ticket : tickets) {
                table.addCell(createCell(String.valueOf(ticket.getId())));
                table.addCell(createCell(ticket.getTicketReference()));
                table.addCell(createCell(truncate(ticket.getSubject(), 40)));
                table.addCell(createCell(ticket.getStatus() != null ? ticket.getStatus().name() : ""));
                table.addCell(createCell(ticket.getPriority() != null ? ticket.getPriority().name() : ""));
                table.addCell(createCell(ticket.getCategory()));
                table.addCell(createCell(ticket.getAssignedAgent() != null ? ticket.getAssignedAgent() : "-"));
            }
            document.add(table);
            document.close();
        } catch (Exception e) {
            log.error("Failed to export tickets to PDF: {}", e.getMessage());
            throw new RuntimeException("PDF export failed", e);
        }
        return baos.toByteArray();
    }

    public byte[] exportAuditLogsToPdf(List<AuditLog> logs) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);
            document.add(new Paragraph("Audit Log Report").setFontSize(18).setBold().setFontColor(ColorConstants.DARK_GRAY).setMarginBottom(20));
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DATE_FORMAT)).setFontSize(10).setFontColor(ColorConstants.GRAY).setMarginBottom(20));
            float[] columnWidths = {1, 2, 2, 3, 1.5f, 2};
            Table table = new Table(UnitValue.createPercentArray(columnWidths)).useAllAvailableWidth().setHorizontalAlignment(HorizontalAlignment.CENTER);
            DeviceRgb headerBg = new DeviceRgb(79, 70, 229);
            String[] headers = {"ID", "Actor", "Action", "Description", "Success", "Timestamp"};
            for (String header : headers) {
                Cell cell = new Cell().add(new Paragraph(header).setFontSize(9).setBold()).setBackgroundColor(headerBg).setFontColor(ColorConstants.WHITE).setPadding(5);
                table.addHeaderCell(cell);
            }
            for (AuditLog auditLog : logs) {
                table.addCell(createCell(String.valueOf(auditLog.getId())));
                table.addCell(createCell(auditLog.getActorEmail()));
                table.addCell(createCell(auditLog.getActionType()));
                table.addCell(createCell(truncate(auditLog.getDescription(), 50)));
                table.addCell(createCell(auditLog.isSuccess() ? "Y" : "N"));
                table.addCell(createCell(auditLog.getTimestamp() != null ? auditLog.getTimestamp().format(DATE_FORMAT) : ""));
            }
            document.add(table);
            document.close();
        } catch (Exception e) {
            log.error("Failed to export audit logs to PDF: {}", e.getMessage());
            throw new RuntimeException("PDF export failed", e);
        }
        return baos.toByteArray();
    }

    // ─── Analytics Exports (Sections 6.9 & 6.10) ─────────────────────

    /**
     * Export analytics performance summary as a styled PDF report.
     */
    public byte[] exportAnalyticsToPdf(AnalyticsMetricsDto metrics) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            DeviceRgb headerBg = new DeviceRgb(79, 70, 229);

            // Title
            document.add(new Paragraph("Analytics Performance Report")
                    .setFontSize(20).setBold()
                    .setFontColor(ColorConstants.DARK_GRAY)
                    .setMarginBottom(5));
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DATE_FORMAT))
                    .setFontSize(10).setFontColor(ColorConstants.GRAY).setMarginBottom(5));
            document.add(new Paragraph("Period: " + metrics.startDate().format(DATE_FORMAT)
                    + " — " + metrics.endDate().format(DATE_FORMAT))
                    .setFontSize(10).setFontColor(ColorConstants.GRAY).setMarginBottom(20));

            // Key Metrics Table
            document.add(new Paragraph("Key Metrics")
                    .setFontSize(14).setBold().setMarginBottom(10));

            float[] colWidths = {4f, 2f};
            Table metricsTable = new Table(UnitValue.createPercentArray(colWidths)).useAllAvailableWidth();

            String[][] rows = {
                    {"Total Conversations", String.valueOf(metrics.totalConversations())},
                    {"AI Resolved Conversations", String.valueOf(metrics.aiResolvedConversations())},
                    {"Escalated Conversations", String.valueOf(metrics.escalatedConversations())},
                    {"AI Containment Rate", String.format("%.2f%%", metrics.aiContainmentRate())},
                    {"Human Escalation Rate", String.format("%.2f%%", metrics.humanEscalationRate())},
                    {"Average CSAT Rating", metrics.averageCsatRating() != null
                            ? String.format("%.2f / 5.0", metrics.averageCsatRating()) : "N/A"},
                    {"Avg First Response Time", String.format("%.2f minutes", metrics.averageFirstResponseTimeMinutes())}
            };

            for (String[] row : rows) {
                Cell labelCell = new Cell().add(new Paragraph(row[0]).setFontSize(10).setBold())
                        .setPadding(5).setBackgroundColor(new DeviceRgb(245, 243, 255));
                Cell valueCell = new Cell().add(new Paragraph(row[1]).setFontSize(10))
                        .setPadding(5);
                metricsTable.addCell(labelCell);
                metricsTable.addCell(valueCell);
            }
            document.add(metricsTable);
            document.add(new Paragraph("").setMarginBottom(15));

            // Tickets by Status
            if (metrics.ticketsByStatus() != null && !metrics.ticketsByStatus().isEmpty()) {
                document.add(new Paragraph("Tickets by Status")
                        .setFontSize(14).setBold().setMarginBottom(10));
                Table statusTable = new Table(UnitValue.createPercentArray(colWidths)).useAllAvailableWidth();
                metrics.ticketsByStatus().forEach((status, count) -> {
                    statusTable.addCell(createCell(status));
                    statusTable.addCell(createCell(String.valueOf(count)));
                });
                document.add(statusTable);
                document.add(new Paragraph("").setMarginBottom(15));
            }

            // Tickets by Priority
            if (metrics.ticketsByPriority() != null && !metrics.ticketsByPriority().isEmpty()) {
                document.add(new Paragraph("Tickets by Priority")
                        .setFontSize(14).setBold().setMarginBottom(10));
                Table priorityTable = new Table(UnitValue.createPercentArray(colWidths)).useAllAvailableWidth();
                metrics.ticketsByPriority().forEach((priority, count) -> {
                    priorityTable.addCell(createCell(priority));
                    priorityTable.addCell(createCell(String.valueOf(count)));
                });
                document.add(priorityTable);
            }

            document.close();
        } catch (Exception e) {
            log.error("Failed to export analytics to PDF: {}", e.getMessage());
            throw new RuntimeException("Analytics PDF export failed", e);
        }
        return baos.toByteArray();
    }

    /**
     * Export analytics performance summary as a CSV.
     * Columns: Metric, Value
     */
    public String exportAnalyticsToCsv(
            double aiContainmentRate, double humanEscalationRate,
            Double avgCsatRating, double avgFirstResponseTimeMinutes,
            long totalConversations, long escalatedConversations) {
        StringWriter writer = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeNext(new String[]{"Metric", "Value"});
            csvWriter.writeNext(new String[]{"Total Conversations", String.valueOf(totalConversations)});
            csvWriter.writeNext(new String[]{"AI Resolved Conversations",
                    String.valueOf(totalConversations - escalatedConversations)});
            csvWriter.writeNext(new String[]{"Escalated Conversations",
                    String.valueOf(escalatedConversations)});
            csvWriter.writeNext(new String[]{"AI Containment Rate (%)",
                    String.format("%.2f", aiContainmentRate)});
            csvWriter.writeNext(new String[]{"Human Escalation Rate (%)",
                    String.format("%.2f", humanEscalationRate)});
            csvWriter.writeNext(new String[]{"Average CSAT Rating",
                    avgCsatRating != null ? String.format("%.2f", avgCsatRating) : "N/A"});
            csvWriter.writeNext(new String[]{"Average First Response Time (min)",
                    String.format("%.2f", avgFirstResponseTimeMinutes)});
            csvWriter.writeNext(new String[]{"Generated At",
                    LocalDateTime.now().format(DATE_FORMAT)});
        } catch (Exception e) {
            log.error("Failed to export analytics to CSV: {}", e.getMessage());
            throw new RuntimeException("Analytics CSV export failed", e);
        }
        return writer.toString();
    }

    private Cell createCell(String content) {
        return new Cell().add(new Paragraph(content != null ? content : "").setFontSize(8)).setPadding(4);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
