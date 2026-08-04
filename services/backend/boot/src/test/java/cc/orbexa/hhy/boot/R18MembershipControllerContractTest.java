package cc.orbexa.hhy.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cc.orbexa.hhy.boot.admin.R18MembershipAdminController;
import cc.orbexa.hhy.boot.user.R12AccountController;
import cc.orbexa.hhy.boot.user.R18MembershipController;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class R18MembershipControllerContractTest {
    @Test
    void springManagedControllersRemainProxyable() {
        assertFalse(Modifier.isFinal(R18MembershipController.class.getModifiers()));
        assertFalse(Modifier.isFinal(R18MembershipAdminController.class.getModifiers()));
    }

    @Test
    void allTenFrozenOperationIdsExistExactlyOnceWithExactPermissions() {
        Map<Class<?>, Map<String, String>> expected = new LinkedHashMap<>();
        expected.put(R12AccountController.class, Map.of(
                "membershipGetMeMembership", ""));
        expected.put(R18MembershipController.class, Map.of(
                "membershipGetMembershipSkus", "",
                "membershipPostMembershipOrders", "",
                "membershipPostMembershipUpgradeQuotes", "",
                "membershipPostMembershipUpgradeOrders", ""));
        expected.put(R18MembershipAdminController.class, Map.of(
                "adminMembershipGetMembershipSkus", "hasAuthority('membership.read')",
                "adminMembershipPatchMembershipSkusById", "hasAuthority('membership.write')",
                "adminMembershipPutMembershipSkusByIdBenefits", "hasAuthority('membership.write')",
                "adminMembershipGetUserMemberships", "hasAuthority('membership.read')",
                "adminMembershipPostUserMembershipsGrants", "hasAuthority('membership.grant')"));

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
