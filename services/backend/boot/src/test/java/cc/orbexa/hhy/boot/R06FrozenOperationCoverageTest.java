package cc.orbexa.hhy.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class R06FrozenOperationCoverageTest {
    private static final Set<String> USER_OPERATIONS = Set.of("homeGetHome");
    private static final Set<String> ADMIN_OPERATIONS = Set.of(
            "adminContentGetContents",
            "adminContentGetContentsById",
            "adminContentPostContentsByIdOnline",
            "adminContentPostContentsByIdOffline",
            "adminContentPostContentsByIdBan",
            "adminContentPostContentsByIdRecommend",
            "adminContentPostContentsByIdOfficialMark",
            "adminContentGetContentDictionaries",
            "adminContentPutContentDictionariesByCode");

    @Test
    void runtimeContractsContainExactlyTheTenFrozenR06Operations() throws IOException {
        String user = resource("/contracts/openapi.yaml");
        String admin = resource("/contracts/admin-openapi.yaml");

        assertEquals(10, USER_OPERATIONS.size() + ADMIN_OPERATIONS.size());
        USER_OPERATIONS.forEach(operation -> assertTrue(
                user.contains("operationId: " + operation), operation));
        ADMIN_OPERATIONS.forEach(operation -> assertTrue(
                admin.contains("operationId: " + operation), operation));
    }

    private static String resource(String path) throws IOException {
        try (var stream = R06FrozenOperationCoverageTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing runtime contract " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
