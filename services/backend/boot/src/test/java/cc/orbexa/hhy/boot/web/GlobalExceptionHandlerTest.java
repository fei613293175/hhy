package cc.orbexa.hhy.boot.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void unexpectedFailureReusesMdcCorrelationWithoutDuplicateStructuredField() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("requestId", "request-safe-001");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute("requestId", "request-safe-001");
            new GlobalExceptionHandler(Clock.systemUTC()).unexpected(
                    new IllegalStateException(
                            "https://storage.example/private/key?X-Amz-Credential=must-not-appear"),
                    request);
        } finally {
            MDC.remove("requestId");
            logger.detachAppender(appender);
            appender.stop();
        }

        ILoggingEvent event = appender.list.stream()
                .filter(row -> "unhandled_api_exception".equals(row.getFormattedMessage()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields)
                .containsEntry("errorType", "IllegalStateException")
                .doesNotContainKey("requestId");
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "request-safe-001");
        assertThat(event.toString())
                .doesNotContain("must-not-appear", "storage.example", "private/key", "X-Amz");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void unexpectedFailureFallsBackToStructuredCorrelationWhenMdcIsMissing() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.remove("requestId");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute("requestId", "request-fallback-001");
            new GlobalExceptionHandler(Clock.systemUTC()).unexpected(
                    new IllegalArgumentException("must-not-appear"), request);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        ILoggingEvent event = appender.list.stream()
                .filter(row -> "unhandled_api_exception".equals(row.getFormattedMessage()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields)
                .containsEntry("requestId", "request-fallback-001")
                .containsEntry("errorType", "IllegalArgumentException");
        assertThat(event.getMDCPropertyMap()).doesNotContainKey("requestId");
        assertThat(event.toString()).doesNotContain("must-not-appear");
        assertThat(event.getThrowableProxy()).isNull();
    }
}
