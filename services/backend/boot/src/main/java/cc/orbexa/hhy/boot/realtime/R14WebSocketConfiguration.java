package cc.orbexa.hhy.boot.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@EnableScheduling
public class R14WebSocketConfiguration implements WebSocketConfigurer {
    private static final int MAX_PAYLOAD_BYTES = 10 * 1024 * 1024;
    private final R14WebSocketHandler handler;
    private final R14WebSocketHandshakeInterceptor handshake;

    public R14WebSocketConfiguration(
            R14WebSocketHandler handler, R14WebSocketHandshakeInterceptor handshake) {
        this.handler = handler;
        this.handshake = handshake;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(handshake)
                .setAllowedOrigins("https://h5.orbexa.cc");
    }

    @Bean
    ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_PAYLOAD_BYTES);
        container.setMaxBinaryMessageBufferSize(MAX_PAYLOAD_BYTES);
        container.setMaxSessionIdleTimeout(75_000L);
        return container;
    }
}
