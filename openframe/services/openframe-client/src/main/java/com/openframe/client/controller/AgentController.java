package com.openframe.client.controller;


import com.openframe.client.dto.agent.*;
import com.openframe.client.service.agentregistration.AgentRegistrationService;
import com.openframe.client.service.ToolConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRegistrationService agentRegistrationService;
    private final ToolConnectionService toolConnectionService;

    @PostMapping("/register")
    public ResponseEntity<AgentRegistrationResponse> register(
            @RequestHeader("X-Initial-Key") String initialKey,
            @Valid @RequestBody AgentRegistrationRequest request) {

        AgentRegistrationResponse response = agentRegistrationService.register(initialKey, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tool-connections")
    public ResponseEntity<List<ToolConnectionResponse>> getAllToolConnections() {
        return ResponseEntity.ok(toolConnectionService.getAllToolConnections());
    }

    @GetMapping("/tool-connections/{openframeAgentId}")
    public ResponseEntity<List<ToolConnectionResponse>> getToolConnectionsByMachineId(
            @PathVariable String openframeAgentId) {
        return ResponseEntity.ok(toolConnectionService.getToolConnectionsByMachineId(openframeAgentId));
    }

    @GetMapping("/tool-connections/{openframeAgentId}/{toolType}")
    public ResponseEntity<ToolConnectionResponse> getToolConnectionByMachineIdAndToolType(
            @PathVariable String openframeAgentId,
            @PathVariable String toolType) {
        return ResponseEntity.ok(toolConnectionService.getToolConnectionByMachineIdAndToolType(openframeAgentId, toolType));
    }

}