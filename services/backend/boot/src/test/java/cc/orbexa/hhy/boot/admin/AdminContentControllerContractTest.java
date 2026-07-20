package cc.orbexa.hhy.boot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class AdminContentControllerContractTest {
    @Test
    void frozenR06RoutesCarryExactPermissions() {
        Map<String, String> expected = Map.of(
                "GET /contents", "hasAuthority('content.read')",
                "GET /contents/{id}", "hasAuthority('content.read')",
                "POST /contents/{id}/online", "hasAuthority('content.manage')",
                "POST /contents/{id}/offline", "hasAuthority('content.manage')",
                "POST /contents/{id}/ban", "hasAuthority('content.ban')",
                "POST /contents/{id}/recommend", "hasAuthority('content.recommend')",
                "POST /contents/{id}/official-mark", "hasAuthority('content.official')",
                "GET /content-dictionaries", "hasAuthority('content.dict.read')",
                "PUT /content-dictionaries/{code}", "hasAuthority('content.dict.write')");

        for (Map.Entry<String, String> route : expected.entrySet()) {
            Method method = Arrays.stream(AdminContentController.class.getDeclaredMethods())
                    .filter(candidate -> mapping(candidate).contains(route.getKey()))
                    .findFirst().orElseThrow(() -> new AssertionError(route.getKey()));
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertTrue(permission != null, route.getKey());
            assertEquals(route.getValue(), permission.value(), route.getKey());
        }
    }

    private static List<String> mapping(Method method) {
        for (Class<? extends Annotation> type : List.of(GetMapping.class, PostMapping.class, PutMapping.class)) {
            Annotation annotation = method.getAnnotation(type);
            if (annotation instanceof GetMapping get) {
                return Arrays.stream(get.value()).map(path -> "GET " + path).toList();
            }
            if (annotation instanceof PostMapping post) {
                return Arrays.stream(post.value()).map(path -> "POST " + path).toList();
            }
            if (annotation instanceof PutMapping put) {
                return Arrays.stream(put.value()).map(path -> "PUT " + path).toList();
            }
        }
        return List.of();
    }
}
