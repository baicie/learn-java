package io.aegisops.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EvidenceOrchestrationService {
  private static final String DEFAULT_COLLECTOR = "zabbix.metric-event";

  private final EvidenceCollectorRegistry registry;
  private final EvidenceCollectionTaskRepository taskRepository;
  private final ObjectMapper objectMapper;

  public EvidenceOrchestrationService(
      EvidenceCollectorRegistry registry,
      EvidenceCollectionTaskRepository taskRepository,
      ObjectMapper objectMapper) {
    this.registry = registry;
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
  }

  public EvidenceCollectResponse collect(String tenantId, String incidentId, EvidenceCollectRequest request) {
    String collectorKey = collectorKey(request);
    String taskId = taskRepository.start(tenantId, incidentId, collectorKey, write(request));
    try {
      EvidenceCollectResponse response = registry.get(collectorKey).collect(tenantId, incidentId, request);
      taskRepository.complete(tenantId, taskId, write(response));
      return response;
    } catch (RuntimeException error) {
      taskRepository.fail(tenantId, taskId, error.getMessage());
      throw error;
    }
  }

  private String collectorKey(EvidenceCollectRequest request) {
    if (request == null || request.collectorKey() == null || request.collectorKey().isBlank()) {
      return DEFAULT_COLLECTOR;
    }
    return request.collectorKey();
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception e) {
      throw new IllegalArgumentException("json serialization failed", e);
    }
  }
}
