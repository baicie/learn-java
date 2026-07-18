package io.aegisops.kubernetes.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.kubernetes.domain.model.KubernetesResource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KubernetesInventoryParser {
  private final ObjectMapper objectMapper;

  public KubernetesInventoryParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<KubernetesResource> parse(String kind, String json) {
    try {
      List<KubernetesResource> resources = new ArrayList<>();
      for (JsonNode item : objectMapper.readTree(json).path("items")) {
        JsonNode metadata = item.path("metadata");
        String uid = text(metadata, "uid");
        String name = text(metadata, "name");
        if (uid == null || name == null) {
          continue;
        }
        resources.add(
            new KubernetesResource(
                kind,
                uid,
                name,
                text(metadata, "namespace"),
                objectMap(metadata.path("labels")),
                ownerUids(metadata.path("ownerReferences")),
                text(item.path("spec"), "providerID"),
                machineId(item),
                address(item, kind),
                objectMap(item)));
      }
      return List.copyOf(resources);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid Kubernetes inventory response", exception);
    }
  }

  private List<String> ownerUids(JsonNode owners) {
    List<String> result = new ArrayList<>();
    owners.forEach(
        owner -> {
          String uid = text(owner, "uid");
          if (uid != null) result.add(uid);
        });
    return result;
  }

  private String machineId(JsonNode item) {
    String value = text(item.path("status").path("nodeInfo"), "machineID");
    return value == null ? label(item, "node.kubernetes.io/machine-id") : value;
  }

  private String address(JsonNode item, String kind) {
    if ("Service".equals(kind)) {
      return text(item.path("spec"), "clusterIP");
    }
    for (JsonNode address : item.path("status").path("addresses")) {
      if ("InternalIP".equals(text(address, "type"))) return text(address, "address");
    }
    return text(item.path("status"), "podIP");
  }

  private String label(JsonNode item, String name) {
    return text(item.path("metadata").path("labels"), name);
  }

  private String text(JsonNode node, String field) {
    String value = node.path(field).asText("").trim();
    return value.isEmpty() ? null : value;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> objectMap(JsonNode node) {
    if (!node.isObject()) return Map.of();
    Map<String, Object> result = new LinkedHashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    fields.forEachRemaining(
        entry ->
            result.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
    return result;
  }
}
