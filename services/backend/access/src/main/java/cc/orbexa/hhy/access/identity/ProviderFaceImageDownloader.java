package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.shared.api.BusinessException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Downloads provider evidence into bounded memory without following redirects or local-network URLs. */
public final class ProviderFaceImageDownloader {
    static final int MAX_IMAGE_BYTES = 100 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private final HostResolver resolver;
    private final Transport transport;

    public ProviderFaceImageDownloader() {
        this(InetAddress::getAllByName, new JdkTransport());
    }

    ProviderFaceImageDownloader(HostResolver resolver, Transport transport) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public DownloadedFace download(URI source) {
        requirePublicHttps(source);
        try {
            Response response = transport.get(source, TIMEOUT, MAX_IMAGE_BYTES);
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300
                    || response.body() == null || response.body().length == 0
                    || response.body().length > MAX_IMAGE_BYTES) {
                throw unavailable();
            }
            String contentType = normalizedContentType(response.contentType());
            if (!("image/jpeg".equals(contentType) && jpeg(response.body()))
                    && !("image/png".equals(contentType) && png(response.body()))) {
                throw unavailable();
            }
            String sha256 = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(response.body()));
            return new DownloadedFace(contentType, response.body(), sha256);
        } catch (BusinessException known) {
            throw known;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception failure) {
            throw unavailable();
        }
    }

    private void requirePublicHttps(URI source) {
        try {
            if (source == null || !"https".equalsIgnoreCase(source.getScheme())
                    || source.getHost() == null || source.getUserInfo() != null
                    || source.getFragment() != null || source.getPort() != -1) {
                throw unavailable();
            }
            String host = source.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".localhost")
                    || host.endsWith(".local") || host.endsWith(".internal")) {
                throw unavailable();
            }
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses == null || addresses.length == 0) throw unavailable();
            for (InetAddress address : addresses) {
                if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw unavailable();
                }
            }
        } catch (BusinessException unsafe) {
            throw unsafe;
        } catch (Exception unsafe) {
            throw unavailable();
        }
    }

    private static String normalizedContentType(String value) {
        if (value == null) throw unavailable();
        return value.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    }

    private static boolean jpeg(byte[] value) {
        return value.length >= 3 && (value[0] & 0xff) == 0xff
                && (value[1] & 0xff) == 0xd8 && (value[2] & 0xff) == 0xff;
    }

    private static boolean png(byte[] value) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (value.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (value[index] != signature[index]) return false;
        }
        return true;
    }

    private static BusinessException unavailable() {
        return new BusinessException(
                "COMMON-500-INTERNAL", "实名认证服务暂时不可用", 500, true);
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws Exception;
    }

    interface Transport {
        Response get(URI source, Duration timeout, int maximumBytes) throws Exception;
    }

    record Response(int statusCode, String contentType, byte[] body) { }

    public record DownloadedFace(String contentType, byte[] bytes, String sha256) {
        public DownloadedFace {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private static final class JdkTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        @Override
        public Response get(URI source, Duration timeout, int maximumBytes) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(source)
                    .timeout(timeout)
                    .header("Accept", "image/jpeg,image/png")
                    .header("User-Agent", "HHY-R05-Identity/1")
                    .GET().build();
            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                return new Response(
                        response.statusCode(),
                        response.headers().firstValue("Content-Type").orElse(null),
                        input.readNBytes(maximumBytes + 1));
            }
        }
    }
}
