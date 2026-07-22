package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

class R08ControllerContractTest {
    @Test
    void controllerExposesAllSevenPreviouslyMissingFrozenOperations() throws Exception {
        Map<String, String> methods = Map.of(
                "contentPostContents", "/api/v1/contents",
                "contentGetContentsById", "/api/v1/contents/{id}",
                "contentPatchContentsById", "/api/v1/contents/{id}",
                "contentPostContentsByIdFavorite", "/api/v1/contents/{id}/favorite",
                "contentPostContentsByIdShare", "/api/v1/contents/{id}/share",
                "chatPostConversationsDirect", "/api/v1/conversations/direct",
                "publicGetShareContentsById", "/public-api/v1/share/contents/{id}");
        for (Map.Entry<String, String> expected : methods.entrySet()) {
            Method method = java.util.Arrays.stream(R08Controller.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(expected.getKey())).findFirst().orElseThrow();
            String path;
            if (method.isAnnotationPresent(GetMapping.class)) path = method.getAnnotation(GetMapping.class).value()[0];
            else if (method.isAnnotationPresent(PatchMapping.class)) path = method.getAnnotation(PatchMapping.class).value()[0];
            else {
                assertNotNull(method.getAnnotation(PostMapping.class));
                path = method.getAnnotation(PostMapping.class).value()[0];
            }
            assertEquals(expected.getValue(), path, expected.getKey());
        }
    }
}
