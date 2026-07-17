package cc.orbexa.hhy.boot.web;

import cc.orbexa.hhy.shared.api.ApiErrorResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public final class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    private static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute("requestId");
        return id == null ? "missing" : id.toString();
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> business(BusinessException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.httpStatus());
        if (ex.httpStatus() == 429 && ex.retryAfterSeconds() != null) {
            response.header("Retry-After", Long.toString(ex.retryAfterSeconds()));
        }
        return response
                .body(error(request, ex.code(), ex.getMessage(), ex.retryable()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> validation(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                request,
                "COMMON-400-VALIDATION",
                "请求字段校验失败",
                false));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbidden(
            AuthorizationDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(403).body(error(
                request,
                "COMMON-403-FORBIDDEN",
                "缺少访问权限",
                false));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        LOG.error("unhandled_api_exception requestId={}", requestId(request), ex);
        return ResponseEntity.internalServerError().body(error(
                request,
                "COMMON-500-INTERNAL",
                "内部错误",
                true));
    }

    private ApiErrorResponse error(
            HttpServletRequest request, String code, String message, boolean retryable) {
        return ApiErrorResponse.of(
                requestId(request), code, message, retryable, Instant.now(clock));
    }
}
