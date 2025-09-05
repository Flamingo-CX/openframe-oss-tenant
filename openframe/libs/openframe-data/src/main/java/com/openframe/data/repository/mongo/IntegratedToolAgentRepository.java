package com.openframe.data.repository.mongo;

import com.openframe.core.model.IntegratedToolAgent;
import com.openframe.core.model.ToolAgentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IntegratedToolAgentRepository extends MongoRepository<IntegratedToolAgent, String> {

    List<IntegratedToolAgent> findByStatus(ToolAgentStatus status);

} 