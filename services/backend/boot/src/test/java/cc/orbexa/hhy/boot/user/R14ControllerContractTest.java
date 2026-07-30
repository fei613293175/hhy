package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class R14ControllerContractTest {
    @Test
    void controllerExposesAllNineFrozenChatOperations() {
        Map<String, String> operations = Map.of(
                "chatGetConversations", "/api/v1/conversations",
                "chatPostConversationsDirect", "/api/v1/conversations/direct",
                "chatGetConversationsByIdMessages", "/api/v1/conversations/{id}/messages",
                "chatPostConversationsByIdMessages", "/api/v1/conversations/{id}/messages",
                "chatPostConversationsByIdRead", "/api/v1/conversations/{id}/read",
                "chatDeleteConversationsById", "/api/v1/conversations/{id}",
                "chatPostUsersByIdBlock", "/api/v1/users/{id}/block",
                "chatDeleteUsersByIdBlock", "/api/v1/users/{id}/block",
                "chatPostConversationsByIdReport", "/api/v1/conversations/{id}/report");
        operations.forEach((operationId, expectedPath) -> {
            Method method = java.util.Arrays.stream(R14Controller.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(operationId)).findFirst().orElseThrow();
            String path;
            if (method.isAnnotationPresent(GetMapping.class)) {
                path = method.getAnnotation(GetMapping.class).value()[0];
            } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                path = method.getAnnotation(DeleteMapping.class).value()[0];
            } else {
                assertNotNull(method.getAnnotation(PostMapping.class));
                path = method.getAnnotation(PostMapping.class).value()[0];
            }
            assertEquals(expectedPath, path, operationId);
        });
    }
}
