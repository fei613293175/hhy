package cc.orbexa.hhy.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cc.orbexa.hhy.boot.admin.R16CommerceAdminController;
import cc.orbexa.hhy.boot.user.R16OrderController;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class R16CommerceControllerContractTest {
    @Test
    void springManagedControllersMustRemainProxyable() {
        assertFalse(Modifier.isFinal(R16CommerceAdminController.class.getModifiers()));
        assertFalse(Modifier.isFinal(R16OrderController.class.getModifiers()));
    }

    @Test
    void allFrozenOperationIdsExistExactlyOnce() {
        Map<Class<?>, Map<String, String>> expected = Map.of(
                R16OrderController.class, Map.of(
                        "orderGetMeOrders", "",
                        "orderGetMeOrdersByOrderno", ""),
                R16CommerceAdminController.class, Map.of(
                        "adminProductsGetProducts", "hasAuthority('product.read')",
                        "adminProductsPostProducts", "hasAuthority('product.write')",
                        "adminProductsPatchProductsById", "hasAuthority('product.write')",
                        "adminProductsGetSkus", "hasAuthority('product.read')",
                        "adminProductsPostSkus", "hasAuthority('product.write')",
                        "adminProductsPatchSkusById", "hasAuthority('product.write')",
                        "adminOrdersGetOrders", "hasAuthority('order.read')",
                        "adminOrdersGetOrdersByOrderno", "hasAuthority('order.read')"));

        for (Map.Entry<Class<?>, Map<String, String>> controller : expected.entrySet()) {
            for (Map.Entry<String, String> operation : controller.getValue().entrySet()) {
                Method method = java.util.Arrays.stream(controller.getKey().getDeclaredMethods())
                        .filter(candidate -> candidate.getName().equals(operation.getKey()))
                        .reduce((left, right) -> {
                            throw new AssertionError("duplicate operationId method " + operation.getKey());
                        })
                        .orElseThrow(() -> new AssertionError("missing operationId " + operation.getKey()));
                if (!operation.getValue().isEmpty()) {
                    PreAuthorize authority = method.getAnnotation(PreAuthorize.class);
                    assertNotNull(authority, operation.getKey());
                    assertEquals(operation.getValue(), authority.value(), operation.getKey());
                }
            }
        }
    }
}
