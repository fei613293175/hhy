package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.identity.IdentityCallbackService;
import cc.orbexa.hhy.access.identity.IdentityCallbackService.CallbackConsumeRequest;
import cc.orbexa.hhy.access.identity.IdentityCallbackService.CallbackStatusResource;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class PublicIdentityCallbackControllerTest {
    @Test
    void exposesPublicConsumeMappingAndDelegatesToService() throws Exception {
        IdentityCallbackService service = mock(IdentityCallbackService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        var body = new CallbackConsumeRequest("ff738a6c-71a1-4647-ab50-5d6f3cb5f544");
        when(service.consume(body)).thenReturn(new CallbackStatusResource("VERIFIED"));
        when(request.getAttribute("requestId")).thenReturn("req-1");
        var controller = new PublicIdentityCallbackController(service,
                Clock.fixed(Instant.parse("2026-07-19T19:20:00Z"), ZoneOffset.UTC));

        var response = controller.publicPostIdentityCallbackConsume(body, request);

        assertEquals("VERIFIED", response.data().status());
        verify(service).consume(body);
        assertEquals("/public-api/v1/identity/callback",
                PublicIdentityCallbackController.class.getAnnotation(RequestMapping.class).value()[0]);
        var consume = PublicIdentityCallbackController.class.getDeclaredMethod(
                "publicPostIdentityCallbackConsume", CallbackConsumeRequest.class,
                HttpServletRequest.class);
        assertEquals("/consume", consume.getAnnotation(PostMapping.class).value()[0]);
    }
}
