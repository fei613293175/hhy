package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.user.UserPrincipal;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;

class HomeControllerContractTest {
    @Test
    void frozenHomeRouteRequiresAnAuthenticatedUserPrincipal() {
        Method method = Arrays.stream(HomeController.class.getDeclaredMethods())
                .filter(candidate -> candidate.isAnnotationPresent(GetMapping.class))
                .findFirst().orElseThrow();
        assertArrayEquals(new String[]{"/api/v1/home"}, method.getAnnotation(GetMapping.class).value());
        assertTrue(Arrays.stream(method.getParameters()).anyMatch(parameter ->
                parameter.getType() == UserPrincipal.class
                        && parameter.isAnnotationPresent(AuthenticationPrincipal.class)));
    }
}
