package com.openframe.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openframe.client.exception.*;
import com.openframe.client.service.agentregistration.AgentRegistrationService;
import com.openframe.client.util.TestAuthenticationManager;
import com.openframe.client.dto.agent.*;
import com.openframe.client.service.ToolConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AgentRegistrationService agentRegistrationService;

    @Mock
    private ToolConnectionService toolConnectionService;

    private static final String OPENFRAME_AGENT_ID = "test-agent-id";
    private static final String TOOL_TYPE = "test-tool-type";
    private static final String AGENT_TOOL_ID = "test-remote-agent-id";

    private AgentRegistrationRequest registrationRequest;
    private AgentRegistrationResponse registrationResponse;
    private ToolConnectionRequest toolConnectionRequest;
    private ToolConnectionResponse toolConnectionResponse;
    private ToolConnectionUpdateRequest toolConnectionUpdateRequest;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        AgentController controller = new AgentController(agentRegistrationService, toolConnectionService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .addFilter(new BasicAuthenticationFilter(new TestAuthenticationManager()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        setupTestData();
    }

    private void setupTestData() {
        registrationRequest = new AgentRegistrationRequest();
        registrationRequest.setHostname("test-host");
        registrationRequest.setIp("192.168.1.1");
        registrationRequest.setMacAddress("00:11:22:33:44:55");
        registrationRequest.setOsUuid("test-os-uuid");
        registrationRequest.setAgentVersion("1.0.0");
        registrationResponse = new AgentRegistrationResponse("test-machine-id", "client-id", "client-secret");

        toolConnectionRequest = new ToolConnectionRequest();
        toolConnectionRequest.setOpenframeAgentId(OPENFRAME_AGENT_ID);
        toolConnectionRequest.setToolType(TOOL_TYPE);
        toolConnectionRequest.setAgentToolId(AGENT_TOOL_ID);

        toolConnectionUpdateRequest = new ToolConnectionUpdateRequest();
        toolConnectionUpdateRequest.setAgentToolId(AGENT_TOOL_ID);

        toolConnectionResponse = new ToolConnectionResponse();
        toolConnectionResponse.setOpenframeAgentId(OPENFRAME_AGENT_ID);
        toolConnectionResponse.setToolType(TOOL_TYPE);
        toolConnectionResponse.setAgentToolId(AGENT_TOOL_ID);
        toolConnectionResponse.setStatus("CONNECTED");
    }

    @Test
    void register_WithValidRequest_ReturnsOk() throws Exception {
        when(agentRegistrationService.register(any(), any())).thenReturn(registrationResponse);

        mockMvc.perform(post("/api/agents/register")
                        .header("X-Initial-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineId").value("test-machine-id"))
                .andExpect(jsonPath("$.clientId").value("client-id"))
                .andExpect(jsonPath("$.clientSecret").value("client-secret"));
    }

    @Test
    @WithAnonymousUser
    void anonymousAccess_ReturnsUnauthorized() throws Exception {
        when(toolConnectionService.getAllToolConnections())
                .thenThrow(new AccessDeniedException("Access is denied"));

        mockMvc.perform(get("/api/agents/tool-connections"))
                .andExpect(status().isUnauthorized()); // Returns 403 FORBIDDEN
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCanAccessToolConnections() throws Exception {
        when(toolConnectionService.getAllToolConnections())
                .thenReturn(Arrays.asList(toolConnectionResponse));

        mockMvc.perform(get("/api/agents/tool-connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].openframeAgentId").value(OPENFRAME_AGENT_ID))
                .andExpect(jsonPath("$[0].toolType").value(TOOL_TYPE))
                .andExpect(jsonPath("$[0].agentToolId").value(AGENT_TOOL_ID))
                .andExpect(jsonPath("$[0].status").value("CONNECTED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getToolConnectionsByMachineId_WithUserRole_ReturnsOk() throws Exception {
        when(toolConnectionService.getToolConnectionsByMachineId(OPENFRAME_AGENT_ID))
                .thenReturn(Arrays.asList(toolConnectionResponse));

        mockMvc.perform(get("/api/agents/tool-connections/{openframeAgentId}", OPENFRAME_AGENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].openframeAgentId").value(OPENFRAME_AGENT_ID))
                .andExpect(jsonPath("$[0].toolType").value(TOOL_TYPE))
                .andExpect(jsonPath("$[0].status").value("CONNECTED"));
    }

    @Test
    @WithMockUser
    void getToolConnectionsByMachineId_WhenEmpty_ReturnsEmptyArray() throws Exception {
        when(toolConnectionService.getToolConnectionsByMachineId(OPENFRAME_AGENT_ID))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/agents/tool-connections/{openframeAgentId}", OPENFRAME_AGENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void register_MissingHeader_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Required header 'X-Initial-Key' is missing"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void accessWithUserRole_Succeeds() throws Exception {
        when(toolConnectionService.getAllToolConnections())
                .thenReturn(Arrays.asList(toolConnectionResponse));

        mockMvc.perform(get("/api/agents/tool-connections"))
                .andExpect(status().isOk());
    }

    @Test
    void register_WithoutInitialKey_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Required header 'X-Initial-Key' is missing"));
    }

    @Test
    void register_WithInvalidInitialKey_ReturnsUnauthorized() throws Exception {
        when(agentRegistrationService.register(any(String.class), any(AgentRegistrationRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid initial key"));

        mockMvc.perform(post("/api/agents/register")
                        .header("X-Initial-Key", "invalid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid initial key"));
    }

    @Test
    void register_WithDuplicateMachineId_ReturnsConflict() throws Exception {
        when(agentRegistrationService.register(eq("test-key"), any(AgentRegistrationRequest.class)))
                .thenThrow(new DuplicateConnectionException("Machine already registered"));

        mockMvc.perform(post("/api/agents/register")
                        .header("X-Initial-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"))
                .andExpect(jsonPath("$.message").value("Machine already registered"));
    }

    @Test
    void register_WithValidRequest_ReturnsCredentials() throws Exception {
        when(agentRegistrationService.register(eq("test-key"), any(AgentRegistrationRequest.class)))
                .thenReturn(registrationResponse);

        mockMvc.perform(post("/api/agents/register")
                        .header("X-Initial-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("client-id"))
                .andExpect(jsonPath("$.clientSecret").value("client-secret"));
    }

    @Test
    void register_WithValidRequest_StoresAgentInfo() throws Exception {
        when(agentRegistrationService.register(eq("test-key"), any(AgentRegistrationRequest.class)))
                .thenReturn(registrationResponse);

        mockMvc.perform(post("/api/agents/register")
                        .header("X-Initial-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isOk());

        verify(agentRegistrationService).register(
                eq("test-key"),
                argThat(request ->
                                request.getHostname().equals("test-host") &&
                                request.getIp().equals("192.168.1.1") &&
                                request.getMacAddress().equals("00:11:22:33:44:55") &&
                                request.getOsUuid().equals("test-os-uuid") &&
                                request.getAgentVersion().equals("1.0.0")
                )
        );
    }

    @Test
    @WithMockUser(roles = "USER")
    void getToolConnectionByMachineIdAndToolType_WhenExists_ReturnsOk() throws Exception {
        when(toolConnectionService.getToolConnectionByMachineIdAndToolType(OPENFRAME_AGENT_ID, TOOL_TYPE))
                .thenReturn(toolConnectionResponse);

        mockMvc.perform(get("/api/agents/tool-connections/{openframeAgentId}/{toolType}",
                        OPENFRAME_AGENT_ID, TOOL_TYPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openframeAgentId").value(OPENFRAME_AGENT_ID))
                .andExpect(jsonPath("$.toolType").value(TOOL_TYPE))
                .andExpect(jsonPath("$.agentToolId").value(AGENT_TOOL_ID))
                .andExpect(jsonPath("$.status").value("CONNECTED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getToolConnectionByMachineIdAndToolType_WhenNotFound_Returns404() throws Exception {
        when(toolConnectionService.getToolConnectionByMachineIdAndToolType(OPENFRAME_AGENT_ID, TOOL_TYPE))
                .thenThrow(new ConnectionNotFoundException("Connection not found"));

        mockMvc.perform(get("/api/agents/tool-connections/{openframeAgentId}/{toolType}",
                        OPENFRAME_AGENT_ID, TOOL_TYPE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message").value("Connection not found"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getToolConnectionByMachineIdAndToolType_WithInvalidAgentId_ReturnsBadRequest() throws Exception {
        when(toolConnectionService.getToolConnectionByMachineIdAndToolType(eq("invalid-id"), any()))
                .thenThrow(new InvalidAgentIdException("Invalid agent ID"));

        mockMvc.perform(get("/api/agents/tool-connections/{openframeAgentId}/{toolType}",
                        "invalid-id", TOOL_TYPE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Invalid agent ID"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getToolConnectionByMachineIdAndToolType_WithInvalidToolType_ReturnsBadRequest() throws Exception {
        when(toolConnectionService.getToolConnectionByMachineIdAndToolType(any(), eq("invalid-type")))
                .thenThrow(new InvalidToolTypeException("Invalid tool type: invalid-type"));

        mockMvc.perform(get("/api/agents/tool-connections/{openframeAgentId}/{toolType}",
                        OPENFRAME_AGENT_ID, "invalid-type"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Invalid tool type: invalid-type"));
    }

    @Test
    @WithAnonymousUser
    void getToolConnectionByMachineIdAndToolType_WithoutAuth_ReturnsUnauthorized() throws Exception {
        when(toolConnectionService.getToolConnectionByMachineIdAndToolType(any(), any()))
                .thenThrow(new AccessDeniedException("Access is denied"));

        mockMvc.perform(get("/api/agents/tool-connections/{openframeAgentId}/{toolType}",
                        OPENFRAME_AGENT_ID, TOOL_TYPE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getMachineNotFound_Returns404() throws Exception {
        when(toolConnectionService.getToolConnectionByMachineIdAndToolType(eq("non-existent"), any()))
                .thenThrow(new MachineNotFoundException("Machine not found: non-existent"));

        mockMvc.perform(get("/api/agents/tool-connections/{openframeAgentId}/{toolType}",
                        "non-existent", TOOL_TYPE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message").value("Machine not found: non-existent"));
    }
}