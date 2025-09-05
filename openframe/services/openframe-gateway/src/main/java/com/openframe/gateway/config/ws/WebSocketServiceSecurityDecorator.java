package com.openframe.gateway.config.ws;

import com.openframe.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static com.openframe.gateway.config.ws.WebSocketGatewayConfig.*;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@RequiredArgsConstructor
@Slf4j
// TODO: remove session wrapper after testing
public class WebSocketServiceSecurityDecorator implements WebSocketService {

    private final WebSocketService defaultWebSocketService;
    private final JwtService jwtService;

    @Override
    public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler defaultWebSocketHandler) {
        String path = exchange.getRequest().getPath().value();

        if (isSecuredEndpoint(path)) {
            return defaultWebSocketService.handleRequest(exchange, session -> {
                Jwt jwt = getRequestJwt(exchange);
                Instant expiresAt = jwt.getExpiresAt();
                long secondsUntilExpiration = Duration.between(Instant.now(), expiresAt).getSeconds();
                Disposable disposable = scheduleSessionRemoveJob(session, secondsUntilExpiration);
                processSessionClosedEvent(session, disposable);
                return defaultWebSocketHandler.handle(new TemporaryWsSessionWrapper(session));
            });
        } else {
            return defaultWebSocketService.handleRequest(exchange, defaultWebSocketHandler);
        }
    }

    /* TODO: avoid hardcoded paths.
        Relay on spring security to verify that endpoint is available with token or no token.
        Then if request have token, we should run session remove job etc.
    */
    private boolean isSecuredEndpoint(String path) {
        return Set.of(TOOLS_API_WS_ENDPOINT_PREFIX, TOOLS_AGENT_WS_ENDPOINT_PREFIX, NATS_WS_ENDPOINT_PATH)
                .stream()
                .anyMatch(path::startsWith);
    }

    private boolean isNatsEndpoint(String path) {
        return path.equals(NATS_WS_ENDPOINT_PATH);
    }

    private Jwt getRequestJwt(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String jwt = getRequestToken(request);
        return jwtService.decodeToken(jwt);
    }

    private String getRequestToken(ServerHttpRequest request) {
        String authorisation = extractAuthorisation(request);
        if (!authorisation.startsWith("Bearer ")) {
            throw new IllegalStateException("No bearer token found");
        }
        return authorisation.substring(7);
    }

    private String extractAuthorisation(ServerHttpRequest request) {
        String authorisationHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (isNotEmpty(authorisationHeader)) {
            return authorisationHeader;
        }
        throw new IllegalStateException("No authorization data found");
    }

    private Disposable scheduleSessionRemoveJob(WebSocketSession session, long secondsUntilExpiration) {
        String sessionId = session.getId();
        log.info("Scheduling session remove job: {} - {} seconds", sessionId, secondsUntilExpiration);
        return Mono.delay(Duration.ofSeconds(secondsUntilExpiration))
                .flatMap(__ -> {
                    log.info("Executing session remove job: {}", sessionId);
                    return session.close()
                            .doOnSuccess(___ -> log.info("Closed session: {}", sessionId))
                            .doOnError(ex -> log.error("Failed to close session {}", sessionId, ex));
                })
                .subscribe();
    }

    private void processSessionClosedEvent(WebSocketSession session, Disposable disposable) {
        session.closeStatus()
                .subscribe(status -> {
                    log.info("Session {} has been closed with status {}", session.getId(), status);
                    log.info("Cancelling session remove job: {}", session.getId());
                    disposable.dispose();
                    log.info("Cancelled session remove job: {}", session.getId());
                });
    }
}
