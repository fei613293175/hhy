package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

class R12PublishingControllerContractTest {
    @Test
    void tenFrozenContentOperationsHaveExactlyOneControllerLanding() throws Exception {
        Set<String> operations = Set.of(
                "contentGetContentsById", "contentPostContentsByIdCopy",
                "contentGetContentsByIdAnalytics", "contentGetMeContents",
                "contentPostContentsByIdOnline", "contentPostContentsByIdOffline",
                "contentGetMeDrafts", "contentDeleteContentsById",
                "contentGetContentsByIdReviews", "contentPostContentsByIdSubmit");
        var contract = R12PublishingControllerContractTest.class
                .getResourceAsStream("/contracts/openapi.yaml");
        assertNotNull(contract);
        String source;
        try (contract) {
            source = new String(contract.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String operation : operations) {
            assertEquals(1, source.lines().map(String::strip)
                    .filter(("operationId: " + operation)::equals).count(), operation);
            long controllerCount = Arrays.stream(new Class<?>[] {R08Controller.class, R12PublishingController.class})
                    .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                    .map(Method::getName).filter(operation::equals).count();
            assertEquals(1, controllerCount, operation);
        }
    }

    @Test
    void deleteUsesFrozenRouteAndRequiredExpectedVersionQuery() {
        Method method = Arrays.stream(R12PublishingController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("contentDeleteContentsById"))
                .findFirst().orElseThrow();
        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
        assertEquals("/api/v1/contents/{id}", mapping.value()[0]);
        Parameter expectedVersion = Arrays.stream(method.getParameters())
                .filter(parameter -> parameter.isAnnotationPresent(RequestParam.class)
                        && parameter.getName().equals("expectedVersion"))
                .findFirst().orElseThrow();
        assertTrue(expectedVersion.getAnnotation(RequestParam.class).required());
    }
}
