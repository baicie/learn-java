package io.aegisops.security;

public interface TenantSecurityEventRepository {
  void create(TenantSecurityEventCreateCommand command);
}
