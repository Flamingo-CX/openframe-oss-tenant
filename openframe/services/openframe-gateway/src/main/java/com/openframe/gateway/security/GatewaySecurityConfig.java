package com.openframe.gateway.security;

import com.openframe.gateway.security.filter.AddAuthorizationHeaderFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import static com.openframe.gateway.config.ws.WebSocketGatewayConfig.NATS_WS_ENDPOINT_PATH;
import static com.openframe.gateway.security.SecurityConstants.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({ManagementServerProperties.class, ServerProperties.class})
@RequiredArgsConstructor
@Slf4j
public class GatewaySecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String AGENT = "AGENT";

    @Bean
    public ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthoritiesClaimName("scope");
        scopesConverter.setAuthorityPrefix("SCOPE_");

        ReactiveJwtAuthenticationConverter jwtAuthenticationConverter = new ReactiveJwtAuthenticationConverter();

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Flux<GrantedAuthority> roles = Flux.fromIterable(rolesConverter.convert(jwt));
            Flux<GrantedAuthority> scopes = Flux.fromIterable(scopesConverter.convert(jwt));
            return Flux.concat(roles, scopes);
        });

        jwtAuthenticationConverter.setPrincipalClaimName("sub");

        return jwtAuthenticationConverter;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            @Value("${management.endpoints.web.base-path}") String managementBasePath,
            ReactiveAuthenticationManagerResolver<ServerWebExchange> issuerResolver,
            AddAuthorizationHeaderFilter addAuthorizationHeaderFilter
    ) {
        String managementContextPath = isNotBlank(managementBasePath)
                ? managementBasePath: "/actuator";

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(issuerResolver)
                )
                .addFilterBefore(addAuthorizationHeaderFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS,    "/**").permitAll()
                        .pathMatchers(
                                "/error/**",
                                "/health/**",
                                CLIENTS_PREFIX + "/metrics/**",
                                CLIENTS_PREFIX + "/api/agents/register",
                                CLIENTS_PREFIX + "/oauth/token",
                                DASHBOARD_PREFIX + "/sso/providers",
                                managementContextPath + "/**",
                                // TODO: remove after migration artifacts to GitHub
                                CLIENTS_PREFIX + "/tool-agent/**"
                        ).permitAll()
                        // Api service
                            .pathMatchers(DASHBOARD_PREFIX + "/**").hasRole(ADMIN)
                        // Agent tools
                        .pathMatchers(TOOLS_PREFIX + "/agent/**").hasRole(AGENT)
                        .pathMatchers(WS_TOOLS_PREFIX + "/agent/**").hasRole(AGENT)
                        // Agent nats
                        .pathMatchers(NATS_WS_ENDPOINT_PATH).hasRole("AGENT")
                        // Client service
                        .pathMatchers(CLIENTS_PREFIX + "/**").hasRole(AGENT)
                        // Api tools
                        .pathMatchers(TOOLS_PREFIX + "/**").hasRole(ADMIN)
                        .pathMatchers(WS_TOOLS_PREFIX + "/**").hasRole(ADMIN)
                        // UI
                        .pathMatchers("/**").permitAll()
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.addAllowedOriginPattern("http://localhost:*");
        configuration.addAllowedOriginPattern("https://localhost:*");
        configuration.addAllowedOriginPattern("http://localhost");
        configuration.addAllowedOriginPattern("https://localhost");
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        configuration.setMaxAge(3600L);
        configuration.addExposedHeader("Authorization");
        configuration.addExposedHeader("Content-Type");
        configuration.addExposedHeader(ACCESS_TOKEN_HEADER);
        configuration.addExposedHeader(REFRESH_TOKEN_HEADER);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
