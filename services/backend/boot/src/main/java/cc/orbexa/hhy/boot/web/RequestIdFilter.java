package cc.orbexa.hhy.boot.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestIdFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final String REQUEST_HEADER = "X-Request-Id";
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-fA-F]{2}-([0-9a-fA-F]{32})-[0-9a-fA-F]{16}-[0-9a-fA-F]{2}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = requestId(request.getHeader(REQUEST_HEADER));
        String traceId = traceId(request.getHeader(TRACE_HEADER), request.getHeader(TRACEPARENT_HEADER));
        long startedAt = System.nanoTime();
        boolean failed = false;

        request.setAttribute("requestId", requestId);
        request.setAttribute("traceId", traceId);
        response.setHeader(REQUEST_HEADER, requestId);
        response.setHeader(TRACE_HEADER, traceId);
        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            int status = failed && response.getStatus() < 400 ? 500 : response.getStatus();
            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            LOGGER.atInfo()
                    .addKeyValue("operation", operation(request))
                    .addKeyValue("status", status)
                    .addKeyValue("latency", latency)
                    .log("http_request_completed");
            MDC.remove("traceId");
            MDC.remove("requestId");
        }
    }

    private static String requestId(String incoming) {
        return incoming != null && REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }

    private static String traceId(String explicitTraceId, String traceparent) {
        if (explicitTraceId != null && TRACE_ID.matcher(explicitTraceId).matches()) {
            return explicitTraceId.toLowerCase();
        }
        if (traceparent != null) {
            Matcher matcher = TRACEPARENT.matcher(traceparent);
            if (matcher.matches() && !matcher.group(1).equals("00000000000000000000000000000000")) {
                return matcher.group(1).toLowerCase();
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String operation(HttpServletRequest request) {
        Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = routePattern == null ? "UNMAPPED" : routePattern.toString();
        return request.getMethod() + " " + route;
    }
}
