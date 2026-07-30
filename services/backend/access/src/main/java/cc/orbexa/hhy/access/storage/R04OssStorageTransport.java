package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.SecretResolver;
import cc.orbexa.hhy.access.storage.R04StorageProviderSettings.Settings;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Binding;
import cc.orbexa.hhy.access.storage.StorageObjectPort.CompleteUpload;
import cc.orbexa.hhy.access.storage.StorageObjectPort.MigrationPage;
import cc.orbexa.hhy.access.storage.StorageObjectPort.ObjectRef;
import cc.orbexa.hhy.access.storage.StorageObjectPort.ReadTicket;
import cc.orbexa.hhy.access.storage.StorageObjectPort.StoredObject;
import cc.orbexa.hhy.access.storage.StorageObjectPort.UploadIntent;
import cc.orbexa.hhy.access.storage.StorageObjectPort.UploadTicket;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Alibaba Cloud OSS V4 transport; credentials remain inside one short-lived SDK scope. */
public final class R04OssStorageTransport implements StorageProviderAdapter.StorageTransport {
    private static final int API_TIMEOUT_MILLIS = 15_000;
    private static final String SHA_METADATA = "sha256";
    private final ConfigurationSource configuration;
    private final SecretResolver secrets;
    private final Clock clock;
    private final FacadeFactory facades;

    public R04OssStorageTransport(
            ConfigurationSource configuration, SecretResolver secrets, Clock clock) {
        this(configuration, secrets, clock, OssSdkFacade::new);
    }

    R04OssStorageTransport(
            ConfigurationSource configuration, SecretResolver secrets,
            Clock clock, FacadeFactory facades) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.facades = Objects.requireNonNull(facades, "facades");
    }

    @Override
    public UploadTicket createUpload(Binding binding, UploadIntent intent) {
        Settings settings = configuration.current(binding);
        return withFacade(settings, facade -> facade.signPut(
                settings.bucket(), objectKey(binding, intent), intent.contentType(),
                intent.sha256().toLowerCase(Locale.ROOT), settings.signedUrlTtl(), clock.instant()));
    }

    @Override
    public StoredObject completeUpload(Binding binding, CompleteUpload command) {
        Settings settings = configuration.current(binding);
        Head head = withFacade(settings, facade -> facade.head(settings.bucket(), command.objectKey()));
        if (head.sizeBytes() != command.expectedSizeBytes()
                || !command.expectedSha256().equalsIgnoreCase(head.sha256())
                || !etag(command.etag()).equals(etag(head.etag()))) {
            throw new IllegalStateException("Stored object integrity verification failed");
        }
        return new StoredObject(
                command.objectKey(), head.etag(), head.sizeBytes(),
                head.sha256().toLowerCase(Locale.ROOT), clock.instant());
    }

    @Override
    public ReadTicket createReadUrl(Binding binding, ObjectRef object, Duration ttl) {
        Settings settings = configuration.current(binding);
        if (ttl.compareTo(settings.signedUrlTtl()) > 0) {
            throw new IllegalArgumentException("read ttl exceeds activated storage policy");
        }
        return withFacade(settings, facade -> facade.signGet(
                settings.bucket(), object.objectKey(), ttl, clock.instant()));
    }

    @Override
    public void delete(Binding binding, ObjectRef object) {
        Settings settings = configuration.current(binding);
        withFacade(settings, facade -> {
            facade.delete(settings.bucket(), object.objectKey());
            return null;
        });
    }

    @Override
    public MigrationPage scan(Binding binding, String cursor, int limit) {
        Settings settings = configuration.current(binding);
        return withFacade(settings, facade -> facade.scan(settings.bucket(), cursor, limit));
    }

    @Override
    public void copy(Binding source, Binding target, ObjectRef object) {
        if (source.provider() != target.provider()
                || source.configVersionId() != target.configVersionId()) {
            throw new IllegalArgumentException("cross-configuration server-side copy is unsupported");
        }
        Settings settings = configuration.current(target);
        withFacade(settings, facade -> {
            facade.copy(source.bucket(), target.bucket(), object.objectKey());
            return null;
        });
    }

    private <T> T withFacade(Settings settings, Function<OssFacade, T> operation) {
        char[] accessKey = resolve(settings.accessKeyReference());
        char[] secretKey = null;
        try {
            secretKey = resolve(settings.secretAccessKeyReference());
            try (OssFacade facade = facades.open(settings, new String(accessKey), new String(secretKey))) {
                return operation.apply(facade);
            }
        } finally {
            Arrays.fill(accessKey, '\0');
            if (secretKey != null) Arrays.fill(secretKey, '\0');
        }
    }

    private char[] resolve(String reference) {
        try {
            char[] value = secrets.resolve(reference);
            if (value == null || value.length == 0) throw new IllegalStateException();
            return value;
        } catch (RuntimeException unavailable) {
            throw new IllegalStateException("Storage credential is unavailable", unavailable);
        }
    }

    private static String objectKey(Binding binding, UploadIntent intent) {
        if (intent.ownerId() <= 0) throw new IllegalArgumentException("owner is required");
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    intent.idempotencyKey().getBytes(StandardCharsets.UTF_8)));
            return binding.scope().name().toLowerCase(Locale.ROOT) + "/" + intent.ownerId()
                    + "/" + digest + extension(intent.fileName(), intent.contentType());
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static String extension(String fileName, String contentType) {
        int dot = fileName.lastIndexOf('.');
        if (dot > -1) {
            String candidate = fileName.substring(dot).toLowerCase(Locale.ROOT);
            if (candidate.matches("^\\.[a-z0-9]{1,10}$")) return candidate;
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "application/pdf" -> ".pdf";
            default -> ".bin";
        };
    }

    private static String etag(String value) {
        return value == null ? "" : value.strip().replace("\"", "").toLowerCase(Locale.ROOT);
    }

    interface ConfigurationSource {
        Settings current(Binding binding);
    }

    interface FacadeFactory {
        OssFacade open(Settings settings, String accessKey, String secretKey);
    }

    interface OssFacade extends AutoCloseable {
        UploadTicket signPut(
                String bucket, String key, String contentType,
                String sha256, Duration ttl, Instant now);
        Head head(String bucket, String key);
        ReadTicket signGet(String bucket, String key, Duration ttl, Instant now);
        void delete(String bucket, String key);
        MigrationPage scan(String bucket, String cursor, int limit);
        void copy(String sourceBucket, String targetBucket, String key);
        @Override void close();
    }

    record Head(String etag, long sizeBytes, String sha256) { }

    private static final class OssSdkFacade implements OssFacade {
        private final OSS client;

        private OssSdkFacade(Settings settings, String accessKey, String secretKey) {
            ClientBuilderConfiguration config = new ClientBuilderConfiguration();
            config.setSignatureVersion(SignVersion.V4);
            config.setConnectionTimeout(API_TIMEOUT_MILLIS);
            config.setSocketTimeout(API_TIMEOUT_MILLIS);
            this.client = OSSClientBuilder.create()
                    .endpoint(settings.endpoint().toString())
                    .credentialsProvider(new DefaultCredentialProvider(accessKey, secretKey))
                    .clientConfiguration(config)
                    .region(settings.region())
                    .build();
        }

        @Override
        public UploadTicket signPut(
                String bucket, String key, String contentType,
                String sha256, Duration ttl, Instant now) {
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucket, key, HttpMethod.PUT);
            request.setExpiration(Date.from(now.plus(ttl)));
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", contentType);
            request.setHeaders(headers);
            Map<String, String> metadata = new HashMap<>();
            metadata.put(SHA_METADATA, sha256);
            request.setUserMetadata(metadata);
            URI url = URI.create(client.generatePresignedUrl(request).toString());
            return new UploadTicket(key, url, now.plus(ttl), Map.of(
                    "content-type", contentType,
                    "x-oss-meta-" + SHA_METADATA, sha256));
        }

        @Override
        public Head head(String bucket, String key) {
            var metadata = client.getObjectMetadata(bucket, key);
            Object sha = metadata.getUserMetadata().get(SHA_METADATA);
            return new Head(metadata.getETag(), metadata.getContentLength(),
                    sha == null ? null : sha.toString());
        }

        @Override
        public ReadTicket signGet(String bucket, String key, Duration ttl, Instant now) {
            URI url = URI.create(client.generatePresignedUrl(
                    bucket, key, Date.from(now.plus(ttl)), HttpMethod.GET).toString());
            return new ReadTicket(url, now.plus(ttl));
        }

        @Override
        public void delete(String bucket, String key) {
            client.deleteObject(bucket, key);
        }

        @Override
        public MigrationPage scan(String bucket, String cursor, int limit) {
            var listing = client.listObjects(new ListObjectsRequest(bucket, null, cursor, null, limit));
            List<ObjectRef> objects = new ArrayList<>();
            for (OSSObjectSummary item : listing.getObjectSummaries()) {
                Head metadata = head(bucket, item.getKey());
                objects.add(new ObjectRef(owner(item.getKey()), item.getKey(), metadata.sha256()));
            }
            return new MigrationPage(
                    objects, listing.isTruncated() ? listing.getNextMarker() : null,
                    !listing.isTruncated());
        }

        @Override
        public void copy(String sourceBucket, String targetBucket, String key) {
            client.copyObject(sourceBucket, key, targetBucket, key);
        }

        @Override
        public void close() {
            client.shutdown();
        }

        private static long owner(String key) {
            String[] segments = key.split("/", 3);
            if (segments.length != 3) throw new IllegalStateException("Stored object key is invalid");
            try {
                long owner = Long.parseLong(segments[1]);
                if (owner <= 0) throw new NumberFormatException();
                return owner;
            } catch (NumberFormatException invalid) {
                throw new IllegalStateException("Stored object key is invalid", invalid);
            }
        }
    }
}
