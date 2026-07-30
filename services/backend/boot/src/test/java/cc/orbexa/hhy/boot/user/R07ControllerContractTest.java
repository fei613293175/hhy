package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.content.R07Contracts.CommandResult;
import cc.orbexa.hhy.content.R07Contracts.ContactAccess;
import cc.orbexa.hhy.content.R07Contracts.ContactAccessRequest;
import cc.orbexa.hhy.content.R07Contracts.PageMeta;
import cc.orbexa.hhy.content.R07Contracts.PublisherSummary;
import cc.orbexa.hhy.content.R07Contracts.SearchResults;
import cc.orbexa.hhy.content.R07Contracts.SearchTerms;
import cc.orbexa.hhy.content.R07Service;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class R07ControllerContractTest {
    private static final Instant NOW = Instant.parse("2026-07-21T17:45:00Z");
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(11, 1, 0, "jti", "ACTIVE");

    @Test
    void exposesTheSixR07ControllerOperationsAndProtectsSensitiveContactResponse() throws Exception {
        R07Service service = mock(R07Service.class);
        R07Controller controller = new R07Controller(service, Clock.fixed(NOW, ZoneOffset.UTC));
        HttpServletRequest request = request();
        PageMeta page = new PageMeta(1, 20, "0", null, "false");
        SearchResults results = new SearchResults(List.of(), page);
        SearchTerms terms = new SearchTerms(List.of(), page);
        PublisherSummary publisher = new PublisherSummary("7", "发布者", null, null,
                true, "PRO", true);
        ContactAccess contact = new ContactAccess("EMAIL", "contact@example.com", NOW);
        CommandResult cleared = new CommandResult("11", null, "CLEARED", 0, NOW);
        ContactAccessRequest body = new ContactAccessRequest(Map.of("action", "VIEW"));
        when(service.search(eq(11L), eq("合作"), any(), any(), any(),
                eq(1), eq(20), any(), eq("relevance:desc"))).thenReturn(results);
        when(service.hot(1, 20, null, null, "weight:desc")).thenReturn(terms);
        when(service.history(11, 1, 20, null, null, "createdAt:desc")).thenReturn(terms);
        when(service.clearHistory(11, "r07-history-key-001")).thenReturn(cleared);
        when(service.publisher(11, "7")).thenReturn(publisher);
        when(service.contact(11, "42", "EMAIL", body, "r07-contact-key-001")).thenReturn(contact);

        assertEquals(results, controller.searchGetSearch(PRINCIPAL, "合作", null, null,
                null, 1, 20, null, "relevance:desc", request).data());
        assertEquals(terms, controller.searchGetSearchHot(
                1, 20, null, null, "weight:desc", request).data());
        assertEquals(terms, controller.searchGetSearchHistory(
                PRINCIPAL, 1, 20, null, null, "createdAt:desc", request).data());
        assertEquals(cleared, controller.searchDeleteSearchHistory(
                PRINCIPAL, "r07-history-key-001", request).data());
        assertEquals(publisher, controller.userGetPublishersById(PRINCIPAL, "7", request).data());
        var response = controller.contentPostContentsByIdContactsByChannelAccess(
                PRINCIPAL, "42", "EMAIL", body, "r07-contact-key-001", request);
        assertEquals(contact, response.getBody().data());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("no-cache", response.getHeaders().getFirst("Pragma"));
        verify(service).contact(11, "42", "EMAIL", body, "r07-contact-key-001");

        assertMapping("searchGetSearch", GetMapping.class, "/api/v1/search");
        assertMapping("searchGetSearchHot", GetMapping.class, "/api/v1/search/hot");
        assertMapping("searchGetSearchHistory", GetMapping.class, "/api/v1/search/history");
        assertMapping("searchDeleteSearchHistory", DeleteMapping.class, "/api/v1/search/history");
        assertMapping("userGetPublishersById", GetMapping.class, "/api/v1/publishers/{id}");
        assertMapping("contentPostContentsByIdContactsByChannelAccess", PostMapping.class,
                "/api/v1/contents/{id}/contacts/{channel}/access");
        assertTrue(Arrays.stream(R07Controller.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class))
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .anyMatch(parameter -> parameter.getType() == UserPrincipal.class
                        && parameter.isAnnotationPresent(AuthenticationPrincipal.class)));
    }

    private static void assertMapping(
            String methodName, Class<? extends java.lang.annotation.Annotation> annotation,
            String expectedPath) {
        Method method = Arrays.stream(R07Controller.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        String actual = annotation == GetMapping.class
                ? method.getAnnotation(GetMapping.class).value()[0]
                : annotation == PostMapping.class
                ? method.getAnnotation(PostMapping.class).value()[0]
                : method.getAnnotation(DeleteMapping.class).value()[0];
        assertEquals(expectedPath, actual);
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("requestId")).thenReturn("request-r07-001");
        return request;
    }
}
