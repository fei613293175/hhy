package cc.orbexa.hhy.shared.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(boolean success, String requestId, Instant timestamp, ErrorBody error) {
    public record ErrorBody(String code, String message, List<ErrorDetail> details, boolean retryable, String traceId) { }
    public record ErrorDetail(String field, String code, String message) { }
    public static ApiErrorResponse of(
            String requestId, String traceId, String code, String message, boolean retryable) {
        return new ApiErrorResponse(false, requestId, Instant.now(),
                new ErrorBody(code, message, List.of(), retryable, traceId));
    }

    public static ApiErrorResponse of(
            String requestId, String traceId, String code, String message, boolean retryable, Instant timestamp) {
        return new ApiErrorResponse(false, requestId, timestamp,
                new ErrorBody(code, message, List.of(), retryable, traceId));
    }
}
