package io.aegisops.inspection;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InspectionService {
  private final InspectionRepository repository;

  public InspectionService(InspectionRepository repository) {
    this.repository = repository;
  }

  public List<InspectionTaskRecord> list(String tenantId) {
    return repository.list(tenantId);
  }

  public InspectionTaskRecord get(String tenantId, String id) {
    return repository.find(tenantId, id);
  }

  public InspectionTaskRecord create(
      String tenantId, CreateInspectionTaskRequest request, String createdBy) {
    if (request == null || request.name() == null || request.name().isBlank()) {
      throw new IllegalArgumentException("inspection task name is required");
    }
    if (request.targetType() == null || request.targetType().isBlank()) {
      throw new IllegalArgumentException("inspection targetType is required");
    }
    if (request.templateKey() == null || request.templateKey().isBlank()) {
      throw new IllegalArgumentException("inspection templateKey is required");
    }
    return repository.create(tenantId, request, createdBy == null ? "system" : createdBy);
  }
}
