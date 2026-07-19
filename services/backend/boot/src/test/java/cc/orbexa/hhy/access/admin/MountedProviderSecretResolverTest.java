package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MountedProviderSecretResolverTest {
    private static final String REFERENCE = "vault://hhy/storage/r2-access";

    @TempDir
    Path directory;

    @Test
    void resolvesStrictReferenceFromDeterministicReadOnlyMaterialFile() throws Exception {
        Path file = material(REFERENCE, "provider-fixture-value\n");
        var resolver = new MountedProviderSecretResolver(directory);

        char[] result = resolver.resolve(REFERENCE);

        assertArrayEquals("provider-fixture-value".toCharArray(), result);
        assertEquals(file.getFileName().toString(),
                MountedProviderSecretResolver.materialFileName(REFERENCE));
        assertEquals(71, file.getFileName().toString().length());
    }

    @Test
    void doesNotCacheMaterialAcrossRotation() throws Exception {
        Path file = material(REFERENCE, "rotation-fixture-one");
        var resolver = new MountedProviderSecretResolver(directory);
        char[] first = resolver.resolve(REFERENCE);
        Files.delete(file);
        Files.writeString(file, "rotation-fixture-two", StandardCharsets.UTF_8);
        restrict(file);

        char[] second = resolver.resolve(REFERENCE);

        assertNotEquals(new String(first), new String(second));
        assertArrayEquals("rotation-fixture-two".toCharArray(), second);
    }

    @Test
    void rejectsInvalidReferencesSymlinksAndOversizedMaterial() throws Exception {
        var resolver = new MountedProviderSecretResolver(directory);
        assertThrows(IllegalStateException.class, () -> resolver.resolve("file:///tmp/value"));
        assertThrows(IllegalStateException.class, () -> resolver.resolve("vault://../escape"));

        Path oversized = directory.resolve(
                MountedProviderSecretResolver.materialFileName("kms://hhy/oversized"));
        Files.write(oversized, new byte[MountedProviderSecretResolver.MAX_BYTES + 1]);
        restrict(oversized);
        assertThrows(IllegalStateException.class, () -> resolver.resolve("kms://hhy/oversized"));

        Path target = material("vault://hhy/real", "real-fixture-value");
        Path link = directory.resolve(
                MountedProviderSecretResolver.materialFileName("vault://hhy/link"));
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | java.io.IOException unsupported) {
            Assumptions.abort("symbolic links are unavailable on this test host");
        }
        assertThrows(IllegalStateException.class, () -> resolver.resolve("vault://hhy/link"));
    }

    @Test
    void rejectsWorldReadableMaterialOnPosix() throws Exception {
        FileStore store = Files.getFileStore(directory);
        Assumptions.assumeTrue(store.supportsFileAttributeView("posix"));
        Path file = material(REFERENCE, "permission-fixture-value");
        Files.setPosixFilePermissions(file, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OTHERS_READ));

        var resolver = new MountedProviderSecretResolver(directory);
        assertThrows(IllegalStateException.class, () -> resolver.resolve(REFERENCE));
    }

    @Test
    void productionRequiresConfiguredReadableDirectory() {
        var missing = new ProviderSecretMaterialProperties("");
        ProviderSecretStartupGuard.verify(missing, false);
        assertThrows(IllegalStateException.class,
                () -> ProviderSecretStartupGuard.verify(missing, true));
        assertThrows(IllegalStateException.class, () -> ProviderSecretStartupGuard.verify(
                new ProviderSecretMaterialProperties(directory.resolve("missing").toString()), false));
    }

    private Path material(String reference, String value) throws Exception {
        Path file = directory.resolve(MountedProviderSecretResolver.materialFileName(reference));
        Files.writeString(file, value, StandardCharsets.UTF_8);
        restrict(file);
        return file;
    }

    private static void restrict(Path file) throws Exception {
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ));
        }
    }
}
