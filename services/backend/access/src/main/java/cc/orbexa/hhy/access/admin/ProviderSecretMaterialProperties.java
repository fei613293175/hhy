package cc.orbexa.hhy.access.admin;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hhy.provider-secrets")
public record ProviderSecretMaterialProperties(String materialDirectory) {
    public boolean configured() {
        return materialDirectory != null && !materialDirectory.isBlank();
    }

    public Path directory() {
        if (!configured()) throw new IllegalStateException("Provider secret material directory is not configured");
        return Path.of(materialDirectory.strip()).toAbsolutePath().normalize();
    }
}
