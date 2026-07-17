package cc.orbexa.hhy.shared.api;

public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final int httpStatus;
    private final boolean retryable;
    private final Long retryAfterSeconds;

    public BusinessException(String code, String message, int httpStatus, boolean retryable) {
        this(code, message, httpStatus, retryable, null);
    }

    public BusinessException(
            String code, String message, int httpStatus, boolean retryable, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
    public boolean retryable() { return retryable; }
    public Long retryAfterSeconds() { return retryAfterSeconds; }
}
