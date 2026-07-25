package cc.orbexa.hhy.boot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewActorContext;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewDecisionRequest;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewResource;
import cc.orbexa.hhy.content.R12ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class R12ReviewControllerContractTest {
    @Test
    void frozenOperationsCarryExactRoutesPermissionsAndRuntimeContracts() throws Exception {
        Map<String, OperationSpec> expected = Map.of(
                "adminReviewGetReviewsQueue",
                new OperationSpec("GET /reviews/queue", "hasAuthority('review.read')"),
                "adminReviewGetReviewsById",
                new OperationSpec("GET /reviews/{id}", "hasAuthority('review.read')"),
                "adminReportsGetContentReports",
                new OperationSpec("GET /content-reports", "hasAuthority('report.read')"),
                "adminAppealsGetAppeals",
                new OperationSpec("GET /appeals", "hasAuthority('appeal.read')"),
                "adminReviewPostReviewsByIdDecide",
                new OperationSpec("POST /reviews/{id}/decide", "hasAuthority('review.decide')"),
                "adminReviewPostReviewsByIdAssign",
                new OperationSpec("POST /reviews/{id}/assign", "hasAuthority('review.assign')"));

        var contract = R12ReviewControllerContractTest.class
                .getResourceAsStream("/contracts/admin-openapi.yaml");
        assertTrue(contract != null);
        String source;
        try (contract) {
            source = new String(contract.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (Map.Entry<String, OperationSpec> operation : expected.entrySet()) {
            Method method = Arrays.stream(R12ReviewController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(operation.getKey()))
                    .findFirst().orElseThrow(() -> new AssertionError(operation.getKey()));
            assertTrue(mapping(method).contains(operation.getValue().route()), operation.getKey());
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertTrue(permission != null, operation.getKey());
            assertEquals(operation.getValue().permission(), permission.value(), operation.getKey());
            assertEquals(1, source.lines().map(String::strip)
                    .filter(("operationId: " + operation.getKey())::equals).count(),
                    operation.getKey());
        }
    }

    @Test
    void decidePassesAuthenticatedSessionContextWithoutTrustingDeviceHeaders() {
        R12ReviewService service = mock(R12ReviewService.class);
        AdminClientIpResolver ipResolver = new AdminClientIpResolver("");
        HttpServletRequest request = mock(HttpServletRequest.class);
        Instant now = Instant.parse("2026-07-25T04:00:00Z");
        R12ReviewController controller = new R12ReviewController(
                service, Clock.fixed(now, ZoneOffset.UTC), ipResolver);
        AdminPrincipal principal = new AdminPrincipal(
                9, 19, 1, "jti", "reviewer-9", Set.of("review.decide"));
        ReviewDecisionRequest body = new ReviewDecisionRequest(
                "ESCALATE", "申请二审", 4L, List.of("evidence-1"));
        ReviewResource result = new ReviewResource(
                "71", "CONTENT", "71", "REVIEWING", "LOW", "12",
                "ESCALATE", "申请二审", now, 5);
        when(request.getAttribute("requestId")).thenReturn("request-19");
        when(request.getHeader("X-Device-Fingerprint")).thenReturn("untrusted-header-device");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(service.decide(org.mockito.ArgumentMatchers.any(), eq("71"), eq(body),
                eq("r12-review-key-0001"))).thenReturn(result);

        controller.adminReviewPostReviewsByIdDecide(
                principal, "71", body, "r12-review-key-0001", request);

        ArgumentCaptor<ReviewActorContext> actor = ArgumentCaptor.forClass(ReviewActorContext.class);
        verify(service).decide(actor.capture(), eq("71"), eq(body), eq("r12-review-key-0001"));
        assertEquals(9, actor.getValue().adminId());
        assertEquals(19, actor.getValue().sessionId());
        assertEquals("reviewer-9", actor.getValue().username());
        assertEquals("review.decide", actor.getValue().permission());
        assertEquals("request-19", actor.getValue().requestId());
        assertEquals("127.0.0.1", actor.getValue().ip());
        verify(request, never()).getHeader("X-Device-Fingerprint");
    }

    private static List<String> mapping(Method method) {
        for (Class<? extends Annotation> type : List.of(GetMapping.class, PostMapping.class)) {
            Annotation annotation = method.getAnnotation(type);
            if (annotation instanceof GetMapping get) {
                return Arrays.stream(get.value()).map(path -> "GET " + path).toList();
            }
            if (annotation instanceof PostMapping post) {
                return Arrays.stream(post.value()).map(path -> "POST " + path).toList();
            }
        }
        return List.of();
    }

    private record OperationSpec(String route, String permission) { }
}
