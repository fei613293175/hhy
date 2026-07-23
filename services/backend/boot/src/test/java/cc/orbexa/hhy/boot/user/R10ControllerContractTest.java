package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.content.R10Service;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class R10ControllerContractTest {
    @Test
    void frozenControllerUsesOneGroupDispatcherWithoutDuplicateMappings() {
        Field groupService = java.util.Arrays.stream(R08Controller.class.getDeclaredFields())
                .filter(field -> field.getType().equals(R10Service.class)).findFirst().orElseThrow();
        assertEquals("groupService", groupService.getName());
        for (String methodName : new String[] {
                "contentPostContents", "contentGetContentsById",
                "contentPatchContentsById", "publicGetShareContentsById"}) {
            long count = java.util.Arrays.stream(R08Controller.class.getDeclaredMethods())
                    .map(Method::getName).filter(methodName::equals).count();
            assertEquals(1, count, methodName);
        }
        assertTrue(java.util.Arrays.stream(R08Controller.class.getDeclaredConstructors())
                .flatMap(constructor -> java.util.Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(R10Service.class::equals));
    }
}
