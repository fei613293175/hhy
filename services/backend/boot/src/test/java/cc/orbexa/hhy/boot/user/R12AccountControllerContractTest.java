package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.user.R12ProfileContracts.ProfilePatchRequest;
import cc.orbexa.hhy.access.user.UserPrincipal;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestHeader;

class R12AccountControllerContractTest {
    @Test
    void threeFrozenOperationsHaveExactRoutesAndAuthenticatedPrincipals() throws Exception {
        Map<String, Method> operations = Arrays.stream(R12AccountController.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("userPatch")
                        || method.getName().startsWith("membershipGet")
                        || method.getName().startsWith("rewardGet"))
                .collect(Collectors.toMap(Method::getName, Function.identity()));
        assertEquals(3, operations.size());

        Method profile = operations.get("userPatchMeProfile");
        assertNotNull(profile);
        assertArrayEquals(new String[] {"/api/v1/me/profile"},
                profile.getAnnotation(PatchMapping.class).value());
        assertTrue(Arrays.stream(profile.getParameters()).anyMatch(parameter ->
                parameter.getType() == ProfilePatchRequest.class));
        assertTrue(Arrays.stream(profile.getParameters()).anyMatch(parameter ->
                parameter.isAnnotationPresent(RequestHeader.class)
                        && "X-Idempotency-Key".equals(
                                parameter.getAnnotation(RequestHeader.class).value())));

        Method membership = operations.get("membershipGetMeMembership");
        Method reward = operations.get("rewardGetMeRewardAccount");
        assertArrayEquals(new String[] {"/api/v1/me/membership"},
                membership.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[] {"/api/v1/me/reward-account"},
                reward.getAnnotation(GetMapping.class).value());
        for (Method method : operations.values()) {
            assertTrue(Arrays.stream(method.getParameters()).anyMatch(parameter ->
                    parameter.getType() == UserPrincipal.class
                            && parameter.isAnnotationPresent(AuthenticationPrincipal.class)));
        }
    }

    @Test
    void runtimeOpenApiContainsEachOperationExactlyOnce() throws Exception {
        var contract = R12AccountControllerContractTest.class
                .getResourceAsStream("/contracts/openapi.yaml");
        assertNotNull(contract);
        String source;
        try (contract) {
            source = new String(contract.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String operation : new String[] {
                "userPatchMeProfile", "membershipGetMeMembership", "rewardGetMeRewardAccount"}) {
            assertEquals(1, source.lines().map(String::strip)
                    .filter(("operationId: " + operation)::equals).count(), operation);
        }
        String userResource = schema(source, "UserResource", "IdentitySessionResource");
        String patchRequest = schema(source, "UserPatchMeProfileRequest", "UserPatchMeProfileResponse");
        assertEquals(2, userResource.lines().map(String::strip)
                .filter("maxLength: 255"::equals).count());
        assertEquals(2, patchRequest.lines().map(String::strip)
                .filter("maxLength: 255"::equals).count());
    }

    private static String schema(String source, String start, String end) {
        int from = source.indexOf("    " + start + ":");
        int to = source.indexOf("    " + end + ":", from + start.length());
        assertTrue(from >= 0 && to > from, start);
        return source.substring(from, to);
    }
}
