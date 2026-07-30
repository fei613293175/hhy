package cc.orbexa.hhy.boot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class R03DomainCommandControllerContractTest {
    @Test
    void updateAndVerificationUseFrozenPermissions() {
        var update = Arrays.stream(R03DomainCommandController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PutMapping.class))
                .findFirst().orElseThrow();
        var verify = Arrays.stream(R03DomainCommandController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .findFirst().orElseThrow();
        assertEquals("hasAuthority('domain.write')",
                update.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('domain.verify')",
                verify.getAnnotation(PreAuthorize.class).value());
    }
}
