package cc.orbexa.hhy.boot.realtime;

import cc.orbexa.hhy.access.user.UserAuthStore;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.access.user.UserTokenService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class R14WebSocketHandshakeInterceptor implements HandshakeInterceptor {
    public static final String USER_ATTRIBUTE = "hhy.websocket.user";
    public static final String SEQUENCE_ATTRIBUTE = "hhy.websocket.lastServerSequence";
    private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    private static final Pattern COMPACT_JWT = Pattern.compile(
            "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

    private final UserTokenService tokens;
    private final UserAuthStore users;
    private final Clock clock;

    public R14WebSocketHandshakeInterceptor(
            UserTokenService tokens, UserAuthStore users, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        MultiValueMap<String, String> query;
        long sequence;
        try {
            query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            if (query.containsKey("wsTicket")) return reject(response, HttpStatus.BAD_REQUEST);
            List<String> sequenceValues = query.getOrDefault("lastServerSequence", List.of());
            if (sequenceValues.size() > 1) return reject(response, HttpStatus.BAD_REQUEST);
            sequence = sequenceValues.isEmpty() ? 0 : Long.parseLong(sequenceValues.getFirst());
            if (sequence < 0) return reject(response, HttpStatus.BAD_REQUEST);
        } catch (RuntimeException exception) {
            return reject(response, HttpStatus.BAD_REQUEST);
        }
        try {
            List<String> protocols = new ArrayList<>();
            for (String header : request.getHeaders().getOrEmpty(PROTOCOL_HEADER)) {
                for (String value : header.split(",", -1)) protocols.add(value.trim());
            }
            if (protocols.size() != 2 || protocols.stream().filter("hhy.v1"::equals).count() != 1) {
                return reject(response, HttpStatus.UNAUTHORIZED);
            }
            List<String> accessProtocols = protocols.stream()
                    .filter(value -> value.startsWith("hhy.access.")).toList();
            if (accessProtocols.size() != 1) return reject(response, HttpStatus.UNAUTHORIZED);
            String compact = accessProtocols.getFirst().substring("hhy.access.".length());
            if (!COMPACT_JWT.matcher(compact).matches()) {
                return reject(response, HttpStatus.UNAUTHORIZED);
            }
            UserPrincipal principal = users.authenticate(tokens.parseAccess(compact), Instant.now(clock))
                    .orElse(null);
            if (principal == null || !"ACTIVE".equals(principal.userStatus())) {
                return reject(response, HttpStatus.UNAUTHORIZED);
            }
            attributes.put(USER_ATTRIBUTE, principal);
            attributes.put(SEQUENCE_ATTRIBUTE, sequence);
            return true;
        } catch (RuntimeException exception) {
            return reject(response, HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) {
        // The protocol credential is deliberately never copied into logs or attributes.
    }

    private static boolean reject(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return false;
    }
}
