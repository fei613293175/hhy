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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** Cloudflare R2 transport using the S3-compatible AWS SDK without persisting credentials. */
public final class R04S3StorageTransport implements StorageProviderAdapter.StorageTransport {
    private static final Duration API_TIMEOUT = Duration.ofSeconds(15);
    private static final String SHA_METADATA = "sha256";
    private final ConfigurationSource configuration;
    private final SecretResolver secrets;
    private final Clock clock;
    private final FacadeFactory facades;

    public R04S3StorageTransport(
            ConfigurationSource configuration, SecretResolver secrets, Clock clock) {
        this(configuration, secrets, clock, AwsSdkFacade::new);
    }

    R04S3StorageTransport(
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
        String objectKey = objectKey(binding, intent);
        return withFacade(settings, facade -> facade.signPut(
                settings.bucket(), objectKey, intent.contentType(), intent.sizeBytes(),
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

    private <T> T withFacade(Settings settings, Function<S3Facade, T> operation) {
        char[] accessKey = resolve(settings.accessKeyReference());
        char[] secretKey = null;
        try {
            secretKey = resolve(settings.secretAccessKeyReference());
            try (S3Facade facade = facades.open(settings, new String(accessKey), new String(secretKey))) {
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
        String digest;
        try {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    intent.idempotencyKey().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
        return binding.scope().name().toLowerCase(Locale.ROOT) + "/" + intent.ownerId()
                + "/" + digest + extension(intent.fileName(), intent.contentType());
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
        S3Facade open(Settings settings, String accessKey, String secretKey);
    }

    interface S3Facade extends AutoCloseable {
        UploadTicket signPut(
                String bucket, String key, String contentType, long sizeBytes,
                String sha256, Duration ttl, Instant now);
        Head head(String bucket, String key);
        ReadTicket signGet(String bucket, String key, Duration ttl, Instant now);
        void delete(String bucket, String key);
        MigrationPage scan(String bucket, String cursor, int limit);
        void copy(String sourceBucket, String targetBucket, String key);
        @Override void close();
    }

    record Head(String etag, long sizeBytes, String sha256) { }

    private static final class AwsSdkFacade implements S3Facade {
        private final S3Client client;
        private final S3Presigner presigner;

        private AwsSdkFacade(Settings settings, String accessKey, String secretKey) {
            var credentials = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey));
            var s3 = S3Configuration.builder().pathStyleAccessEnabled(true).build();
            var override = ClientOverrideConfiguration.builder()
                    .apiCallTimeout(API_TIMEOUT).apiCallAttemptTimeout(API_TIMEOUT).build();
            this.client = S3Client.builder()
                    .endpointOverride(settings.endpoint()).region(Region.of(settings.region()))
                    .credentialsProvider(credentials).serviceConfiguration(s3)
                    .overrideConfiguration(override)
                    .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
            this.presigner = S3Presigner.builder()
                    .endpointOverride(settings.endpoint()).region(Region.of(settings.region()))
                    .credentialsProvider(credentials).serviceConfiguration(s3).build();
        }

        @Override
        public UploadTicket signPut(
                String bucket, String key, String contentType, long sizeBytes,
                String sha256, Duration ttl, Instant now) {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(bucket).key(key).contentType(contentType).contentLength(sizeBytes)
                    .metadata(Map.of(SHA_METADATA, sha256)).build();
            PresignedPutObjectRequest signed = presigner.presignPutObject(
                    PutObjectPresignRequest.builder().signatureDuration(ttl)
                            .putObjectRequest(put).build());
            Map<String, String> headers = signedHeaders(signed.signedHeaders());
            return new UploadTicket(key, URI.create(signed.url().toString()), now.plus(ttl), headers);
        }

        @Override
        public Head head(String bucket, String key) {
            var response = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return new Head(response.eTag(), response.contentLength(), response.metadata().get(SHA_METADATA));
        }

        @Override
        public ReadTicket signGet(String bucket, String key, Duration ttl, Instant now) {
            PresignedGetObjectRequest signed = presigner.presignGetObject(
                    GetObjectPresignRequest.builder().signatureDuration(ttl)
                            .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                            .build());
            return new ReadTicket(URI.create(signed.url().toString()), now.plus(ttl));
        }

        @Override
        public void delete(String bucket, String key) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        }

        @Override
        public MigrationPage scan(String bucket, String cursor, int limit) {
            var response = client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket).continuationToken(cursor).maxKeys(limit).build());
            List<ObjectRef> objects = new ArrayList<>();
            response.contents().forEach(item -> {
                Head metadata = head(bucket, item.key());
                objects.add(new ObjectRef(owner(item.key()), item.key(), metadata.sha256()));
            });
            boolean complete = !Boolean.TRUE.equals(response.isTruncated());
            return new MigrationPage(objects, complete ? null : response.nextContinuationToken(), complete);
        }

        @Override
        public void copy(String sourceBucket, String targetBucket, String key) {
            client.copyObject(CopyObjectRequest.builder().sourceBucket(sourceBucket)
                    .sourceKey(key).destinationBucket(targetBucket).destinationKey(key).build());
        }

        @Override
        public void close() {
            presigner.close();
            client.close();
        }

        private static Map<String, String> signedHeaders(Map<String, List<String>> signed) {
            Map<String, String> safe = new LinkedHashMap<>();
            signed.forEach((name, values) -> {
                String normalized = name.toLowerCase(Locale.ROOT);
                if ((normalized.equals("content-type") || normalized.startsWith("x-amz-meta-"))
                        && values != null && values.size() == 1) {
                    safe.put(normalized, values.getFirst());
                }
            });
            return Map.copyOf(safe);
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
