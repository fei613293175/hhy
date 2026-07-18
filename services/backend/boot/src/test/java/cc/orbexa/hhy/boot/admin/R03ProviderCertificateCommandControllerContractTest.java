package cc.orbexa.hhy.boot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

class R03ProviderCertificateCommandControllerContractTest {
    @Test
    void uploadAndRotationUseFrozenPermissions() {
        var methods = Arrays.stream(R03ProviderCertificateCommandController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class)).toList();
        var upload = methods.stream()
                .filter(method -> method.getAnnotation(PostMapping.class).value().length == 0)
                .findFirst().orElseThrow();
        var rotate = methods.stream()
                .filter(method -> Arrays.asList(method.getAnnotation(PostMapping.class).value())
                        .contains("/{id}/rotate"))
                .findFirst().orElseThrow();
        assertEquals("hasAuthority('provider.certificate.write')",
                upload.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('provider.certificate.rotate')",
                rotate.getAnnotation(PreAuthorize.class).value());
    }
}
