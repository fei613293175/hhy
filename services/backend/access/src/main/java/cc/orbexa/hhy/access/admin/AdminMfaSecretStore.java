package cc.orbexa.hhy.access.admin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * File-backed secret provider for administrator TOTP seeds.
 *
 * <p>The database stores only an opaque versioned reference. Secret payloads are
 * AES-GCM protected by a runtime key supplied outside the repository/database.
 * The provider is intentionally fail-closed: an unreadable, missing or
 * unauthenticated secret never falls back to deterministic derivation.</p>
 */
@Component
public final class AdminMfaSecretStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String REF_PREFIX = "secret-file:";
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final Path directory;
    private final String currentVersion;
    private final Map<String, SecretKeySpec> keys;

    @Autowired
    public AdminMfaSecretStore(
            AdminSecurityProperties properties,
            @Value("${hhy.admin-security.mfa-secret-directory}") String directory,
            @Value("${hhy.admin-security.mfa-root-secret-version:v1}") String currentVersion,
            @Value("${hhy.admin-security.mfa-previous-root-secrets:}") String previousSecrets) {
        this(directory, currentVersion, properties.mfaRootSecret(), previousSecrets);
    }

    AdminMfaSecretStore(
            String directory, String currentVersion, String currentSecret, String previousSecrets) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.currentVersion = validateToken(currentVersion, "MFA root key version");
        this.keys = new HashMap<>();
        keys.put(this.currentVersion, key(currentSecret));
        if (previousSecrets != null && !previousSecrets.isBlank()) {
            for (String entry : previousSecrets.split(";")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2 || pair[1].length() < 32) {
                    throw new IllegalArgumentException("Invalid previous MFA root key entry");
                }
                String version = validateToken(pair[0], "MFA root key version");
                if (keys.putIfAbsent(version, key(pair[1])) != null) {
                    throw new IllegalArgumentException("Duplicate MFA root key version");
                }
            }
        }
        prepareDirectory();
    }

    public StoredSecret create(long adminId, String enrollmentId) {
        String safeEnrollmentId = validateToken(enrollmentId, "MFA enrollment id");
        String reference = reference(currentVersion, safeEnrollmentId);
        String totpSeed = randomBase32Secret();
        Path target = path(adminId, reference);
        try {
            Files.createFile(target);
            restrict(target, FILE_PERMISSIONS);
            Files.writeString(target, encrypt(adminId, reference, totpSeed), StandardCharsets.US_ASCII,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return new StoredSecret(reference, totpSeed);
        } catch (Exception exception) {
            tryDeleteEmpty(target);
            throw new IllegalStateException("Unable to persist administrator MFA secret", exception);
        }
    }

    public StoredSecret resolve(long adminId, String reference) {
        ParsedReference parsed = parse(reference);
        Path target = path(adminId, reference);
        try {
            String payload = Files.readString(target, StandardCharsets.US_ASCII);
            return new StoredSecret(reference, decrypt(adminId, reference, parsed.version(), payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve administrator MFA secret", exception);
        }
    }

    public String enrollmentId(String reference) {
        return parse(reference).enrollmentId();
    }

    public void revoke(long adminId, String reference) {
        parse(reference);
        try {
            Files.deleteIfExists(path(adminId, reference));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to revoke administrator MFA secret", exception);
        }
    }

    private String encrypt(long adminId, String reference, String plainSeed) throws Exception {
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentVersion), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad(adminId, reference));
        byte[] ciphertext = cipher.doFinal(plainSeed.getBytes(StandardCharsets.US_ASCII));
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(nonce) + "." + encoder.encodeToString(ciphertext);
    }

    private String decrypt(long adminId, String reference, String version, String payload) throws Exception {
        SecretKeySpec key = keys.get(version);
        if (key == null) {
            throw new IllegalStateException("MFA root key version is unavailable");
        }
        String[] parts = payload.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("MFA secret payload is malformed");
        }
        Base64.Decoder decoder = Base64.getUrlDecoder();
        byte[] nonce = decoder.decode(parts[0]);
        if (nonce.length != 12) {
            throw new IllegalStateException("MFA secret nonce is malformed");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad(adminId, reference));
        return new String(cipher.doFinal(decoder.decode(parts[1])), StandardCharsets.US_ASCII);
    }

    private Path path(long adminId, String reference) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((adminId + ":" + reference).getBytes(StandardCharsets.UTF_8)));
            Path target = directory.resolve(digest + ".secret").normalize();
            if (!target.getParent().equals(directory)) {
                throw new IllegalArgumentException("Invalid MFA secret reference");
            }
            return target;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to locate administrator MFA secret", exception);
        }
    }

    private void prepareDirectory() {
        try {
            Files.createDirectories(directory);
            restrict(directory, DIRECTORY_PERMISSIONS);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare administrator MFA secret directory", exception);
        }
    }

    private static void restrict(Path path, Set<PosixFilePermission> permissions) throws Exception {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows ACLs are inherited from the configured directory. Production Linux uses POSIX 0600/0700.
        }
    }

    private static void tryDeleteEmpty(Path target) {
        try {
            if (Files.exists(target) && Files.size(target) == 0) Files.delete(target);
        } catch (Exception ignored) {
            // Preserve the primary fail-closed exception.
        }
    }

    private static ParsedReference parse(String reference) {
        if (reference == null || !reference.startsWith(REF_PREFIX)) {
            throw new IllegalArgumentException("Invalid MFA secret reference");
        }
        String[] parts = reference.substring(REF_PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid MFA secret reference");
        }
        return new ParsedReference(
                validateToken(parts[0], "MFA root key version"),
                validateToken(parts[1], "MFA enrollment id"));
    }

    private static String reference(String version, String enrollmentId) {
        return REF_PREFIX + version + ":" + enrollmentId;
    }

    private static String validateToken(String value, String name) {
        if (value == null || !value.matches("^[A-Za-z0-9._-]{1,64}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static SecretKeySpec key(String value) {
        if (value == null || value.length() < 32) {
            throw new IllegalArgumentException("MFA root key must contain at least 32 characters");
        }
        try {
            return new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize MFA root key", exception);
        }
    }

    private static byte[] aad(long adminId, String reference) {
        return (adminId + ":" + reference).getBytes(StandardCharsets.UTF_8);
    }

    private static String randomBase32Secret() {
        byte[] seed = new byte[20];
        RANDOM.nextBytes(seed);
        return AdminTotpService.base32(seed);
    }

    public record StoredSecret(String reference, String secret) { }
    private record ParsedReference(String version, String enrollmentId) { }
}
