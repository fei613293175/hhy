package cc.orbexa.hhy.boot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;

class AdminClientIpResolverTest {
    @Test
    void ignoresForwardedHeaderFromUntrustedDirectPeer() {
        var resolver = new AdminClientIpResolver("10.0.0.0/8,127.0.0.1/32");
        var request = request("203.0.113.9", "198.51.100.7, 10.0.0.3");

        assertEquals("203.0.113.9", resolver.resolve(request));
    }

    @Test
    void walksTrustedProxyChainFromRightAndReturnsFirstUntrustedClient() {
        var resolver = new AdminClientIpResolver("10.0.0.0/8,127.0.0.1/32");
        var request = request("10.0.0.2", "198.51.100.7, 10.0.0.3, 10.0.0.4");

        assertEquals("198.51.100.7", resolver.resolve(request));
    }

    @ParameterizedTest
    @MethodSource("malformedForwardedChains")
    void fallsBackToTrustedPeerForMalformedOrOversizedForwardedChain(String forwarded) {
        var resolver = new AdminClientIpResolver("10.0.0.0/8");
        var request = request("10.0.0.2", forwarded);

        assertEquals("10.0.0.2", resolver.resolve(request));
    }

    @Test
    void returnsUnknownWhenDirectPeerAddressIsMalformed() {
        var resolver = new AdminClientIpResolver("10.0.0.0/8");
        var request = request("not-an-ip", "198.51.100.7");

        assertEquals("unknown", resolver.resolve(request));
    }

    private static Stream<String> malformedForwardedChains() {
        return Stream.of(
                "198.51.100.7,,10.0.0.3",
                "198.51.100.7:443,10.0.0.3",
                "client.example.test,10.0.0.3",
                String.join(",", Collections.nCopies(17, "10.0.0.3")),
                "1".repeat(1025));
    }

    private static MockHttpServletRequest request(String remote, String forwarded) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
