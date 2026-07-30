package cc.orbexa.hhy.boot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

class R03ConfigurationQueryControllerContractTest {
    @Test
    void frozenGetRoutesCarryTheirExactPermissions() {
        Map<String, String> expected = Map.of(
                "/provider-configs", "hasAuthority('provider.config.read')",
                "/provider-configs/{provider}", "hasAuthority('provider.config.read')",
                "/provider-certificates", "hasAuthority('provider.certificate.read')",
                "/domains", "hasAuthority('domain.read')",
                "/domains/dns-actions", "hasAuthority('domain.read')");

        for (Map.Entry<String, String> route : expected.entrySet()) {
            Method method = Arrays.stream(R03ConfigurationQueryController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.isAnnotationPresent(GetMapping.class))
                    .filter(candidate -> Arrays.asList(
                            candidate.getAnnotation(GetMapping.class).value()).contains(route.getKey()))
                    .findFirst().orElseThrow();
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertTrue(permission != null, route.getKey());
            assertEquals(route.getValue(), permission.value(), route.getKey());
        }
    }
}
