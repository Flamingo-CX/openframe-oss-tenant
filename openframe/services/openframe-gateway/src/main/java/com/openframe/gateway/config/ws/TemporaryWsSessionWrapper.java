package com.openframe.gateway.config.ws;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;

@Slf4j
@RequiredArgsConstructor
public class TemporaryWsSessionWrapper implements WebSocketSession {

    @Delegate
    private final WebSocketSession delegate;

    @Override
    public Flux<WebSocketMessage> receive() {
        return delegate.receive()
                .map(message -> {
                    String payload = message.getPayloadAsText();
                    log.info("Payload: " + payload);
                    return message;
                });
    }
}
