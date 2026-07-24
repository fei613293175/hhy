package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.content.R11Service;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class R11ControllerContractTest {
    @Test
    void eightFrozenOperationsHaveOneControllerLandingAndTeamLeaderDispatcher() throws Exception {
        assertTrue(Arrays.stream(R08Controller.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("teamLeaderService")
                        && field.getType().equals(R11Service.class)));
        Set<String> r11Operations = Set.of(
                "contentGetContents", "contentGetContentsById", "contentPostContents",
                "contentPatchContentsById", "contentPostContentsByIdContactsByChannelAccess",
                "chatPostConversationsDirect", "contentPostContentsByIdFavorite", "contentPostContentsByIdShare");
        var contract = R11ControllerContractTest.class.getResourceAsStream("/contracts/openapi.yaml");
        assertNotNull(contract, "runtime OpenAPI contract must be on the boot classpath");
        String source;
        try (contract) {
            source = new String(contract.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String operation : r11Operations) {
            assertEquals(1, source.lines().map(String::strip)
                    .filter(("operationId: " + operation)::equals).count(), operation);
            long controllerCount = Arrays.stream(new Class<?>[] {ContentController.class, R07Controller.class, R08Controller.class})
                    .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                    .map(Method::getName).filter(operation::equals).count();
            assertEquals(1, controllerCount, operation);
        }
        assertTrue(Arrays.stream(R08Controller.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(R11Service.class::equals));
    }
}
