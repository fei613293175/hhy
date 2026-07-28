package cc.orbexa.hhy.boot.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.user.UserAuthStore;
import cc.orbexa.hhy.access.user.UserAuthProperties;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.access.user.UserTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class R14WebSocketHandshakeInterceptorTest {
    private static final String TOKEN = "aaa.bbb.ccc";

    @Test
    void acceptsExactlyTwoProtocolsAfterLiveSessionValidation() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T04:00:00Z"), ZoneOffset.UTC);
        UserTokenService tokens = tokens(clock);
        UserAuthStore users = mock(UserAuthStore.class);
        String token = tokens.issueAccess(11, 22, 0, "jti",
                Instant.parse("2026-07-28T05:00:00Z"));
        var claims = tokens.parseAccess(token);
        when(users.authenticate(claims, Instant.parse("2026-07-28T04:00:00Z")))
                .thenReturn(Optional.of(new UserPrincipal(11, 22, 0, "jti", "ACTIVE")));
        R14WebSocketHandshakeInterceptor interceptor = new R14WebSocketHandshakeInterceptor(
                tokens, users, clock);
        ServerHttpRequest request = request("wss://ws.orbexa.cc/ws?lastServerSequence=7",
                "hhy.v1, hhy.access." + token);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        var attributes = new HashMap<String, Object>();

        assertTrue(interceptor.beforeHandshake(
                request, response, mock(WebSocketHandler.class), attributes));
        assertEquals(7L, attributes.get(R14WebSocketHandshakeInterceptor.SEQUENCE_ATTRIBUTE));
        assertEquals(11L, ((UserPrincipal) attributes.get(
                R14WebSocketHandshakeInterceptor.USER_ATTRIBUTE)).userId());
    }

    @Test
    void rejectsDuplicateAuthenticationProtocolWithoutEchoingCredential() {
        Clock clock = Clock.systemUTC();
        R14WebSocketHandshakeInterceptor interceptor = new R14WebSocketHandshakeInterceptor(
                tokens(clock), mock(UserAuthStore.class), clock);
        ServerHttpRequest request = request("wss://ws.orbexa.cc/ws",
                "hhy.v1, hhy.access." + TOKEN + ", hhy.access." + TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        assertFalse(interceptor.beforeHandshake(
                request, response, mock(WebSocketHandler.class), new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsMalformedSequenceAndQueryCredentialAsBadRequests() {
        Clock clock = Clock.systemUTC();
        R14WebSocketHandshakeInterceptor interceptor = new R14WebSocketHandshakeInterceptor(
                tokens(clock), mock(UserAuthStore.class), clock);
        for (String uri : java.util.List.of(
                "wss://ws.orbexa.cc/ws?lastServerSequence=not-a-number",
                "wss://ws.orbexa.cc/ws?lastServerSequence=-1",
                "wss://ws.orbexa.cc/ws?lastServerSequence=1&lastServerSequence=2",
                "wss://ws.orbexa.cc/ws?wsTicket=credential")) {
            ServerHttpResponse response = mock(ServerHttpResponse.class);
            assertFalse(interceptor.beforeHandshake(
                    request(uri, "hhy.v1, hhy.access." + TOKEN), response,
                    mock(WebSocketHandler.class), new HashMap<>()));
            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void rejectsMissingPaddedOrWhitespaceContaminatedCredentialProtocols() {
        Clock clock = Clock.systemUTC();
        R14WebSocketHandshakeInterceptor interceptor = new R14WebSocketHandshakeInterceptor(
                tokens(clock), mock(UserAuthStore.class), clock);
        for (String protocol : java.util.List.of(
                "hhy.v1",
                "hhy.v1, hhy.access.aaa.bbb.ccc=",
                "hhy.v1, hhy.access. aaa.bbb.ccc",
                "hhy.v1, hhy.v1")) {
            ServerHttpResponse response = mock(ServerHttpResponse.class);
            assertFalse(interceptor.beforeHandshake(
                    request("wss://ws.orbexa.cc/ws", protocol), response,
                    mock(WebSocketHandler.class), new HashMap<>()));
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }
    }

    private static UserTokenService tokens(Clock clock) {
        UserAuthProperties properties = new UserAuthProperties(
                "jwt-secret-at-least-thirty-two-characters",
                "token-hmac-secret-at-least-thirty-two-characters",
                "snapshot-root-secret-at-least-thirty-two-characters",
                "hhy-test", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofHours(24));
        return new UserTokenService(new ObjectMapper().findAndRegisterModules(), properties, clock);
    }

    private static ServerHttpRequest request(String uri, String protocol) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", protocol);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }
}
