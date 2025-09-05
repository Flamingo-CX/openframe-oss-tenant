package com.openframe.management.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.openframe.core.model.IntegratedToolAgent;
import com.openframe.data.service.IntegratedToolAgentService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v1/tool-agents")
@RequiredArgsConstructor
public class IntegratedToolAgentController {

    private final IntegratedToolAgentService toolAgentService;

    @Data
    public static class SaveToolAgentRequest {
        private IntegratedToolAgent toolAgent;
    }

    @PostMapping("/{id}")
    public Map<String, Object> saveAgent(
            @PathVariable String id,
            @RequestBody SaveToolAgentRequest request) {
        try {
            IntegratedToolAgent toolAgent = request.getToolAgent();
            toolAgent.setId(id);

            IntegratedToolAgent savedToolAgent = toolAgentService.save(toolAgent);
            log.info("Successfully saved agent configuration for: {}", id);
            return Map.of("status", "success", "agent", savedToolAgent);
        } catch (Exception e) {
            log.error("Failed to save agent: {}", id, e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
} 