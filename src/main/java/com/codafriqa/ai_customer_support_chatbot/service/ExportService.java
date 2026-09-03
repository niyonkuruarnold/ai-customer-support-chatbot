package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.AuditLog;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.repository.AuditLogRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFontFactory;
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

/**
 * Service for generating CSV and PDF exports of ticket and audit log data.
 */
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

    // ─── CSV Export ─────────────────────────────────────────────────────

    /**
     * Export tickets to CSV format.
     */
    public String exportTicketsToCsv(List<SupportTicket> tickets) {
        StringWriter writer = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(writer)) {
            // Header
            csvWriter.writeNext(new String[]{
                "ID", "Reference", "Subject", "Description", "Status", "Priority",
                "Category", "Assigned Agent", "Sentiment", "Created At", "Updated At"
            });

            // Data rows
            for (SupportTicket ticket : tickets) {
                csvWriter.writeNext(new String[]{
                    String.valueOf(ticket.getId()),
                    ticket.getTicketReference(),
                    ticket.getSubject(),
                    ticket.getDescription(),
                    ticket.getStatus(),
                    ticket.getPriority(),
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

    /**
     * Export audit logs to CSV format.
     */
    public String exportAuditLogsToCsv(List<AuditLog> logs) {
        StringWriter writer = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(writer)) {
            // Header
            csvWriter.writeNext(new String[]{
                "ID", "Actor Email", "Action Type", "Description", "IP Address",
                "Resource Type", "Resource ID", "Success", "Timestamp"
            });

            // Data rows
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

    // ─── PDF Export ─────────────────────────────────────────────────────

    /**
     * Export tickets to PDF format.
     */
    public byte[] exportTicketsToPdf(List<SupportTicket> tickets) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Title
            document.add(new Paragraph("Support Tickets Report")
                .setFontSize(18)
                .setBold()
                .setFontColor(ColorConstants.DARK_GRAY)
                .setMarginBottom(20));

            // Generated timestamp
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DATE_FORMAT))
                .setFontSize(10)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(20));

            // Create table
            float[] columnWidths = {1, 2, 3, 2, 1.5f, 1.5f, 2};
            Table table = new Table(UnitValue.createPercentArray(columnWidths))
                .useAllAvailableWidth()
                .setHorizontalAlignment(HorizontalAlignment.CENTER);

            // Header row
            DeviceRgb headerBg = new DeviceRgb(79, 70, 229); // Indigo
            String[] headers = {"ID", "Reference", "Subject", "Status", "Priority", "Category", "Agent"};
            for (String header : headers) {
                Cell cell = new Cell()
                    .add(new Paragraph(header).setFontSize(9).setBold())
                    .setBackgroundColor(headerBg)
                    .setFontColor(ColorConstants.WHITE)
                    .setPadding(5);
                table.addHeaderCell(cell);
            }

            // Data rows
            for (SupportTicket ticket : tickets) {
                table.addCell(createCell(String.valueOf(ticket.getId())));
                table.addCell(createCell(ticket.getTicketReference()));
                table.addCell(createCell(truncate(ticket.getSubject(), 40)));
                table.addCell(createCell(ticket.getStatus()));
                table.addCell(createCell(ticket.getPriority()));
                table.addCell(createCell(ticket.getCategory()));
                table.addCell(createCell(ticket.getAssignedAgent() != null ? ticket.getAssignedAgent() : "—"));
            }

            document.add(table);
            document.close();

        } catch (Exception e) {
            log.error("Failed to export tickets to PDF: {}", e.getMessage());
            throw new RuntimeException("PDF export failed", e);
        }
        return baos.toByteArray();
    }

    /**
     * Export audit logs to PDF format.
     */
    public byte[] exportAuditLogsToPdf(List<AuditLog> logs) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Title
            document.add(new Paragraph("Audit Log Report")
                .setFontSize(18)
                .setBold()
                .setFontColor(ColorConstants.DARK_GRAY)
                .setMarginBottom(20));

            // Generated timestamp
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DATE_FORMAT))
                .setFontSize(10)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(20));

            // Create table
            float[] columnWidths = {1, 2, 2, 3, 1.5f, 2};
            Table table = new Table(UnitValue.createPercentArray(columnWidths))
                .useAllAvailableWidth()
                .setHorizontalAlignment(HorizontalAlignment.CENTER);

            // Header row
            DeviceRgb headerBg = new DeviceRgb(79, 70, 229); // Indigo
            String[] headers = {"ID", "Actor", "Action", "Description", "Success", "Timestamp"};
            for (String header : headers) {
                Cell cell = new Cell()
                    .add(new Paragraph(header).setFontSize(9).setBold())
                    .setBackgroundColor(headerBg)
                    .setFontColor(ColorConstants.WHITE)
                    .setPadding(5);
                table.addHeaderCell(cell);
            }

            // Data rows
            for (AuditLog auditLog : logs) {
                table.addCell(createCell(String.valueOf(auditLog.getId())));
                table.addCell(createCell(auditLog.getActorEmail()));
                table.addCell(createCell(auditLog.getActionType()));
                table.addCell(createCell(truncate(auditLog.getDescription(), 50)));
                table.addCell(createCell(auditLog.isSuccess() ? "✓" : "✗"));
                table.addCell(createCell(auditLog.getTimestamp() != null 
                    ? auditLog.getTimestamp().format(DATE_FORMAT) : ""));
            }

            document.add(table);
            document.close();

        } catch (Exception e) {
            log.error("Failed to export audit logs to PDF: {}", e.getMessage());
            throw new RuntimeException("PDF export failed", e);
        }
        return baos.toByteArray();
    }

    // ─── Helper Methods ─────────────────────────────────────────────────

    private Cell createCell(String content) {
        return new Cell()
            .add(new Paragraph(content != null ? content : "").setFontSize(8))
            .setPadding(4);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
