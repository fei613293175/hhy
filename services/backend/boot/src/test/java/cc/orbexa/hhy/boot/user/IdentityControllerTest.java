package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.identity.IdentityContracts.CreateLivenessTokenRequest;
import cc.orbexa.hhy.access.identity.IdentityContracts.CreateSessionRequest;
import cc.orbexa.hhy.access.identity.IdentityContracts.IdentitySessionResource;
import cc.orbexa.hhy.access.identity.IdentityContracts.IdentityConsentResource;
import cc.orbexa.hhy.access.identity.IdentityContracts.RetrySessionRequest;
import cc.orbexa.hhy.access.identity.IdentityService;
import cc.orbexa.hhy.access.user.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class IdentityControllerTest {
    private static final Instant NOW = Instant.parse("2026-07-19T17:30:00Z");
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(17, 1, 0, "jti", "ACTIVE");

    @Test
    void exposesTheFrozenClientMappingsAndDelegatesToService() throws Exception {
        IdentityService service = mock(IdentityService.class);
        IdentityController controller = new IdentityController(service, Clock.fixed(NOW, ZoneOffset.UTC));
        HttpServletRequest servlet = request();
        String key = "identity-controller-001";
        CreateSessionRequest create = new CreateSessionRequest("张三", "110101199001011234", "identity-v1");
        CreateLivenessTokenRequest liveness = new CreateLivenessTokenRequest("https://app.orbexa.cc/identity");
        RetrySessionRequest retry = new RetrySessionRequest("重新认证", 3L, Map.of());
        IdentitySessionResource resource = new IdentitySessionResource(
                "41", "17", "SESSION_CREATED", "identity-provider", null, null,
                NOW.plusSeconds(300), 0L);
        IdentityConsentResource consent = new IdentityConsentResource(
                "91", "实名认证授权说明", "当前正文");
        when(service.consent(PRINCIPAL)).thenReturn(consent);
        when(service.create(PRINCIPAL, create, key)).thenReturn(resource);
        when(service.livenessToken(PRINCIPAL, "41", liveness, key)).thenReturn(resource);
        when(service.get(PRINCIPAL, "41")).thenReturn(resource);
        when(service.retry(PRINCIPAL, "41", retry, key)).thenReturn(resource);

        assertEquals(consent, controller.identityGetIdentityConsent(PRINCIPAL, servlet).data());
        assertEquals(resource, controller.identityPostIdentitySessions(
                PRINCIPAL, create, key, servlet).data());
        assertEquals(resource, controller.identityPostIdentitySessionsByIdLivenessToken(
                PRINCIPAL, "41", liveness, key, servlet).data());
        assertEquals(resource, controller.identityGetIdentitySessionsById(
                PRINCIPAL, "41", servlet).data());
        assertEquals(resource, controller.identityPostIdentitySessionsByIdRetry(
                PRINCIPAL, "41", retry, key, servlet).data());
        verify(service).consent(PRINCIPAL);
        verify(service).create(PRINCIPAL, create, key);
        verify(service).livenessToken(PRINCIPAL, "41", liveness, key);
        verify(service).get(PRINCIPAL, "41");
        verify(service).retry(PRINCIPAL, "41", retry, key);

        assertEquals("/api/v1/identity",
                IdentityController.class.getAnnotation(RequestMapping.class).value()[0]);
        Method consentGet = IdentityController.class.getMethod("identityGetIdentityConsent",
                UserPrincipal.class, HttpServletRequest.class);
        assertEquals("/consent", consentGet.getAnnotation(GetMapping.class).value()[0]);
        assertPost("identityPostIdentitySessions", "/sessions", UserPrincipal.class,
                CreateSessionRequest.class, String.class, HttpServletRequest.class);
        assertPost("identityPostIdentitySessionsByIdLivenessToken", "/sessions/{id}/liveness-token",
                UserPrincipal.class, String.class, CreateLivenessTokenRequest.class,
                String.class, HttpServletRequest.class);
        Method get = IdentityController.class.getMethod("identityGetIdentitySessionsById",
                UserPrincipal.class, String.class, HttpServletRequest.class);
        assertEquals("/sessions/{id}", get.getAnnotation(GetMapping.class).value()[0]);
        assertPost("identityPostIdentitySessionsByIdRetry", "/sessions/{id}/retry",
                UserPrincipal.class, String.class, RetrySessionRequest.class,
                String.class, HttpServletRequest.class);
    }

    private static void assertPost(String method, String path, Class<?>... parameters) throws Exception {
        Method mapping = IdentityController.class.getMethod(method, parameters);
        String[] values = mapping.getAnnotation(PostMapping.class).value();
        assertEquals(path, values.length == 0 ? "" : values[0]);
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("requestId")).thenReturn("request-identity-001");
        return request;
    }
}
