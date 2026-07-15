package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.FieldPolicyRepository;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.FieldPolicy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class FieldPolicyService {
  private final FieldPolicyRepository repository;
  private final WorkRecordFieldIndexRepository fields;
  private final ObjectMapper objectMapper;

  public FieldPolicyService(
      FieldPolicyRepository repository,
      WorkRecordFieldIndexRepository fields,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.fields = fields;
    this.objectMapper = objectMapper;
  }

  public List<FieldPolicy> list(String tenantId, String versionId, UserPrincipal principal) {
    requireManage(tenantId, principal);
    return repository.listByVersion(tenantId, versionId);
  }

  public void replace(
      String tenantId, String versionId, List<FieldPolicy> requested, UserPrincipal principal) {
    requireManage(tenantId, principal);
    List<FieldPolicy> policies = requested == null ? List.of() : List.copyOf(requested);
    Set<String> known = new HashSet<>();
    fields.listByVersion(tenantId, versionId).forEach(field -> known.add(field.fieldCode()));
    Set<String> seen = new HashSet<>();
    for (FieldPolicy policy : policies) {
      if (policy == null || !versionId.equals(policy.templateVersionId())) {
        throw new IllegalArgumentException("field policy version mismatch");
      }
      if (!known.contains(policy.fieldCode())) {
        throw new IllegalArgumentException("unknown field: " + policy.fieldCode());
      }
      if (!seen.add(policy.fieldCode())) {
        throw new IllegalArgumentException("duplicate field policy: " + policy.fieldCode());
      }
    }
    repository.replaceForVersion(tenantId, versionId, policies);
  }

  public String filterReadableJson(
      String tenantId, String versionId, String json, UserPrincipal principal) {
    ObjectNode source = object(json);
    ObjectNode result = objectMapper.createObjectNode();
    Map<String, FieldPolicy> policies = policies(tenantId, versionId);
    source
        .fields()
        .forEachRemaining(
            entry -> {
              FieldPolicy policy = policies.get(entry.getKey());
              if (canRead(policy, principal)) {
                result.set(entry.getKey(), mask(entry.getValue(), policy));
              }
            });
    return write(result);
  }

  public String filterAuditSnapshot(
      String tenantId, String versionId, String json, UserPrincipal principal) {
    ObjectNode snapshot = object(json);
    JsonNode customData = snapshot.get("customData");
    if (customData != null && customData.isObject()) {
      snapshot.set(
          "customData",
          object(filterReadableJson(tenantId, versionId, customData.toString(), principal)));
    }
    return write(snapshot);
  }

  public void requireWritablePatch(
      String tenantId,
      String versionId,
      String beforeJson,
      String afterJson,
      UserPrincipal principal) {
    ObjectNode before = object(beforeJson);
    ObjectNode after = object(afterJson);
    Map<String, FieldPolicy> policies = policies(tenantId, versionId);
    Set<String> keys = new HashSet<>();
    before.fieldNames().forEachRemaining(keys::add);
    after.fieldNames().forEachRemaining(keys::add);
    for (String key : keys) {
      if (!Objects.equals(before.get(key), after.get(key))
          && !canWrite(policies.get(key), principal)) {
        throw new AccessDeniedException("not allowed to write field: " + key);
      }
    }
  }

  public boolean canReadField(
      String tenantId, String versionId, String fieldCode, UserPrincipal principal) {
    return canRead(policies(tenantId, versionId).get(fieldCode), principal);
  }

  private Map<String, FieldPolicy> policies(String tenantId, String versionId) {
    Map<String, FieldPolicy> result = new HashMap<>();
    repository
        .listByVersion(tenantId, versionId)
        .forEach(value -> result.put(value.fieldCode(), value));
    return result;
  }

  private static boolean canRead(FieldPolicy policy, UserPrincipal principal) {
    return policy == null
        || policy.readRoles().isEmpty()
        || principal != null && principal.roles().stream().anyMatch(policy.readRoles()::contains);
  }

  private static boolean canWrite(FieldPolicy policy, UserPrincipal principal) {
    return policy == null
        || policy.writeRoles().isEmpty()
        || principal != null && principal.roles().stream().anyMatch(policy.writeRoles()::contains);
  }

  private static void requireManage(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(
            io.aegisops.security.PermissionCodes.WORK_RECORD_TEMPLATE_WRITE)) {
      throw new AccessDeniedException("not allowed to manage field policies");
    }
  }

  private JsonNode mask(JsonNode value, FieldPolicy policy) {
    if (policy == null || policy.maskMode() == FieldPolicy.MaskMode.NONE) return value;
    if (policy.maskMode() == FieldPolicy.MaskMode.FULL)
      return objectMapper.getNodeFactory().textNode("******");
    if (!value.isTextual() || value.asText().length() <= 4)
      return objectMapper.getNodeFactory().textNode("****");
    String text = value.asText();
    return objectMapper
        .getNodeFactory()
        .textNode(text.substring(0, 2) + "****" + text.substring(text.length() - 2));
  }

  private ObjectNode object(String json) {
    try {
      JsonNode node = objectMapper.readTree(json == null ? "{}" : json);
      if (!node.isObject()) throw new IllegalArgumentException("custom data must be object");
      return (ObjectNode) node;
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid custom data", ex);
    }
  }

  private String write(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("cannot serialize filtered fields", ex);
    }
  }
}
