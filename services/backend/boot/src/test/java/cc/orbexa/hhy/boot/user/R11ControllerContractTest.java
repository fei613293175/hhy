package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.content.R11Service;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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
        String repositoryRoot = System.getenv().getOrDefault("HHY_REPO_ROOT", "../..");
        String source = Files.readString(Path.of(repositoryRoot, "contracts/openapi.yaml"));
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
