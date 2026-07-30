package cc.orbexa.hhy.boot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class R03FrozenApiCoverageTest {
    @Test
    void allThirteenFrozenConfigurationEndpointsHaveControllers() {
        Set<String> actual = new LinkedHashSet<>();
        collect(actual, R03ConfigurationQueryController.class);
        collect(actual, R03ProviderConfigCommandController.class);
        collect(actual, R03ProviderCertificateCommandController.class);
        collect(actual, R03DomainCommandController.class);

        Set<String> expected = Set.of(
                "GET /admin-api/v1/provider-configs",
                "GET /admin-api/v1/provider-configs/{provider}",
                "POST /admin-api/v1/provider-configs/{provider}/versions",
                "POST /admin-api/v1/provider-configs/{provider}/test",
                "POST /admin-api/v1/provider-configs/{provider}/activate",
                "POST /admin-api/v1/provider-configs/{provider}/rollback",
                "POST /admin-api/v1/provider-certificates",
                "GET /admin-api/v1/provider-certificates",
                "POST /admin-api/v1/provider-certificates/{id}/rotate",
                "GET /admin-api/v1/domains",
                "PUT /admin-api/v1/domains/{code}",
                "GET /admin-api/v1/domains/dns-actions",
                "POST /admin-api/v1/domains/{code}/verify");
        assertEquals(expected, actual);
    }

    private static void collect(Set<String> routes, Class<?> controller) {
        String base = controller.getAnnotation(RequestMapping.class).value()[0];
        for (Method method : controller.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class)) {
                add(routes, "GET", base, method.getAnnotation(GetMapping.class).value());
            }
            if (method.isAnnotationPresent(PostMapping.class)) {
                add(routes, "POST", base, method.getAnnotation(PostMapping.class).value());
            }
            if (method.isAnnotationPresent(PutMapping.class)) {
                add(routes, "PUT", base, method.getAnnotation(PutMapping.class).value());
            }
        }
    }

    private static void add(Set<String> routes, String verb, String base, String[] values) {
        if (values.length == 0) {
            routes.add(verb + " " + base);
            return;
        }
        for (String value : values) routes.add(verb + " " + base + value);
    }
}
