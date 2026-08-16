package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationRequest;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.AiGeneration;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Centralizes record-visibility and tenant-wide-read checks for AI generation results. */
@Component
public class AiGenerationAccessGuard {
  private final WorkRecordQueryService records;
  private final WorkRecordPermissionService permissions;
  private final ObjectMapper objectMapper;

  public AiGenerationAccessGuard(
      WorkRecordQueryService records,
      WorkRecordPermissionService permissions,
      ObjectMapper objectMapper) {
    this.records = records;
    this.permissions = permissions;
    this.objectMapper = objectMapper;
  }

  public WorkRecord visibleRecord(String tenantId, String recordId, UserPrincipal principal) {
    return records.get(tenantId, recordId, principal);
  }

  public void requireTenantWideRead(UserPrincipal principal) {
    if (!permissions.canReadAll(principal)) {
      throw new AccessDeniedException("tenant-wide AI generation requires read-all permission");
    }
  }

  public void requireResourceRead(AiGeneration generation, UserPrincipal principal) {
    if ("record".equals(generation.resourceType())) {
      records.get(generation.tenantId(), generation.resourceId(), principal);
    } else if ("tenant_week".equals(generation.resourceType())
        || "tenant_month".equals(generation.resourceType())) {
      requireTenantWideRead(principal);
    } else {
      throw new IllegalArgumentException("unsupported AI generation resource type");
    }
  }

  public void requireInputReadable(AiGeneration generation, UserPrincipal principal) {
    WorkRecordGenerationRequest input;
    try {
      input = objectMapper.readValue(generation.inputJson(), WorkRecordGenerationRequest.class);
    } catch (Exception ex) {
      throw new AccessDeniedException("AI generation input is not readable", ex);
    }
    if (!generation.tenantId().equals(input.tenantId())) {
      throw new AccessDeniedException("AI generation input tenant mismatch");
    }
    for (WorkRecordGenerationRequest.RecordItem item : input.records()) {
      if (item.id() == null || item.id().isBlank()) {
        throw new AccessDeniedException("AI generation input contains an invalid record");
      }
      var visible = records.get(generation.tenantId(), item.id(), principal);
      ObjectNode visibleFields = readableFields(visible.customDataJson());
      ObjectNode inputFields = objectMapper.valueToTree(item.fields());
      var fields = inputFields.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        if (!visibleFields.has(field.getKey())
            || !Objects.equals(visibleFields.get(field.getKey()), field.getValue())) {
          throw new AccessDeniedException(
              "AI generation contains fields that are not currently readable");
        }
      }
    }
  }

  private ObjectNode readableFields(String customDataJson) {
    try {
      var node = objectMapper.readTree(customDataJson);
      if (node == null || !node.isObject()) {
        throw new AccessDeniedException("work-record fields are not readable");
      }
      return (ObjectNode) node;
    } catch (AccessDeniedException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AccessDeniedException("work-record fields are not readable", ex);
    }
  }
}
