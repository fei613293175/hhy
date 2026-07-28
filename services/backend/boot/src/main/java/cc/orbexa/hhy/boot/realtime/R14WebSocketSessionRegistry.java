package cc.orbexa.hhy.boot.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class R14WebSocketSessionRegistry {
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(long userId, WebSocketSession session) {
        Set<WebSocketSession> current = sessions.get(userId);
        if (current == null) return;
        current.remove(session);
        if (current.isEmpty()) sessions.remove(userId, current);
    }

    public int sendToUser(long userId, String payload) {
        Set<WebSocketSession> current = sessions.getOrDefault(userId, Set.of());
        int delivered = 0;
        for (WebSocketSession session : current) {
            if (send(session, payload)) delivered++;
        }
        return delivered;
    }

    public boolean send(WebSocketSession session, String payload) {
        if (!session.isOpen()) return false;
        try {
            synchronized (session) {
                if (!session.isOpen()) return false;
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
