package com.openframe.client.service;

import com.openframe.client.dto.agent.AgentRegistrationRequest;
import com.openframe.client.dto.agent.AgentRegistrationResponse;
import com.openframe.client.service.agentregistration.AgentRegistrationService;
import com.openframe.client.service.agentregistration.AgentRegistrationToolService;
import com.openframe.client.service.agentregistration.MachineIdGenerator;
import com.openframe.client.service.validator.AgentRegistrationSecretValidator;
import com.openframe.data.document.device.DeviceStatus;
import com.openframe.data.document.device.Machine;
import com.openframe.data.document.oauth.OAuthClient;
import com.openframe.data.repository.device.MachineRepository;
import com.openframe.data.repository.oauth.OAuthClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentRegistrationServiceTest {

    @Mock
    private OAuthClientRepository oauthClientRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private AgentRegistrationSecretValidator agentRegistrationSecretValidator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AgentSecretGenerator agentSecretGenerator;

    @Mock
    private MachineIdGenerator machineIdGenerator;

    @Mock
    private AgentRegistrationToolService agentRegistrationToolService;

    @Captor
    private ArgumentCaptor<OAuthClient> oauthClientCaptor;

    @Captor
    private ArgumentCaptor<Machine> machineCaptor;

    private AgentRegistrationService agentRegistrationService;
    private AgentRegistrationRequest request;
    private static final String INITIAL_KEY = "test-initial-key";
    private static final String MACHINE_ID = "test-machine-id";
    private static final String CLIENT_SECRET = "01234567890123456789012345678912";

    @BeforeEach
    void setUp() {
        agentRegistrationService = new AgentRegistrationService(oauthClientRepository, machineRepository, agentRegistrationSecretValidator, agentSecretGenerator, passwordEncoder, machineIdGenerator, agentRegistrationToolService);
        request = createTestRequest();
    }

    @Test
    void registerAgent_WithNewMachine_ReturnsCredentials() {
        when(machineIdGenerator.generate()).thenReturn(MACHINE_ID);
        when(oauthClientRepository.existsByMachineId(MACHINE_ID)).thenReturn(false);
        when(agentSecretGenerator.generate()).thenReturn(CLIENT_SECRET);
        when(passwordEncoder.encode(CLIENT_SECRET)).thenReturn("encoded-secret");
        when(oauthClientRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        when(machineRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        
        AgentRegistrationResponse response = agentRegistrationService.register(INITIAL_KEY, request);
        
        assertNotNull(response);
        assertEquals(MACHINE_ID, response.getMachineId());
        assertEquals("agent_" + MACHINE_ID, response.getClientId());
        assertEquals(CLIENT_SECRET, response.getClientSecret());

        verify(agentRegistrationSecretValidator).validate(INITIAL_KEY);
        verify(machineIdGenerator).generate();
        verify(oauthClientRepository).existsByMachineId(MACHINE_ID);
        verify(agentSecretGenerator).generate();
        verify(passwordEncoder).encode(CLIENT_SECRET);

        verify(oauthClientRepository).save(oauthClientCaptor.capture());
        OAuthClient savedClient = oauthClientCaptor.getValue();
        assertEquals(MACHINE_ID, savedClient.getMachineId());
        assertEquals("agent_" + MACHINE_ID, savedClient.getClientId());
        assertEquals("encoded-secret", savedClient.getClientSecret());
        assertArrayEquals(new String[]{"client_credentials"}, savedClient.getGrantTypes());
        assertArrayEquals(new String[]{"AGENT"}, savedClient.getRoles());

        verify(machineRepository).save(machineCaptor.capture());
        Machine savedMachine = machineCaptor.getValue();
        assertEquals(MACHINE_ID, savedMachine.getMachineId());
        assertEquals("test-hostname", savedMachine.getHostname());
        assertEquals("192.168.1.1", savedMachine.getIp());
        assertEquals("00:11:22:33:44:55", savedMachine.getMacAddress());
        assertEquals("test-os-uuid", savedMachine.getOsUuid());
        assertEquals("1.0.0", savedMachine.getAgentVersion());
        assertEquals(DeviceStatus.ACTIVE, savedMachine.getStatus());
        assertNotNull(savedMachine.getLastSeen());
    }

    @Test
    void registerAgent_WithExistingMachine_ThrowsException() {
        when(machineIdGenerator.generate()).thenReturn(MACHINE_ID);
        when(oauthClientRepository.existsByMachineId(MACHINE_ID)).thenReturn(true);
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> agentRegistrationService.register(INITIAL_KEY, request)
        );
        assertEquals("Failed to register client", exception.getMessage());

        verify(agentRegistrationSecretValidator).validate(INITIAL_KEY);
        verify(machineIdGenerator).generate();
        verify(oauthClientRepository).existsByMachineId(MACHINE_ID);
        verify(oauthClientRepository, never()).save(any());
        verify(machineRepository, never()).save(any());
    }

    private AgentRegistrationRequest createTestRequest() {
        AgentRegistrationRequest request = new AgentRegistrationRequest();
        request.setHostname("test-hostname");
        request.setIp("192.168.1.1");
        request.setMacAddress("00:11:22:33:44:55");
        request.setOsUuid("test-os-uuid");
        request.setAgentVersion("1.0.0");
        return request;
    }
} 