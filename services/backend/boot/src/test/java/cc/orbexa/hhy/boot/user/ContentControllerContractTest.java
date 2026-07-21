package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.content.ContentContracts.ContentPage;
import cc.orbexa.hhy.content.ContentContracts.PageMeta;
import cc.orbexa.hhy.content.ContentService;
import cc.orbexa.hhy.shared.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

class ContentControllerContractTest {
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(11, 1, 0, "jti", "ACTIVE");

    @Test
    void clientListForcesOnlineVisibilityAndPassesPublisherFilter() throws Exception {
        ContentService service = mock(ContentService.class);
        ContentController controller = new ContentController(service,
                Clock.fixed(Instant.parse("2026-07-21T17:45:00Z"), ZoneOffset.UTC));
        ContentPage result = new ContentPage(List.of(), new PageMeta(1, 20, "0", null, "false"));
        when(service.list(1, 20, null, "ONLINE", "合作", "createdAt:desc",
                null, null, null, "7")).thenReturn(result);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("requestId")).thenReturn("request-r07-content");

        var response = controller.contentGetContents(PRINCIPAL, 1, 20, null, null,
                "合作", "createdAt:desc", null, null, null, "7", request);

        assertEquals(result, response.data());
        assertEquals("/api/v1/contents", ContentController.class
                .getDeclaredMethod("contentGetContents", UserPrincipal.class, int.class, int.class,
                        String.class, String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class, HttpServletRequest.class)
                .getAnnotation(GetMapping.class).value()[0]);
        verify(service).list(1, 20, null, "ONLINE", "合作", "createdAt:desc",
                null, null, null, "7");
    }

    @Test
    void clientCannotRequestDraftOrOfflineRows() {
        ContentController controller = new ContentController(mock(ContentService.class), Clock.systemUTC());

        BusinessException error = assertThrows(BusinessException.class, () ->
                controller.contentGetContents(PRINCIPAL, 1, 20, null, "DRAFT", null,
                        "createdAt:desc", null, null, null, null, mock(HttpServletRequest.class)));

        assertEquals("COMMON-400-VALIDATION", error.code());
    }
}
