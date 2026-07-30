package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.user.CiAutomationService;
import cc.orbexa.hhy.access.user.CiAutomationService.BootstrapCode;
import cc.orbexa.hhy.access.user.CiAutomationService.VerifiedWorkflow;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class CiAutomationControllerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");
    private static final String COMMIT = "a".repeat(40);

    @Test
    void verifiedOidcIdentityPassesValidatedReleaseToService() {
        GitHubOidcVerifier verifier = mock(GitHubOidcVerifier.class);
        CiAutomationService service = mock(CiAutomationService.class);
        VerifiedWorkflow identity = new VerifiedWorkflow("fei613293175/hhy", "workflow", COMMIT, "42");
        BootstrapCode expected = new BootstrapCode("code", NOW.plusSeconds(60), COMMIT, "42");
        when(verifier.verify("token", COMMIT, "42")).thenReturn(identity);
        when(service.issue(identity, "R12")).thenReturn(expected);
        CiAutomationController controller = new CiAutomationController(
                verifier, service, Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("requestId", "request-1");

        var response = controller.bootstrap("Bearer token",
                new CiAutomationController.BootstrapRequest(COMMIT, "42", "R12"), request);

        assertEquals(expected, response.data());
        verify(service).issue(identity, "R12");
    }

    @Test
    void oidcFailureNeverInvokesFixtureIssuanceService() {
        GitHubOidcVerifier verifier = mock(GitHubOidcVerifier.class);
        CiAutomationService service = mock(CiAutomationService.class);
        when(verifier.verify("bad", COMMIT, "42")).thenThrow(
                new JwtValidationException("invalid", java.util.List.of(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"))));
        CiAutomationController controller = new CiAutomationController(
                verifier, service, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(JwtValidationException.class, () -> controller.bootstrap("Bearer bad",
                new CiAutomationController.BootstrapRequest(COMMIT, "42", "R12"),
                new MockHttpServletRequest()));

        verifyNoInteractions(service);
    }

    @Test
    void bootstrapRequestRejectsInvalidRelease() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(
                    new CiAutomationController.BootstrapRequest(COMMIT, "42", "R33"));
            assertFalse(violations.isEmpty());
        }
    }
}
