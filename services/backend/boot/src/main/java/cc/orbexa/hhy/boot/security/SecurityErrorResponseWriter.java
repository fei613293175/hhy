package cc.orbexa.hhy.boot.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public final class SecurityErrorResponseWriter {
    private final Clock clock;

    public SecurityErrorResponseWriter(Clock clock) {
        this.clock = clock;
    }

    void writeUnauthenticated(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "COMMON-401-UNAUTHENTICATED", "未认证或会话失效");
    }

    void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN,
                "COMMON-403-FORBIDDEN", "权限或能力不足");
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message) throws IOException {
        Object requestIdAttribute = request.getAttribute("requestId");
        String requestId = requestIdAttribute == null ? "missing" : requestIdAttribute.toString();
        Object traceIdAttribute = request.getAttribute("traceId");
        String traceId = traceIdAttribute == null ? "missing" : traceIdAttribute.toString();
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"success":false,"requestId":"%s","timestamp":"%s","error":{"code":"%s","message":"%s","details":[],"retryable":false,"traceId":"%s"}}
                """.formatted(requestId, Instant.now(clock), code, message, traceId).strip());
    }
}
