package com.openframe.client.service;

import com.openframe.client.dto.agent.AgentToolCollectionResponse;
import com.openframe.client.dto.agent.ToolConnectionResponse;
import com.openframe.client.exception.ConnectionNotFoundException;
import com.openframe.client.exception.InvalidAgentIdException;
import com.openframe.client.exception.InvalidToolTypeException;
import com.openframe.client.exception.MachineNotFoundException;
import com.openframe.data.document.tool.ConnectionStatus;
import com.openframe.data.document.tool.ToolConnection;
import com.openframe.data.document.tool.ToolType;
import com.openframe.data.repository.device.MachineRepository;
import com.openframe.data.repository.tool.ToolConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolConnectionService {

    private final ToolConnectionRepository toolConnectionRepository;
    private final MachineRepository machineRepository;

    public List<ToolConnectionResponse> getAllToolConnections() {
        return toolConnectionRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<ToolConnectionResponse> getToolConnectionsByMachineId(String openframeAgentId) {
        validateAgentId(openframeAgentId);
        return toolConnectionRepository.findByMachineId(openframeAgentId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public AgentToolCollectionResponse getAgentToolCollection(String openframeAgentId) {
        validateAgentId(openframeAgentId);
        List<ToolConnection> connections = toolConnectionRepository.findByMachineId(openframeAgentId);
        List<AgentToolCollectionResponse.ToolInfo> tools = connections.stream()
                .map(conn -> new AgentToolCollectionResponse.ToolInfo(
                        conn.getToolType().toString().toLowerCase(),
                        conn.getAgentToolId()))
                .collect(Collectors.toList());

        return new AgentToolCollectionResponse(openframeAgentId, tools);
    }

    public ToolConnectionResponse getToolConnectionByMachineIdAndToolType(String openframeAgentId, String agentToolType) {
        validateAgentId(openframeAgentId);
        validateToolType(agentToolType);

        ToolType toolType = getToolTypeFromString(agentToolType);
        return toolConnectionRepository.findByMachineIdAndToolType(openframeAgentId, toolType)
                .map(this::convertToResponse)
                .orElseThrow(() -> new ConnectionNotFoundException("Connection not found for machine " + openframeAgentId + " and tool type " + agentToolType));
    }

    @Transactional
    public void addToolConnection(String openframeAgentId, String agentToolType, String agentId) {
        ToolType toolType = getToolTypeFromString(agentToolType);

        toolConnectionRepository
                .findByMachineIdAndToolType(openframeAgentId, toolType)
                .ifPresentOrElse(
                        toolConnection -> processExistingToolConnection(
                                toolConnection,
                                openframeAgentId,
                                toolType,
                                agentId
                        ),
                        () -> addNewToolConnection(openframeAgentId, toolType, agentId)
                );
    }

    private void processExistingToolConnection(
            ToolConnection toolConnection,
            String openframeAgentId,
            ToolType toolType,
            String agentId
    ) {
        if (toolConnection.getStatus() == ConnectionStatus.DISCONNECTED) {
            toolConnection.setStatus(ConnectionStatus.CONNECTED);
            toolConnection.setAgentToolId(agentId);
            toolConnection.setConnectedAt(Instant.now());
            toolConnection.setDisconnectedAt(null);
            toolConnectionRepository.save(toolConnection);

            log.info("Updated existing tool connection with machineId {} tool {} agentToolId {}", openframeAgentId, toolType, agentId);
        } else {
            ConnectionStatus toolConnectionStatus = toolConnection.getStatus();
            log.warn("Tools agent already connected with machineId {} tool {} agentToolId {} status {}", openframeAgentId, toolType, agentId, toolConnectionStatus);
        }
    }

    private void addNewToolConnection(String openframeAgentId, ToolType toolType, String agentId) {
        ToolConnection connection = new ToolConnection();
        connection.setMachineId(openframeAgentId);
        connection.setToolType(toolType);
        connection.setAgentToolId(agentId);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setConnectedAt(Instant.now());
        toolConnectionRepository.save(connection);

        log.info("Saved tool connection with machineId {} tool {} agentToolId {}", openframeAgentId, toolType, agentId);
    }

    @Transactional
    public ToolConnectionResponse updateToolConnection(String openframeAgentId, String agentToolType, String agentId) {
        validateAgentId(openframeAgentId);
        validateToolType(agentToolType);
        validateAgentToolId(agentId);

        ToolType toolType = getToolTypeFromString(agentToolType);
        ToolConnection connection = toolConnectionRepository.findByMachineIdAndToolType(openframeAgentId, toolType)
                .orElseThrow(() -> new ConnectionNotFoundException("Connection not found for machine " + openframeAgentId + " and tool type " + agentToolType));

        connection.setAgentToolId(agentId);
        connection.setLastSyncAt(Instant.now());
        return convertToResponse(toolConnectionRepository.save(connection));
    }

    @Transactional
    public void deleteToolConnection(String openframeAgentId, String agentToolType) {
        validateAgentId(openframeAgentId);
        validateToolType(agentToolType);

        ToolType toolType = getToolTypeFromString(agentToolType);
        ToolConnection connection = toolConnectionRepository.findByMachineIdAndToolType(openframeAgentId, toolType)
                .orElseThrow(() -> new ConnectionNotFoundException("Connection not found for machine " + openframeAgentId + " and tool type " + agentToolType));

        connection.setStatus(ConnectionStatus.DISCONNECTED);
        connection.setDisconnectedAt(Instant.now());
        toolConnectionRepository.save(connection);
    }

    private void validateMachineExists(String machineId) {
        if (!machineRepository.findByMachineId(machineId).isPresent()) {
            throw new MachineNotFoundException("Machine not found: " + machineId);
        }
    }

    private void validateAgentToolId(String agentToolId) {
        if (agentToolId == null || agentToolId.trim().isEmpty()) {
            throw new InvalidAgentIdException("Agent tool ID cannot be empty");
        }
    }

    private void validateAgentId(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new InvalidAgentIdException("Agent ID cannot be empty");
        }
    }

    private void validateToolType(String toolType) {
        if (toolType == null || toolType.trim().isEmpty()) {
            throw new InvalidToolTypeException("Tool type cannot be empty");
        }
    }

    private ToolType getToolTypeFromString(String agentToolType) {
        try {
            return ToolType.valueOf(agentToolType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidToolTypeException("Invalid tool type: " + agentToolType);
        }
    }

    private ToolConnectionResponse convertToResponse(ToolConnection connection) {
        return new ToolConnectionResponse(
                connection.getMachineId(),
                connection.getToolType().toString().toLowerCase(),
                connection.getAgentToolId(),
                connection.getStatus().toString()
        );
    }
}