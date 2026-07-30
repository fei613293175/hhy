package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class R13ControllerContractTest {
    @Test
    void frozenR13OperationIdsHaveExactHttpMappings() {
        Map<String, Expected> operations = new LinkedHashMap<>();
        operations.put("contentPostContentsByIdFavorite",
                new Expected(R08Controller.class, "POST", "/api/v1/contents/{id}/favorite"));
        operations.put("contentDeleteContentsByIdFavorite",
                new Expected(R13Controller.class, "DELETE", "/api/v1/contents/{id}/favorite"));
        operations.put("contentGetMeFavorites",
                new Expected(R13Controller.class, "GET", "/api/v1/me/favorites"));
        operations.put("contentGetMeHistory",
                new Expected(R13Controller.class, "GET", "/api/v1/me/history"));
        operations.put("contentPostContentsByIdShare",
                new Expected(R08Controller.class, "POST", "/api/v1/contents/{id}/share"));
        operations.put("contentPostContentsByIdInvalidFeedback",
                new Expected(R13Controller.class, "POST", "/api/v1/contents/{id}/invalid-feedback"));

        operations.forEach((operationId, expected) -> {
            Method method = java.util.Arrays.stream(expected.controller().getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(operationId)).findFirst().orElseThrow();
            assertEquals(expected.path(), path(method, expected.verb()), operationId);
        });
    }

    private static String path(Method method, String verb) {
        return switch (verb) {
            case "GET" -> {
                GetMapping mapping = method.getAnnotation(GetMapping.class);
                assertNotNull(mapping);
                yield mapping.value()[0];
            }
            case "POST" -> {
                PostMapping mapping = method.getAnnotation(PostMapping.class);
                assertNotNull(mapping);
                yield mapping.value()[0];
            }
            case "DELETE" -> {
                DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
                assertNotNull(mapping);
                yield mapping.value()[0];
            }
            default -> throw new IllegalArgumentException("unsupported verb");
        };
    }

    private record Expected(Class<?> controller, String verb, String path) { }
}
