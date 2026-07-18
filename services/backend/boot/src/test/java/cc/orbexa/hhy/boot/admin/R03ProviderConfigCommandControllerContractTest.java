package cc.orbexa.hhy.boot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

class R03ProviderConfigCommandControllerContractTest {
    @Test
    void frozenProviderWriteRoutesCarryExactPermissions() {
        Map<String, String> expected = Map.of(
                "/versions", "hasAuthority('provider.config.write')",
                "/test", "hasAuthority('provider.config.test')",
                "/activate", "hasAuthority('provider.config.activate')",
                "/rollback", "hasAuthority('provider.config.activate')");
        for (Map.Entry<String, String> route : expected.entrySet()) {
            Method method = Arrays.stream(R03ProviderConfigCommandController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.isAnnotationPresent(PostMapping.class))
                    .filter(candidate -> Arrays.asList(
                            candidate.getAnnotation(PostMapping.class).value()).contains(route.getKey()))
                    .findFirst().orElseThrow();
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertTrue(permission != null, route.getKey());
            assertEquals(route.getValue(), permission.value(), route.getKey());
        }
    }
}
