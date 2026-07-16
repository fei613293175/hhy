package cc.orbexa.hhy.shared.api;

import java.time.Instant;
import java.util.Objects;

public record ApiResponse<T>(boolean success, String requestId, Instant timestamp, T data) {
    public ApiResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(data, "data");
    }
    public static <T> ApiResponse<T> success(String requestId, T data) {
        return new ApiResponse<>(true, requestId, Instant.now(), data);
    }
}
