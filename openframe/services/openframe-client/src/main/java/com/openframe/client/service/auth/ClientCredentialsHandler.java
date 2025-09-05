package com.openframe.client.service.auth;

import com.openframe.client.dto.AgentTokenResponse;
import com.openframe.data.document.oauth.OAuthClient;
import com.openframe.data.repository.oauth.OAuthClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import static com.openframe.client.service.AgentAuthService.CLIENT_CREDENTIALS_GRANT_TYPE;
import static org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames.TOKEN_TYPE;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClientCredentialsHandler {

    private final OAuthClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenGenerator accessTokenGenerator;
    private final RefreshTokenGenerator refreshTokenGenerator;

    public AgentTokenResponse handle(String clientId, String clientSecret) {
        OAuthClient client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> {
                    log.error("Client not found: {}", clientId);
                    return new IllegalArgumentException("Client not found");
                });

        validateClientSecret(client, clientSecret);

        String accessToken = accessTokenGenerator.generate(client, CLIENT_CREDENTIALS_GRANT_TYPE);
        String refreshToken = refreshTokenGenerator.generate(client.getClientId());
        long accessTokenExpirationSeconds = accessTokenGenerator.getExpirationSeconds();

        return new AgentTokenResponse(
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                accessTokenExpirationSeconds
        );
    }

    private void validateClientSecret(OAuthClient client, String clientSecret) {
        String clientId = client.getClientId();
        if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
            log.debug("Client secret validation failed for client: {} ", clientId);
            throw new IllegalArgumentException("Invalid client secret");
        }
        log.debug("Client secret validation passed for client: " + clientId);
    }

}
