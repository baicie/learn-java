package io.aegisops.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UserPrincipal(
    String id, String tenantId, String username, String displayName, Set<String> roles)
    implements UserDetails {
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    Set<String> authorities = new LinkedHashSet<>();
    for (String role : roles) {
      String normalizedRole = role.toLowerCase(Locale.ROOT);
      authorities.add("ROLE_" + normalizedRole);
      authorities.addAll(permissionsForRole(normalizedRole));
    }
    return authorities.stream().map(SimpleGrantedAuthority::new).toList();
  }

  private Set<String> permissionsForRole(String role) {
    if ("admin".equals(role)) {
      return Set.of(
          "datasource:read",
          "datasource:write",
          "asset:read",
          "alert:read",
          "alert:write",
          "incident:read",
          "incident:write",
          "incident:diagnose",
          "runbook:read",
          "runbook:write",
          "automation:read",
          "automation:approve",
          "automation:execute",
          "audit:read",
          "admin:manage");
    }
    if ("operator".equals(role)) {
      return Set.of(
          "datasource:read",
          "asset:read",
          "alert:read",
          "alert:write",
          "incident:read",
          "incident:write",
          "incident:diagnose",
          "runbook:read",
          "automation:read",
          "automation:execute",
          "audit:read");
    }
    return Set.of();
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
