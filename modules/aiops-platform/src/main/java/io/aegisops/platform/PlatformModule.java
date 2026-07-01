package io.aegisops.platform;

import java.time.OffsetDateTime;

public record PlatformModule(
    String id,
    String moduleId,
    String name,
    String version,
    boolean enabled,
    String healthStatus,
    String configJson,
    OffsetDateTime createdAt) {

  public PlatformModule markHealthy() {
    return new PlatformModule(
        id, moduleId, name, version, enabled, "HEALTHY", configJson, createdAt);
  }

  public PlatformModule markUnhealthy() {
    return new PlatformModule(
        id, moduleId, name, version, enabled, "UNHEALTHY", configJson, createdAt);
  }
}
