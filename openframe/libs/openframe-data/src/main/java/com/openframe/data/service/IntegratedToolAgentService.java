package com.openframe.data.service;

import com.openframe.core.model.IntegratedToolAgent;
import com.openframe.core.model.ToolAgentStatus;
import com.openframe.data.repository.mongo.IntegratedToolAgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegratedToolAgentService {

    private final IntegratedToolAgentRepository agentRepository;

    public IntegratedToolAgent save(IntegratedToolAgent agent) {
        return agentRepository.save(agent);
    }

    public List<IntegratedToolAgent> getAll() {
        return agentRepository.findAll();
    }

    public List<IntegratedToolAgent> getAllEnabled() {
        return agentRepository.findByStatus(ToolAgentStatus.ENABLED);
    }
} 