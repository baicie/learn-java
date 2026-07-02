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

  public EvidenceCollectResponse collect(
      String tenantId, String incidentId, EvidenceCollectRequest request) {
    validateTenantAndIncident(tenantId, incidentId);

    EvidenceCollectRequest normalizedRequest = normalizeRequest(request);
    String collectorKey = normalizedRequest.collectorKey();
    String taskId =
        taskRepository.start(tenantId, incidentId, collectorKey, write(normalizedRequest));

    try {
      EvidenceCollector collector =
          registry.getSupported(collectorKey, normalizedRequest);
      EvidenceCollectResponse response =
          collector.collect(tenantId, incidentId, normalizedRequest);
      taskRepository.complete(tenantId, taskId, write(response));
      return response;
    } catch (RuntimeException error) {
      taskRepository.fail(tenantId, taskId, safeMessage(error));
      throw error;
    }
  }

  private EvidenceCollectRequest normalizeRequest(EvidenceCollectRequest request) {
    if (request == null) {
      return new EvidenceCollectRequest(null, null, null, DEFAULT_COLLECTOR);
    }

    String collectorKey =
        request.collectorKey() == null || request.collectorKey().isBlank()
            ? DEFAULT_COLLECTOR
            : request.collectorKey().trim();

    return new EvidenceCollectRequest(
        request.lookbackMinutes(), request.timeFrom(), request.timeTo(), collectorKey);
  }

  private void validateTenantAndIncident(String tenantId, String incidentId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenant id is required");
    }
    if (incidentId == null || incidentId.isBlank()) {
      throw new IllegalArgumentException("incident id is required");
    }
  }

  private String safeMessage(RuntimeException error) {
    if (error.getMessage() == null || error.getMessage().isBlank()) {
      return error.getClass().getSimpleName();
    }
    return error.getMessage();
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception e) {
      throw new IllegalArgumentException("json serialization failed", e);
    }
  }
}
