package com.openframe.client.service.agentregistration;

import com.openframe.client.dto.agent.AgentRegistrationRequest;
import com.openframe.client.dto.agent.AgentRegistrationResponse;
import com.openframe.client.service.AgentSecretGenerator;
import com.openframe.client.service.validator.AgentRegistrationSecretValidator;
import com.openframe.data.document.device.DeviceStatus;
import com.openframe.data.document.device.Machine;
import com.openframe.data.document.oauth.OAuthClient;
import com.openframe.data.repository.device.MachineRepository;
import com.openframe.data.repository.oauth.OAuthClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.openframe.client.service.AgentAuthService.CLIENT_CREDENTIALS_GRANT_TYPE;
import static java.lang.String.format;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRegistrationService {

    private static final String AGENT_ROLE = "AGENT";
    private static final String CLIENT_ID_TEMPLATE = "agent_%s";

    private final OAuthClientRepository oauthClientRepository;
    private final MachineRepository machineRepository;
    private final AgentRegistrationSecretValidator secretValidator;
    private final AgentSecretGenerator agentSecretGenerator;
    private final PasswordEncoder passwordEncoder;
    private final MachineIdGenerator machineIdGenerator;
    private final AgentRegistrationToolService agentRegistrationToolService;

    @Transactional
    // TODO: two phase commit for the nats integration or other fallback
    public AgentRegistrationResponse register(String initialKey, AgentRegistrationRequest request) {
        secretValidator.validate(initialKey);

        String machineId = machineIdGenerator.generate();
        String clientId = buildClientId(machineId);
        String clientSecret = agentSecretGenerator.generate();

        saveOAuthClient(machineId, clientId, clientSecret);
        saveMachine(machineId, request);

        agentRegistrationToolService.publishInstallationMessages(machineId);

        return new AgentRegistrationResponse(machineId, clientId, clientSecret);
    }

    private void saveOAuthClient(String machineId, String clientId, String clientSecret) {
        if (oauthClientRepository.existsByMachineId(machineId)) {
            log.error("Generated non unique machine id {}", machineId);
            throw new IllegalStateException("Failed to register client");
        }

        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientSecret(passwordEncoder.encode(clientSecret));
        client.setMachineId(machineId);
        client.setGrantTypes(new String[]{CLIENT_CREDENTIALS_GRANT_TYPE});
        client.setRoles(new String[]{AGENT_ROLE});

        oauthClientRepository.save(client);
    }

    private String buildClientId(String machineId) {
        return format(CLIENT_ID_TEMPLATE, machineId);
    }

    private void saveMachine(String machineId, AgentRegistrationRequest request) {
        Machine machine = new Machine();
        machine.setMachineId(machineId);
        machine.setHostname(request.getHostname());
        machine.setIp(request.getIp());
        machine.setMacAddress(request.getMacAddress());
        machine.setOsUuid(request.getOsUuid());
        machine.setAgentVersion(request.getAgentVersion());
        machine.setLastSeen(Instant.now());
        machine.setStatus(DeviceStatus.ACTIVE);

        machineRepository.save(machine);
    }

}
