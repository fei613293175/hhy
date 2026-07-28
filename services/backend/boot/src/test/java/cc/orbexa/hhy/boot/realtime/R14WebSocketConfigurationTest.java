package cc.orbexa.hhy.boot.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.websocket.server.ServerContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

class R14WebSocketConfigurationTest {
    private static final int TEN_MEBIBYTES = 10 * 1024 * 1024;

    @Test
    void defaultConfigurationCreatesProductionContainerLimits() {
        R14WebSocketHandler handler = mock(R14WebSocketHandler.class);
        R14WebSocketHandshakeInterceptor handshake = mock(R14WebSocketHandshakeInterceptor.class);

        runner(handler, handshake)
                .withInitializer(context -> {
                    MockServletContext servletContext = new MockServletContext();
                    servletContext.setAttribute(
                            "jakarta.websocket.server.ServerContainer",
                            mock(ServerContainer.class));
                    context.setServletContext(servletContext);
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ServletServerContainerFactoryBean container =
                            context.getBean("&webSocketContainer", ServletServerContainerFactoryBean.class);
                    assertThat(container.getMaxTextMessageBufferSize()).isEqualTo(TEN_MEBIBYTES);
                    assertThat(container.getMaxBinaryMessageBufferSize()).isEqualTo(TEN_MEBIBYTES);
                    assertThat(container.getMaxSessionIdleTimeout()).isEqualTo(75_000L);
                });
    }

    @Test
    void disabledContainerStillRegistersWebSocketRouteHandlerAndHandshake() {
        R14WebSocketHandler handler = mock(R14WebSocketHandler.class);
        R14WebSocketHandshakeInterceptor handshake = mock(R14WebSocketHandshakeInterceptor.class);

        runner(handler, handshake)
                .withPropertyValues("hhy.websocket.container.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("&webSocketContainer");

                    WebSocketHandlerMapping mapping = context.getBean(WebSocketHandlerMapping.class);
                    assertThat(mapping.getHandlerMap()).containsKey("/ws");
                    Object route = mapping.getHandlerMap().get("/ws");
                    assertThat(route).isInstanceOf(WebSocketHttpRequestHandler.class);
                    WebSocketHttpRequestHandler requestHandler = (WebSocketHttpRequestHandler) route;
                    assertThat(WebSocketHandlerDecorator.unwrap(requestHandler.getWebSocketHandler()))
                            .isSameAs(handler);
                    assertThat(requestHandler.getHandshakeInterceptors()).contains(handshake);
                });
    }

    private static WebApplicationContextRunner runner(
            R14WebSocketHandler handler, R14WebSocketHandshakeInterceptor handshake) {
        return new WebApplicationContextRunner()
                .withBean(R14WebSocketHandler.class, () -> handler)
                .withBean(R14WebSocketHandshakeInterceptor.class, () -> handshake)
                .withUserConfiguration(R14WebSocketConfiguration.class);
    }
}
