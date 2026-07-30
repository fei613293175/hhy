package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.identity.ProviderFaceImageDownloader.Response;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProviderFaceImageDownloaderTest {
    private static final URI FACE = URI.create(
            "https://provider-images.example/evidence/face.jpg?token=opaque");

    @Test
    void downloadsBoundedJpegAndReturnsStableHash() throws Exception {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01, 0x02};
        RecordingTransport transport = new RecordingTransport(
                new Response(200, "image/jpeg; charset=binary", jpeg));
        var downloader = downloader(transport);

        var result = downloader.download(FACE);

        assertEquals("image/jpeg", result.contentType());
        assertArrayEquals(jpeg, result.bytes());
        assertEquals("055dfd15d4e238ff12497b7ec3680a797e1cb6a77871a42859cc763013b07d01",
                result.sha256());
        assertEquals(FACE, transport.source);
        assertEquals(Duration.ofSeconds(8), transport.timeout);
        assertEquals(100 * 1024, transport.maximumBytes);
    }

    @Test
    void rejectsLocalResolutionBeforeStartingTransport() throws Exception {
        boolean[] called = {false};
        var downloader = new ProviderFaceImageDownloader(
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")},
                (source, timeout, maximumBytes) -> {
                    called[0] = true;
                    return null;
                });

        BusinessException failure = assertThrows(BusinessException.class,
                () -> downloader.download(FACE));

        assertEquals(500, failure.httpStatus());
        assertTrue(failure.retryable());
        assertFalse(called[0]);
    }

    @Test
    void rejectsRedirectOversizeAndContentTypeSpoofing() {
        BusinessException redirect = assertThrows(BusinessException.class,
                () -> downloader(new RecordingTransport(
                        new Response(302, "image/jpeg", new byte[]{1}))).download(FACE));
        BusinessException oversize = assertThrows(BusinessException.class,
                () -> downloader(new RecordingTransport(
                        new Response(200, "image/jpeg", new byte[100 * 1024 + 1]))).download(FACE));
        BusinessException spoofed = assertThrows(BusinessException.class,
                () -> downloader(new RecordingTransport(
                        new Response(200, "image/png", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})))
                        .download(FACE));

        assertEquals(500, redirect.httpStatus());
        assertEquals(500, oversize.httpStatus());
        assertEquals(500, spoofed.httpStatus());
    }

    @Test
    void acceptsPngSignatureAndReturnsDefensiveCopies() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        var result = downloader(new RecordingTransport(
                new Response(200, "image/png", png))).download(FACE);

        byte[] first = result.bytes();
        first[0] = 0;
        assertEquals((byte) 0x89, result.bytes()[0]);
    }

    private static ProviderFaceImageDownloader downloader(RecordingTransport transport) {
        return new ProviderFaceImageDownloader(
                host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})},
                transport);
    }

    private static final class RecordingTransport implements ProviderFaceImageDownloader.Transport {
        private final Response response;
        private URI source;
        private Duration timeout;
        private int maximumBytes;

        private RecordingTransport(Response response) {
            this.response = response;
        }

        @Override
        public Response get(URI source, Duration timeout, int maximumBytes) {
            this.source = source;
            this.timeout = timeout;
            this.maximumBytes = maximumBytes;
            return response;
        }
    }
}
