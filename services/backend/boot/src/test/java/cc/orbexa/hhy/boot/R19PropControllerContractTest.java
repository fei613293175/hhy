package cc.orbexa.hhy.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cc.orbexa.hhy.boot.admin.R19PropAdminController;
import cc.orbexa.hhy.boot.user.R19PropController;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class R19PropControllerContractTest {
    @Test
    void springManagedControllersRemainProxyable() {
        assertFalse(Modifier.isFinal(R19PropController.class.getModifiers()));
        assertFalse(Modifier.isFinal(R19PropAdminController.class.getModifiers()));
    }

    @Test
    void allR19OperationIdsExistExactlyOnceWithExactPermissions() {
        Map<Class<?>, Map<String, String>> expected = new LinkedHashMap<>();
        expected.put(R19PropController.class, Map.of(
                "propGetPropsStore", "",
                "propGetMeProps", "",
                "propPostPropsOrders", "",
                "propPostMePropsByIdUse", ""));
        expected.put(R19PropAdminController.class, Map.of(
                "adminPropsGetProps", "hasAuthority('prop.read')",
                "adminPropsPostProps", "hasAuthority('prop.write')",
                "adminPropsPatchPropsById", "hasAuthority('prop.write')",
                "adminPropsGetHeadlineSlots", "hasAuthority('prop.slot.read')",
                "adminPropsPostHeadlineSlots", "hasAuthority('prop.slot.write')",
                "adminPropsGetPropExecutions", "hasAuthority('prop.read')"));

        for (Map.Entry<Class<?>, Map<String, String>> controller : expected.entrySet()) {
            for (Map.Entry<String, String> operation : controller.getValue().entrySet()) {
                Method method = java.util.Arrays.stream(controller.getKey().getDeclaredMethods())
                        .filter(candidate -> candidate.getName().equals(operation.getKey()))
                        .reduce((left, right) -> {
                            throw new AssertionError("duplicate operationId " + operation.getKey());
                        })
                        .orElseThrow(() -> new AssertionError(
                                "missing operationId " + operation.getKey()));
                if (!operation.getValue().isEmpty()) {
                    PreAuthorize authority = method.getAnnotation(PreAuthorize.class);
                    assertNotNull(authority, operation.getKey());
                    assertEquals(operation.getValue(), authority.value(), operation.getKey());
                }
            }
        }
    }
}
