package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.domain.model.AiGeneration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AiGenerationRepository {
  AiGeneration create(CreateGeneration command);

  Optional<AiGeneration> find(String tenantId, String id);

  Optional<AiGeneration> findReusable(
      String tenantId, String type, String resourceType, String resourceId, String inputHash);

  List<AiGeneration> listByResource(String tenantId, String resourceType, String resourceId);

  boolean markRunning(String tenantId, String id);

  boolean complete(String tenantId, String id, String markdown, String provider, String model);

  boolean markRetrying(String tenantId, String id);

  boolean fail(String tenantId, String id);

  boolean review(String tenantId, String id, String targetStatus, String reviewerId);

  record CreateGeneration(
      String id,
      String tenantId,
      String generationType,
      String resourceType,
      String resourceId,
      LocalDate periodStart,
      LocalDate periodEnd,
      String promptVersion,
      String inputHash,
      String inputJson,
      String requestedBy) {}
}
