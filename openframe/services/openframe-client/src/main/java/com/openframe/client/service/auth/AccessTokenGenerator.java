package com.openframe.client.service.auth;

import com.openframe.data.document.oauth.OAuthClient;
import com.openframe.security.jwt.JwtService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AccessTokenGenerator {

    private static final String TOKEN_TYPE = "Bearer";

    private final JwtService jwtService;
    @Getter
    private final int expirationSeconds;

    public AccessTokenGenerator(
            JwtService jwtService,
            @Value("${security.oauth2.token.access.expiration-seconds}") int expirationSeconds
    ) {
        this.jwtService = jwtService;
        this.expirationSeconds = expirationSeconds;
    }

    public String generate(OAuthClient client, String grantType) {
        JwtClaimsSet claims = buildClaims(client, grantType);
        return jwtService.generateToken(claims);
    }

    private JwtClaimsSet buildClaims(OAuthClient client, String grantType) {
        return JwtClaimsSet.builder()
                .issuer("https://auth.openframe.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(expirationSeconds))
                .subject(client.getClientId())
                .claim("machine_id", client.getMachineId())
                .claim("grant_type", grantType)
                .claim("roles", client.getRoles())
                .build();
    }

}
