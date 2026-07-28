package cc.orbexa.hhy.boot.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.R14RealtimeService;
import cc.orbexa.hhy.content.R14Service;
import cc.orbexa.hhy.content.R14Store;
import cc.orbexa.hhy.access.user.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class R14WebSocketHandlerTest {
    @Test
    void advertisesOnlyFrozenApplicationProtocol() {
        assertEquals(java.util.List.of("hhy.v1"), handler().getSubProtocols());
    }

    @Test
    void connectionWithoutHandshakePrincipalClosesBeforeAnyResume() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler().afterConnectionEstablished(session);

        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(status.capture());
        assertEquals(4401, status.getValue().getCode());
    }

    @Test
    void typingCommandRequiresExplicitBooleanValue() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        var attributes = new HashMap<String, Object>();
        attributes.put(R14WebSocketHandshakeInterceptor.USER_ATTRIBUTE,
                new UserPrincipal(11, 22, 0, "jti", "ACTIVE"));
        when(session.getAttributes()).thenReturn(attributes);

        handler().handleMessage(session, new TextMessage("""
                {"eventId":"7a903f84-9695-4b02-9d0c-f212855bbb75",
                 "eventType":"chat.typing","occurredAt":"2026-07-28T04:00:00Z",
                 "payload":{"conversationId":"42","userId":"11",
                            "expiresAt":"2026-07-28T04:00:05Z"}}
                """));

        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(status.capture());
        assertEquals(4400, status.getValue().getCode());
    }

    @Test
    void pingReturnsPongWithoutCreatingPersistentDelivery() throws Exception {
        WebSocketSession session = authenticatedSession();
        R14WebSocketSessionRegistry sessions = mock(R14WebSocketSessionRegistry.class);
        when(sessions.send(eq(session), contains("\"eventType\":\"system.pong\""))).thenReturn(true);
        R14WebSocketHandler handler = new R14WebSocketHandler(
                mock(R14RealtimeService.class), mock(R14Service.class), mock(R14Store.class),
                sessions, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());

        handler.handleMessage(session, new TextMessage("""
                {"eventId":"7a903f84-9695-4b02-9d0c-f212855bbb75",
                 "eventType":"system.ping","occurredAt":"2026-07-28T04:00:00Z",
                 "payload":{"clientTime":"2026-07-28T04:00:00Z"}}
                """));

        verify(sessions).send(eq(session), contains("\"eventType\":\"system.pong\""));
    }

    @Test
    void typingRequiresLiveConversationMembership() throws Exception {
        WebSocketSession session = authenticatedSession();
        R14Store store = mock(R14Store.class);
        when(store.membership(42, 11, false)).thenReturn(Optional.empty());
        R14WebSocketHandler handler = new R14WebSocketHandler(
                mock(R14RealtimeService.class), mock(R14Service.class), store,
                mock(R14WebSocketSessionRegistry.class),
                new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());

        handler.handleMessage(session, new TextMessage("""
                {"eventId":"7a903f84-9695-4b02-9d0c-f212855bbb75",
                 "eventType":"chat.typing","occurredAt":"2026-07-28T04:00:00Z",
                 "payload":{"conversationId":"42","userId":"11","typing":true,
                            "expiresAt":"2026-07-28T04:00:05Z"}}
                """));

        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(status.capture());
        assertEquals(4403, status.getValue().getCode());
    }

    private static R14WebSocketHandler handler() {
        return new R14WebSocketHandler(
                mock(R14RealtimeService.class), mock(R14Service.class), mock(R14Store.class),
                mock(R14WebSocketSessionRegistry.class),
                new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());
    }

    private static WebSocketSession authenticatedSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        var attributes = new HashMap<String, Object>();
        attributes.put(R14WebSocketHandshakeInterceptor.USER_ATTRIBUTE,
                new UserPrincipal(11, 22, 0, "jti", "ACTIVE"));
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
