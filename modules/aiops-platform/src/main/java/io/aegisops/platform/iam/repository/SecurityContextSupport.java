package io.aegisops.platform.iam.repository;

/**
 * Static utility wrapper around the platform {@link io.aegisops.security.TenantContext} so the IAM
 * repositories do not need to be instantiated with a Spring-managed collaborator. The runtime value
 * is intentionally resolved per-call to keep the repository side stateless.
 */
final class SecurityContextSupport {

  private SecurityContextSupport() {}

  static String currentTenant() {
    try {
      String tenantId = io.aegisops.common.tenant.TenantContext.getTenantId();
      return tenantId != null && !tenantId.isBlank() ? tenantId : "default";
    } catch (Throwable ignored) {
      return "default";
    }
  }
}