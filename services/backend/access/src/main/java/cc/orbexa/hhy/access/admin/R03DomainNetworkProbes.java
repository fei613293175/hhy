package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.DnsProbe;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.ProbeOutcome;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.ServiceHealthProbe;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.ServiceKind;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.ServiceTarget;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.TlsProbe;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.stereotype.Component;

/** Real, bounded DNS/TLS/service probes that persist only safe categorical codes. */
@Component
public final class R03DomainNetworkProbes implements DnsProbe, TlsProbe, ServiceHealthProbe {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public ProbeOutcome resolve(String hostname) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            return addresses.length == 0
                    ? ProbeOutcome.failed("DNS_NO_RECORD") : ProbeOutcome.passed("DNS_OK");
        } catch (Exception ignored) {
            return ProbeOutcome.failed("DNS_UNRESOLVED");
        }
    }

    @Override
    public ProbeOutcome handshake(String hostname) {
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault()
                .createSocket(hostname, 443)) {
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            return ProbeOutcome.passed("TLS_OK");
        } catch (Exception ignored) {
            return ProbeOutcome.failed("TLS_HANDSHAKE_FAILED");
        }
    }

    @Override
    public ProbeOutcome check(ServiceTarget target) {
        if (target.kind() == ServiceKind.WEBSOCKET) return websocket(target);
        try {
            URI uri = new URI("https", target.hostname(), target.healthPath(), null);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", "HHY-R03-Domain-Health/1")
                    .GET().build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 400
                    ? ProbeOutcome.passed("HTTP_HEALTHY")
                    : ProbeOutcome.failed("HTTP_STATUS_UNHEALTHY");
        } catch (Exception ignored) {
            return ProbeOutcome.failed("HTTP_UNREACHABLE");
        }
    }

    private ProbeOutcome websocket(ServiceTarget target) {
        WebSocket socket = null;
        try {
            URI uri = new URI("wss", target.hostname(), target.healthPath(), null);
            socket = client.newWebSocketBuilder().connectTimeout(TIMEOUT)
                    .buildAsync(uri, new WebSocket.Listener() { })
                    .get(TIMEOUT.toSeconds() + 1, TimeUnit.SECONDS);
            return ProbeOutcome.passed("WEBSOCKET_HANDSHAKE_OK");
        } catch (Exception ignored) {
            return ProbeOutcome.failed("WEBSOCKET_HANDSHAKE_FAILED");
        } finally {
            if (socket != null) socket.abort();
        }
    }
}
