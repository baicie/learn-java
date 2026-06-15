package io.aegisops.common.tenant;

import io.aegisops.common.exception.AppException;

public final class TenantContext {
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static void setTenantId(String tenantId) {
    CURRENT.set(tenantId);
  }

  public static String getTenantId() {
    return CURRENT.get();
  }

  public static String requireTenantId() {
    String tenantId = CURRENT.get();
    if (tenantId == null || tenantId.isBlank()) {
      throw new AppException("TENANT_REQUIRED", "Tenant context is required");
    }
    return tenantId;
  }

  public static void clear() {
    CURRENT.remove();
  }
}
