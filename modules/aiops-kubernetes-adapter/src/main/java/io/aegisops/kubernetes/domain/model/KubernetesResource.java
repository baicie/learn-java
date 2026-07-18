package io.aegisops.kubernetes.domain.model;

import java.util.List;
import java.util.Map;

public record KubernetesResource(
    String kind,
    String uid,
    String name,
    String namespace,
    Map<String, Object> labels,
    List<String> ownerUids,
    String providerId,
    String machineId,
    String ip,
    Map<String, Object> rawPayload) {
  public KubernetesResource {
    labels = labels == null ? Map.of() : Map.copyOf(labels);
    ownerUids = ownerUids == null ? List.of() : List.copyOf(ownerUids);
    rawPayload = rawPayload == null ? Map.of() : Map.copyOf(rawPayload);
  }
}
