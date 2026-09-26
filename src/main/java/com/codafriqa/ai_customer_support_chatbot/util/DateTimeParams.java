package com.codafriqa.ai_customer_support_chatbot.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Lenient parsing of date range query parameters.
 *
 * <p>The Vue dashboards send values from {@code <input type="date">}
 * ({@code yyyy-MM-dd}) while API docs and scripts often send full ISO
 * date-times ({@code 2026-09-26T10:15:30}, optionally with an offset).
 * Binding these straight to {@link LocalDateTime} with
 * {@code @DateTimeFormat(iso = DATE_TIME)} made every date-only request fail
 * request binding with HTTP 400, which the frontend surfaced as
 * "Failed to load analytics data".
 *
 * <p>Both formats are accepted here, and a missing/blank value falls back to
 * the caller's default so an absent range never fails the request.
 * Unparseable values throw {@link IllegalArgumentException}, which
 * {@code GlobalExceptionHandler} maps to HTTP 400.
 */
public final class DateTimeParams {

    private DateTimeParams() {
    }

    /**
     * Parse a range start. Date-only values become the start of that day.
     *
     * @param raw          raw query parameter (may be null or blank)
     * @param defaultValue returned when the parameter is absent
     */
    public static LocalDateTime parseStart(String raw, LocalDateTime defaultValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        LocalDateTime parsed = parse(raw);
        if (parsed != null) return parsed;
        try {
            return LocalDate.parse(raw).atStartOfDay();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Invalid startDate: '" + raw + "' (expected yyyy-MM-dd or an ISO date-time)");
        }
    }

    /**
     * Parse a range end. Date-only values become the end of that day so the
     * whole day is included in the range.
     *
     * @param raw          raw query parameter (may be null or blank)
     * @param defaultValue returned when the parameter is absent
     */
    public static LocalDateTime parseEnd(String raw, LocalDateTime defaultValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        LocalDateTime parsed = parse(raw);
        if (parsed != null) return parsed;
        try {
            return LocalDate.parse(raw).atTime(23, 59, 59);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Invalid endDate: '" + raw + "' (expected yyyy-MM-dd or an ISO date-time)");
        }
    }

    /** ISO date-time, with or without an offset. Returns null if neither fits. */
    private static LocalDateTime parse(String raw) {
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        return null;
    }
}
