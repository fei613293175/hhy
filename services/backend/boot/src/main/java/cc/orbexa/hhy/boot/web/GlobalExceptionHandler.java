package cc.orbexa.hhy.boot.web;

import cc.orbexa.hhy.shared.api.ApiErrorResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class GlobalExceptionHandler {
    private static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute("requestId");
        return id == null ? "missing" : id.toString();
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> business(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.httpStatus())
                .body(ApiErrorResponse.of(requestId(request), ex.code(), ex.getMessage(), ex.retryable()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class
    })
    ResponseEntity<ApiErrorResponse> validation(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                requestId(request),
                "COMMON-400-VALIDATION",
                "请求字段校验失败",
                false));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(ApiErrorResponse.of(
                requestId(request),
                "COMMON-500-INTERNAL",
                "内部错误",
                true));
    }
}
