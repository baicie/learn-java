package io.aegisops.execution;

import io.aegisops.execution.dto.AgentMemoryCreateCommand;
import io.aegisops.execution.dto.AgentMemoryEventCreateCommand;
import io.aegisops.execution.dto.AgentMemoryRecord;
import java.util.List;
import java.util.Optional;

public interface AgentMemoryRepository {
  void create(AgentMemoryCreateCommand command);

  Optional<AgentMemoryRecord> find(String tenantId, String memoryId);

  List<AgentMemoryRecord> listActiveCandidates(ListActiveMemoryParams params);

  boolean archive(String tenantId, String memoryId);

  void createEvent(AgentMemoryEventCreateCommand command);
}
