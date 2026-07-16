package cc.orbexa.hhy.shared.api;

public final class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final int httpStatus;
    private final boolean retryable;

    public BusinessException(String code, String message, int httpStatus, boolean retryable) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
    public boolean retryable() { return retryable; }
}
