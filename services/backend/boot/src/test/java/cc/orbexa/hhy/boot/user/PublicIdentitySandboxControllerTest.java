package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.identity.IdentitySandboxService;
import cc.orbexa.hhy.access.identity.IdentitySandboxService.Completion;
import cc.orbexa.hhy.access.identity.IdentitySandboxService.PageContext;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;

class PublicIdentitySandboxControllerTest {
    private static final String STATE = "ff738a6c-71a1-4647-ab50-5d6f3cb5f544";
    private static final String RETURN_URL = "https://h5.orbexa.cc/identity/callback";

    @Test
    void rendersCameraFlowWithoutVisibleTechnicalCopy() {
        IdentitySandboxService service = mock(IdentitySandboxService.class);
        when(service.page(STATE, RETURN_URL)).thenReturn(
                new PageContext(STATE, URI.create(RETURN_URL)));
        PublicIdentitySandboxController controller = new PublicIdentitySandboxController(service);

        String html = controller.liveness(STATE, RETURN_URL);

        assertTrue(html.contains("navigator.mediaDevices.getUserMedia"));
        assertTrue(html.contains("人脸活体检测"));
        assertTrue(html.contains("完成检测"));
        assertFalse(html.contains("请求编号"));
        assertFalse(html.contains("AppCode"));
        assertFalse(html.contains("providerOrderNo"));
        assertEquals("/public-api/v1/identity/sandbox",
                PublicIdentitySandboxController.class.getAnnotation(RequestMapping.class).value()[0]);
    }

    @Test
    void completionUsesSeeOtherBackToTheOnlyAllowedReturnUrl() {
        IdentitySandboxService service = mock(IdentitySandboxService.class);
        when(service.complete(STATE, RETURN_URL, "PASS")).thenReturn(
                new Completion("VERIFIED", URI.create(RETURN_URL)));
        PublicIdentitySandboxController controller = new PublicIdentitySandboxController(service);

        var response = controller.complete(STATE, RETURN_URL, "PASS");

        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals(RETURN_URL, response.getHeaders().getFirst(HttpHeaders.LOCATION));
        verify(service).complete(STATE, RETURN_URL, "PASS");
    }
}
