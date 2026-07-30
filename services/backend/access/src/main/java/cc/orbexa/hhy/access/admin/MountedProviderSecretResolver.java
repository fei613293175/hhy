package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.SecretResolver;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads short-lived Vault/KMS material from a read-only sidecar or CSI mount without caching it. */
public final class MountedProviderSecretResolver implements SecretResolver {
    static final int MAX_BYTES = 8 * 1024;
    private static final Pattern REFERENCE = Pattern.compile(
            "^(?:vault|kms)://[A-Za-z0-9_./:@-]{3,506}[A-Za-z0-9_./:@-]$");
    private static final Set<OpenOption> READ_NOFOLLOW = Set.of(
            StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    private static final Set<PosixFilePermission> PROHIBITED_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_EXECUTE);
    private final Path directory;

    public MountedProviderSecretResolver(Path directory) {
        this.directory = requireDirectory(directory);
    }

    @Override
    public char[] resolve(String reference) {
        String fileName = materialFileName(reference);
        Path target = directory.resolve(fileName).normalize();
        if (!target.getParent().equals(directory)) throw unavailable();
        byte[] bytes = new byte[MAX_BYTES + 1];
        CharBuffer decoded = CharBuffer.allocate(MAX_BYTES);
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || Files.isSymbolicLink(target)
                    || attributes.size() < 1 || attributes.size() > MAX_BYTES) {
                throw unavailable();
            }
            requireRestrictedPermissions(target);
            int length = read(target, bytes);
            if (length > MAX_BYTES) throw unavailable();
            if (bytes[length - 1] == '\n') {
                length--;
                if (length > 0 && bytes[length - 1] == '\r') length--;
            }
            if (length < 1) throw unavailable();
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer source = ByteBuffer.wrap(bytes, 0, length);
            var decodeResult = decoder.decode(source, decoded, true);
            if (decodeResult.isError()) decodeResult.throwException();
            var flushResult = decoder.flush(decoded);
            if (flushResult.isError()) flushResult.throwException();
            decoded.flip();
            char[] material = new char[decoded.remaining()];
            decoded.get(material);
            if (material.length == 0 || containsControlCharacter(material)) {
                Arrays.fill(material, '\0');
                throw unavailable();
            }
            return material;
        } catch (IllegalStateException known) {
            throw known;
        } catch (Exception failure) {
            throw new IllegalStateException("Provider secret material is unavailable", failure);
        } finally {
            Arrays.fill(bytes, (byte) 0);
            Arrays.fill(decoded.array(), '\0');
        }
    }

    static String materialFileName(String reference) {
        if (reference == null || reference.length() > 512 || !REFERENCE.matcher(reference).matches()) {
            throw unavailable();
        }
        String body = reference.substring(reference.indexOf("://") + 3);
        if (body.startsWith("/") || body.endsWith("/") || body.contains("//")
                || Arrays.stream(body.split("/"))
                        .anyMatch(segment -> segment.equals(".") || segment.equals(".."))) {
            throw unavailable();
        }
        try {
            byte[] encoded = reference.getBytes(StandardCharsets.UTF_8);
            try {
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(encoded)) + ".secret";
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        } catch (Exception unavailable) {
            throw unavailable();
        }
    }

    static Path requireDirectory(Path value) {
        if (value == null) throw unavailable();
        Path normalized = value.toAbsolutePath().normalize();
        try {
            if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(normalized) || !Files.isReadable(normalized)) {
                throw unavailable();
            }
            return normalized;
        } catch (SecurityException denied) {
            throw unavailable();
        }
    }

    private static int read(Path target, byte[] destination) throws IOException {
        int total = 0;
        try (SeekableByteChannel channel = Files.newByteChannel(target, READ_NOFOLLOW)) {
            ByteBuffer buffer = ByteBuffer.wrap(destination);
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer);
                if (read < 0) break;
                if (read == 0) continue;
                total += read;
            }
        }
        return total;
    }

    private static void requireRestrictedPermissions(Path target) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(
                target, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) return;
        Set<PosixFilePermission> permissions = view.readAttributes().permissions();
        if (!permissions.contains(PosixFilePermission.OWNER_READ)
                || permissions.stream().anyMatch(PROHIBITED_PERMISSIONS::contains)) {
            throw unavailable();
        }
    }

    private static boolean containsControlCharacter(char[] value) {
        for (char character : value) if (Character.isISOControl(character)) return true;
        return false;
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Provider secret material is unavailable");
    }
}
