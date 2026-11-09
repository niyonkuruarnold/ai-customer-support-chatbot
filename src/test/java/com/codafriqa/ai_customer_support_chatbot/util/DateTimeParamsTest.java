package com.codafriqa.ai_customer_support_chatbot.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Vue dashboards send &lt;input type="date"&gt; values (yyyy-MM-dd) while
 * scripts send full ISO date-times; both must parse so analytics/audit range
 * requests return 200 instead of a request-binding 400.
 */
class DateTimeParamsTest {

    private static final LocalDateTime DEFAULT_START = LocalDateTime.of(2026, 8, 27, 0, 0);
    private static final LocalDateTime DEFAULT_END = LocalDateTime.of(2026, 9, 26, 12, 0);

    @Test
    void fallsBackToDefaultWhenParameterIsAbsentOrBlank() {
        assertEquals(DEFAULT_START, DateTimeParams.parseStart(null, DEFAULT_START));
        assertEquals(DEFAULT_START, DateTimeParams.parseStart("  ", DEFAULT_START));
        assertEquals(DEFAULT_END, DateTimeParams.parseEnd(null, DEFAULT_END));
        assertEquals(DEFAULT_END, DateTimeParams.parseEnd("", DEFAULT_END));
    }

    @Test
    void acceptsDateOnlyValuesFromHtmlDateInputs() {
        assertEquals(LocalDateTime.of(2026, 8, 27, 0, 0),
                DateTimeParams.parseStart("2026-08-27", DEFAULT_START));
        // End of day, so the whole final day is inside the range
        assertEquals(LocalDateTime.of(2026, 9, 26, 23, 59, 59),
                DateTimeParams.parseEnd("2026-09-26", DEFAULT_END));
    }

    @Test
    void acceptsIsoDateTimes() {
        assertEquals(LocalDateTime.of(2026, 8, 27, 10, 15, 30),
                DateTimeParams.parseStart("2026-08-27T10:15:30", DEFAULT_START));
        assertEquals(LocalDateTime.of(2026, 9, 26, 23, 59),
                DateTimeParams.parseEnd("2026-09-26T23:59", DEFAULT_END));
    }

    @Test
    void acceptsDateTimesWithOffset() {
        assertNotNull(DateTimeParams.parseStart("2026-08-27T10:15:30Z", DEFAULT_START));
        assertNotNull(DateTimeParams.parseEnd("2026-09-26T23:59:59+02:00", DEFAULT_END));
    }

    @Test
    void rejectsUnparseableValuesWithHttp400FriendlyError() {
        // GlobalExceptionHandler maps IllegalArgumentException to HTTP 400
        assertThrows(IllegalArgumentException.class,
                () -> DateTimeParams.parseStart("not-a-date", DEFAULT_START));
        assertThrows(IllegalArgumentException.class,
                () -> DateTimeParams.parseEnd("27/08/2026", DEFAULT_END));
    }
}
