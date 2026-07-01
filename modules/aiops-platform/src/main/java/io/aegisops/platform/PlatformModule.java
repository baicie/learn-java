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

  public PlatformModule {
    if (moduleId == null || moduleId.isBlank()) {
      throw new IllegalArgumentException("moduleId is required");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("module name is required");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("module version is required");
    }
    if (healthStatus == null || healthStatus.isBlank()) {
      healthStatus = "UNKNOWN";
    }
    if (configJson == null || configJson.isBlank()) {
      configJson = "{}";
    }
  }

  public PlatformModule markHealthy() {
    return new PlatformModule(
        id, moduleId, name, version, enabled, "HEALTHY", configJson, createdAt);
  }

  public PlatformModule markUnhealthy() {
    return new PlatformModule(
        id, moduleId, name, version, enabled, "UNHEALTHY", configJson, createdAt);
  }
}
